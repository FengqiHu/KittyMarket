package com.example.maoliang.Service;

import com.example.maoliang.Entity.Cat;
import com.example.maoliang.Entity.Good;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Repository.AiCustomerServiceRepository;
import com.example.maoliang.Repository.AiCustomerServiceRepository.PreferenceSignal;
import com.example.maoliang.Repository.GoodListRepository;
import com.example.maoliang.Repository.RecommendationRepository;
import com.example.maoliang.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiCustomerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiCustomerService.class);
    private static final int RECOMMEND_COUNT = 3;
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("(?:#|订单号?|编号|ID|id)\\s*([0-9]{1,9})|\\b([0-9]{1,9})\\s*号?订单");

    private final AiCustomerServiceRepository repository;
    private final GoodListRepository goodListRepository;
    private final RecommendationRepository recommendationRepository;
    private final CatalogRagService catalogRagService;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String difyApiKey;
    private final String difyBaseUrl;
    private final String difyToolToken;
    private final ChatModel qwenChatModel;

    private record DifyResult(String answer, String conversationId) {}
    private record OrderSupportOutcome(List<AiCsOrderSummary> displayOrders, String actionNotice) {}

    @Autowired
    public AiCustomerService(AiCustomerServiceRepository repository,
                             GoodListRepository goodListRepository,
                             RecommendationRepository recommendationRepository,
                             CatalogRagService catalogRagService,
                             OrderService orderService,
                             ObjectMapper objectMapper,
                             @Value("${app.ai.dify.api-key:}") String difyApiKey,
                             @Value("${app.ai.dify.base-url:https://api.dify.ai/v1}") String difyBaseUrl,
                             @Value("${app.ai.dify.tool-token:}") String difyToolToken,
                             @Value("${app.ai.qwen.api-key:}") String qwenApiKey,
                             @Value("${app.ai.qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String qwenBaseUrl,
                             @Value("${app.ai.qwen.chat-model:qwen-plus}") String qwenChatModelName) {
        this.repository = repository;
        this.goodListRepository = goodListRepository;
        this.recommendationRepository = recommendationRepository;
        this.catalogRagService = catalogRagService;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.difyApiKey = difyApiKey == null ? "" : difyApiKey.trim();
        this.difyBaseUrl = (difyBaseUrl == null || difyBaseUrl.isBlank()
                ? "https://api.dify.ai/v1" : difyBaseUrl.trim()).replaceAll("/+$", "");
        this.difyToolToken = difyToolToken == null ? "" : difyToolToken.trim();
        if (qwenApiKey != null && !qwenApiKey.isBlank()) {
            this.qwenChatModel = OpenAiChatModel.builder()
                    .baseUrl(qwenBaseUrl)
                    .apiKey(qwenApiKey.trim())
                    .modelName(qwenChatModelName)
                    .temperature(0.3)
                    .timeout(Duration.ofSeconds(20))
                    .maxRetries(0)
                    .build();
        } else {
            this.qwenChatModel = null;
        }
    }

    public static String resolveUserKey(Usr user, String guestToken, String sessionId) {
        if (user != null && user.getUserid() > 0) {
            return "user:" + user.getUserid();
        }
        String token = guestToken == null ? "" : guestToken.replaceAll("[^a-zA-Z0-9_-]", "");
        if (!token.isEmpty()) {
            return "guest:" + token.substring(0, Math.min(token.length(), 48));
        }
        String sid = sessionId == null ? "anon" : sessionId.replaceAll("[^a-zA-Z0-9_-]", "");
        return "guest:" + sid.substring(0, Math.min(sid.length(), 48));
    }

    public List<AiCsConversationView> listConversations(String userKey) {
        return repository.listConversations(userKey);
    }

    public List<AiCsMessageView> getConversationMessages(long conversationId, String userKey) {
        AiCsConversationView conv = repository.findConversation(conversationId, userKey);
        if (conv == null) throw new IllegalArgumentException("未找到该对话记录");
        return repository.listMessages(conversationId);
    }

    public boolean deleteConversation(long conversationId, String userKey) {
        return repository.deleteConversation(conversationId, userKey);
    }

    public boolean verifyToolToken(String providedToken) {
        if (difyToolToken.isEmpty()) return true;
        return difyToolToken.equals(providedToken);
    }

    public AiCsChatResponse chat(AiCsChatRequest request, Usr user, String sessionId) {
        String query = request == null || request.message() == null ? "" : request.message().trim();
        if (query.isEmpty()) throw new IllegalArgumentException("请输入要咨询的内容");
        if (query.length() > 500) throw new IllegalArgumentException("单次发送内容不能超过 500 个字符");

        String userKey = resolveUserKey(user, request.guestToken(), sessionId);
        Integer userId = user != null ? user.getUserid() : null;

        AiCsConversationView conv = null;
        if (request.conversationId() != null && request.conversationId() > 0) {
            conv = repository.findConversation(request.conversationId(), userKey);
        }
        if (conv == null) {
            conv = repository.createConversation(userKey, userId, query);
        }

        List<AiCsMessageView> history = repository.listMessages(conv.id());
        AiCsMessageView userMsg = repository.insertMessage(
                conv.id(), "user", query, null, List.of(), List.of(), null);

        String intent = detectIntent(query);
        PreferenceSignal prefSignal = repository.loadUserPreferences(
                userId, user != null ? user.getUsername() : null);

        List<AiCsRecommendedProduct> recommended = List.of();
        List<AiCsOrderSummary> orderCards = List.of();
        String orderActionNotice = "";

        if ("shopping_guide".equals(intent)) {
            recommended = recommendThreeProducts(query, user, prefSignal);
        } else if ("order_support".equals(intent)) {
            OrderSupportOutcome outcome = handleOrderSupport(query, user);
            orderCards = outcome.displayOrders();
            orderActionNotice = outcome.actionNotice();
        }

        String provider = "local";
        String answer = null;
        String newDifyConvId = conv.difyConversationId();

        // 1. Call Dify Chat API when configured
        if (!difyApiKey.isEmpty()) {
            try {
                DifyResult difyResult = callDifyChat(
                        query, intent, user, userKey, prefSignal, recommended, orderCards,
                        orderActionNotice, conv.difyConversationId());
                if (difyResult != null && difyResult.answer() != null && !difyResult.answer().isBlank()) {
                    answer = difyResult.answer().trim();
                    newDifyConvId = difyResult.conversationId();
                    provider = "dify";
                }
            } catch (Exception e) {
                LOGGER.warn("Dify Chat API 调用异常，切换至备用回复模型", e);
            }
        }

        // 2. Fallback to Qwen ChatModel when Dify is unconfigured or unreachable
        if (answer == null && qwenChatModel != null) {
            try {
                String qwenReply = callQwenChat(
                        query, intent, user, prefSignal, recommended, orderCards, orderActionNotice, history);
                if (qwenReply != null && !qwenReply.isBlank()) {
                    answer = qwenReply.trim();
                    provider = "qwen";
                }
            } catch (Exception e) {
                LOGGER.warn("Qwen 客服回复生成失败，使用本地规则回复", e);
            }
        }

        // 3. Deterministic local fallback
        if (answer == null || answer.isBlank()) {
            answer = buildLocalReply(query, intent, user, prefSignal, recommended, orderCards, orderActionNotice);
            provider = "local";
        }

        repository.updateConversationAfterTurn(conv.id(), newDifyConvId);
        AiCsMessageView assistantMsg = repository.insertMessage(
                conv.id(), "assistant", answer, intent, recommended, orderCards, provider);
        AiCsConversationView updatedConv = repository.findConversation(conv.id(), userKey);
        return new AiCsChatResponse(updatedConv, userMsg, assistantMsg);
    }

    public AiCsChatResponse performOrderActionInChat(AiCsOrderActionRequest request, Usr user, String sessionId) {
        if (user == null || user.getUserid() < 1) {
            throw new IllegalArgumentException("请先登录后再操作订单");
        }
        if (request == null || request.orderId() == null || request.orderId() < 1) {
            throw new IllegalArgumentException("请选择有效的订单编号");
        }
        String action = request.action() == null ? "" : request.action().trim();
        int orderId = request.orderId();
        String userActionText = "cancel".equalsIgnoreCase(action)
                ? "帮我取消订单 #" + orderId
                : "帮我确认收货订单 #" + orderId;

        String userKey = resolveUserKey(user, request.guestToken(), sessionId);
        AiCsConversationView conv = null;
        if (request.conversationId() != null && request.conversationId() > 0) {
            conv = repository.findConversation(request.conversationId(), userKey);
        }
        if (conv == null) {
            conv = repository.createConversation(userKey, user.getUserid(), userActionText);
        }

        AiCsMessageView userMsg = repository.insertMessage(
                conv.id(), "user", userActionText, "order_support", List.of(), List.of(), null);

        String reply;
        if ("cancel".equalsIgnoreCase(action)) {
            boolean ok = orderService.deleteOrder(orderId, -1, user);
            reply = ok
                    ? "已为您成功取消订单 #" + orderId + "，相应商品库存已恢复。您可以在下方确认最新订单状态。"
                    : "订单 #" + orderId + " 取消失败：该订单可能已发货、已取消或不在可取消状态（仅状态为等待商家确认/备货/发货确认的未发货订单支持取消）。";
        } else if ("confirm".equalsIgnoreCase(action)) {
            List<AiCsOrderSummary> before = repository.findUserOrders(user.getUsername());
            AiCsOrderSummary target = before.stream().filter(o -> o.orderid() == orderId).findFirst().orElse(null);
            if (target == null) {
                reply = "未找到属于您的订单 #" + orderId + "。";
            } else if (target.orderstate() != 4) {
                reply = "订单 #" + orderId + " 当前状态为「" + target.statusText() + "」，仅商家已发货（状态 4）的订单可以确认收货。";
            } else {
                boolean ok = orderService.buyerhistoryconfirmOrder(orderId, 5);
                reply = ok
                        ? "已为您确认收货订单 #" + orderId + "，交易已完成！感谢您在猫咪美食坊选购。"
                        : "订单 #" + orderId + " 确认收货失败，请稍后重试。";
            }
        } else {
            throw new IllegalArgumentException("不支持的订单操作类型");
        }

        List<AiCsOrderSummary> updatedOrders = repository.findUserOrders(user.getUsername()).stream()
                .filter(o -> o.orderid() == orderId)
                .toList();
        if (updatedOrders.isEmpty()) {
            updatedOrders = repository.findUserOrders(user.getUsername()).stream().limit(3).toList();
        }

        repository.updateConversationAfterTurn(conv.id(), conv.difyConversationId());
        AiCsMessageView assistantMsg = repository.insertMessage(
                conv.id(), "assistant", reply, "order_support", List.of(), updatedOrders,
                !difyApiKey.isEmpty() ? "dify" : "local");
        AiCsConversationView updatedConv = repository.findConversation(conv.id(), userKey);
        return new AiCsChatResponse(updatedConv, userMsg, assistantMsg);
    }

    public String detectIntent(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        boolean hasOrderKeyword = containsAny(q,
                "订单", "发货", "物流", "快递", "取消订单", "退货", "退款", "换货", "售后",
                "收货", "催单", "发了没", "还没到", "查单", "订单号", "交易取消", "待确认", "备货");
        boolean hasShoppingKeyword = containsAny(q,
                "推荐", "导购", "选粮", "买什么", "哪款", "哪个好", "适合", "猫粮", "主粮", "零食",
                "日用", "罐头", "冻干", "猫条", "猫砂", "猫抓板", "玩具", "饮水", "幼猫", "成猫",
                "绝育", "减重", "控重", "低脂", "鸡肉", "三文鱼", "牛肉", "鱼", "化毛", "肠胃",
                "高蛋白", "性价比", "便宜", "好物", "偏好", "营养", "几款", "三款", "3款");
        if (hasOrderKeyword && !hasShoppingKeyword) return "order_support";
        if (hasShoppingKeyword && !hasOrderKeyword) return "shopping_guide";
        if (hasOrderKeyword && hasShoppingKeyword) {
            if (containsAny(q, "取消", "退", "发货", "物流", "订单", "收货")) return "order_support";
            return "shopping_guide";
        }
        return "general";
    }

    /**
     * 根据用户当前导购需求 + 用户历史偏好（浏览、收藏、购买、猫咪档案），为用户精准推荐 3 款在售商品。
     */
    public List<AiCsRecommendedProduct> recommendThreeProducts(String query, Usr user, PreferenceSignal prefSignal) {
        List<Good> allAvailable = Optional.ofNullable(goodListRepository.shownow()).orElse(List.of())
                .stream()
                .filter(g -> g.getState() == 0 && g.getNumber() > 0)
                .toList();
        if (allAvailable.isEmpty()) return List.of();

        String detectedKind = detectCategoryFromQuery(query);
        Double maxBudget = detectMaxBudget(query);

        // 1. Semantic / keyword ranking score from CatalogRagService
        Map<Integer, Integer> ragRankPosition = new HashMap<>();
        try {
            CatalogSearchResult ragResult = catalogRagService.search(
                    stripConversationalFiller(query), detectedKind == null ? "全部" : detectedKind, 1, 26);
            List<Good> ragItems = ragResult.items() == null ? List.of() : ragResult.items();
            for (int i = 0; i < ragItems.size(); i++) {
                ragRankPosition.put(ragItems.get(i).getGoodid(), i);
            }
        } catch (Exception e) {
            LOGGER.debug("RAG search in AI CS fallback to local scoring", e);
        }

        // 2. Personalized ranking from RecommendationRepository (combines views, orders, likes, cat profile, popularity)
        Map<Integer, Integer> personalRankPosition = new HashMap<>();
        try {
            GoodPage personalPage = recommendationRepository.page("全部", 1, 48, user);
            List<Good> personalItems = personalPage.items() == null ? List.of() : personalPage.items();
            for (int i = 0; i < personalItems.size(); i++) {
                personalRankPosition.put(personalItems.get(i).getGoodid(), i);
            }
        } catch (Exception e) {
            LOGGER.debug("Personal page ranking fallback", e);
        }

        // Find user's top preferred category and subcategory
        String favKind = prefSignal.kindWeights().entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
        String favSubkind = prefSignal.subkindWeights().entrySet().stream()
                .filter(e -> !e.getKey().isEmpty() && !"默认子类".equals(e.getKey()))
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");

        record ScoredCandidate(Good good, double score, String reason) {}
        List<ScoredCandidate> scored = new ArrayList<>();

        for (Good good : allAvailable) {
            double score = 0.0;
            List<String> reasons = new ArrayList<>();

            // (A) Category demand match
            String kind = safe(good.getKind());
            String subkind = safe(good.getSubkind());
            if (detectedKind != null) {
                if (detectedKind.equals(kind)) {
                    score += 35.0;
                    reasons.add("符合「" + detectedKind + "」需求");
                } else {
                    score -= 20.0;
                }
            }

            // (B) Budget constraint match
            if (maxBudget != null) {
                if (good.getPrice() <= maxBudget) {
                    score += 18.0;
                    reasons.add("预算内高性价比");
                } else {
                    score -= 25.0;
                }
            }

            // (C) Semantic / RAG relevance
            Integer ragPos = ragRankPosition.get(good.getGoodid());
            if (ragPos != null) {
                score += Math.max(8.0, 42.0 - ragPos * 3.5);
                if (reasons.isEmpty()) reasons.add("契合你的选购描述");
            }

            // (D) Fine-grained demand keyword matching
            int kwScore = matchQueryKeywords(query, good);
            if (kwScore > 0) {
                score += kwScore;
                if (reasons.isEmpty()) reasons.add("匹配你的需求关键词");
            }

            // (E) User preference signals (browsing, likes, purchases, category affinity)
            Integer pPos = personalRankPosition.get(good.getGoodid());
            if (pPos != null) {
                score += Math.max(2.0, 22.0 - pPos * 0.9);
            }
            if (prefSignal.likedGoods().contains(good.getGoodid())) {
                score += 14.0;
                reasons.add("你收藏过的偏好同款");
            } else if (prefSignal.purchasedGoods().contains(good.getGoodid())) {
                score += 12.0;
                reasons.add("你曾购买过的复购优选");
            } else if (prefSignal.viewedGoods().contains(good.getGoodid())) {
                score += 8.0;
                reasons.add("结合你近期浏览偏好");
            } else if (!favSubkind.isEmpty() && favSubkind.equals(subkind)) {
                score += 10.0;
                reasons.add("符合你偏好的「" + favSubkind + "」品类");
            } else if (!favKind.isEmpty() && favKind.equals(kind)) {
                score += 6.0;
                reasons.add("符合你常逛的「" + favKind + "」偏好");
            }

            // (F) Cat profile compatibility & boost
            Cat matchedCat = findMatchingCat(good, prefSignal.cats(), query);
            if (matchedCat != null) {
                score += 16.0;
                reasons.add("适合爱猫「" + safe(matchedCat.getCatname()) + "」（"
                        + safe(matchedCat.getCatkind()) + "·" + matchedCat.getCatage() + "岁）");
            } else if (!prefSignal.cats().isEmpty() && isExplicitlyIncompatibleWithAllCats(good, prefSignal.cats())) {
                score -= 18.0;
            }

            String finalReason = reasons.isEmpty()
                    ? (user != null ? "结合你的偏好与店内人气精选" : "店内高人气口碑好物")
                    : reasons.stream().distinct().limit(2).collect(Collectors.joining(" · "));

            scored.add(new ScoredCandidate(good, score, finalReason));
        }

        scored.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                .thenComparing(c -> c.good().getGoodid(), Comparator.reverseOrder()));

        return scored.stream()
                .limit(RECOMMEND_COUNT)
                .map(c -> {
                    Good g = c.good();
                    return new AiCsRecommendedProduct(
                            g.getGoodid(),
                            safe(g.getGoodname()),
                            safe(g.getKind()),
                            safe(g.getSubkind()),
                            g.getPrice(),
                            safe(g.getPicture()),
                            safe(g.getDescription()),
                            g.getNumber(),
                            safe(g.getCatkind()),
                            safe(g.getCatage()),
                            safe(g.getCatweight()),
                            c.reason()
                    );
                })
                .toList();
    }

    private OrderSupportOutcome handleOrderSupport(String query, Usr user) {
        if (user == null || user.getUserid() < 1) {
            return new OrderSupportOutcome(List.of(), "用户当前尚未登录，无法查询或操作个人订单，请引导用户先登录账号。");
        }
        List<AiCsOrderSummary> allOrders = repository.findUserOrders(user.getUsername());
        if (allOrders.isEmpty()) {
            return new OrderSupportOutcome(List.of(), "当前账号暂无历史订单记录。");
        }

        Integer mentionedOrderId = extractOrderId(query);
        String q = query.toLowerCase(Locale.ROOT);
        String actionNotice = "";

        // Check if user explicitly asks to cancel an order
        if (containsAny(q, "取消订单", "帮我取消", "取消#", "取消编号", "退单", "不要了")) {
            Integer targetId = mentionedOrderId;
            if (targetId == null) {
                List<AiCsOrderSummary> cancellable = allOrders.stream().filter(AiCsOrderSummary::canCancel).toList();
                if (cancellable.size() == 1) {
                    targetId = cancellable.get(0).orderid();
                }
            }
            if (targetId != null) {
                boolean cancelled = orderService.deleteOrder(targetId, -1, user);
                allOrders = repository.findUserOrders(user.getUsername());
                actionNotice = cancelled
                        ? "已成功为用户取消订单 #" + targetId + "，并恢复商品库存。"
                        : "尝试取消订单 #" + targetId + " 失败（订单可能已发货、已取消或无权限）。";
            } else {
                actionNotice = "用户希望取消订单，但未指定具体订单号（或存在多笔可取消订单），请提示用户在下方订单卡片直接点击「取消订单」或告知订单编号。";
            }
        } else if (containsAny(q, "确认收货", "确认完成", "收到货了", "已收到")) {
            Integer targetId = mentionedOrderId;
            if (targetId == null) {
                List<AiCsOrderSummary> confirmable = allOrders.stream().filter(AiCsOrderSummary::canConfirm).toList();
                if (confirmable.size() == 1) {
                    targetId = confirmable.get(0).orderid();
                }
            }
            if (targetId != null) {
                final int tid = targetId;
                AiCsOrderSummary targetOrder = allOrders.stream().filter(o -> o.orderid() == tid).findFirst().orElse(null);
                if (targetOrder != null && targetOrder.orderstate() == 4) {
                    boolean confirmed = orderService.buyerhistoryconfirmOrder(tid, 5);
                    allOrders = repository.findUserOrders(user.getUsername());
                    actionNotice = confirmed
                            ? "已成功为用户确认收货订单 #" + tid + "（状态更新为交易成功）。"
                            : "确认收货订单 #" + tid + " 失败。";
                } else {
                    actionNotice = "订单 #" + tid + " 当前不在「商家已发货，等待交易确认」状态，暂无法确认收货。";
                }
            }
        }

        List<AiCsOrderSummary> displayOrders;
        if (mentionedOrderId != null) {
            final int oid = mentionedOrderId;
            displayOrders = allOrders.stream().filter(o -> o.orderid() == oid).toList();
            if (displayOrders.isEmpty()) {
                displayOrders = allOrders.stream().limit(3).toList();
                if (actionNotice.isEmpty()) {
                    actionNotice = "未查到订单号 #" + oid + "，以下展示最近的订单。";
                }
            }
        } else {
            displayOrders = allOrders.stream().limit(3).toList();
        }
        return new OrderSupportOutcome(displayOrders, actionNotice);
    }

    private DifyResult callDifyChat(String query,
                                    String intent,
                                    Usr user,
                                    String userKey,
                                    PreferenceSignal prefSignal,
                                    List<AiCsRecommendedProduct> recommended,
                                    List<AiCsOrderSummary> orders,
                                    String orderActionNotice,
                                    String difyConversationId) throws Exception {
        String userProfileText = buildUserProfileSummary(user, prefSignal);
        String prefText = buildPreferenceSummary(prefSignal);
        String recText = buildRecommendedProductsContext(recommended);
        String orderText = buildOrdersContext(orders, orderActionNotice);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("intent", intent);
        inputs.put("user_profile", userProfileText);
        inputs.put("user_preferences", prefText);
        inputs.put("recommended_products", recText);
        inputs.put("user_orders", orderText);
        inputs.put("order_action_result", orderActionNotice == null ? "" : orderActionNotice);

        String contextualQuery = buildContextualPromptForDify(
                query, intent, userProfileText, prefText, recText, orderText);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputs", inputs);
        payload.put("query", contextualQuery);
        payload.put("response_mode", "blocking");
        payload.put("conversation_id", difyConversationId == null ? "" : difyConversationId);
        payload.put("user", userKey);

        String requestBody = objectMapper.writeValueAsString(payload);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(difyBaseUrl + "/chat-messages"))
                .timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + difyApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Dify HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode root = objectMapper.readTree(response.body());
        String answer = root.path("answer").asText("");
        String convId = root.path("conversation_id").asText(difyConversationId == null ? "" : difyConversationId);
        return new DifyResult(answer, convId);
    }

    private String callQwenChat(String query,
                                String intent,
                                Usr user,
                                PreferenceSignal prefSignal,
                                List<AiCsRecommendedProduct> recommended,
                                List<AiCsOrderSummary> orders,
                                String orderActionNotice,
                                List<AiCsMessageView> history) {
        String userProfileText = buildUserProfileSummary(user, prefSignal);
        String prefText = buildPreferenceSummary(prefSignal);
        String recText = buildRecommendedProductsContext(recommended);
        String orderText = buildOrdersContext(orders, orderActionNotice);

        StringBuilder historyText = new StringBuilder();
        int start = Math.max(0, history.size() - 6);
        for (int i = start; i < history.size(); i++) {
            AiCsMessageView m = history.get(i);
            historyText.append("user".equals(m.role()) ? "用户: " : "客服: ")
                    .append(m.content().replaceAll("\\s+", " "))
                    .append("\n");
        }

        String prompt = """
                你是「猫咪美食坊」官方 AI 智能客服（接入 Dify 智能客服体系），负责提供贴心的猫咪商品导购与订单售后支持。
                请严格遵守以下规则：
                1. 只根据下方提供的真实商城商品数据与真实订单数据回答，绝不编造不存在的商品、价格或订单。
                2. 当任务是「导购推荐 (shopping_guide)」时：结合用户的需求与用户偏好，热情、专业地向用户介绍下方已为你筛选好的 3 款商品（说明每款商品的价格、亮点以及为什么契合用户的偏好或猫咪情况）。商品卡片会自动展示在你的回复下方。
                3. 当任务是「订单支持 (order_support)」时：根据下方订单列表与操作结果解答订单状态、取消未发货订单（状态1~3可取消）、确认收货（状态4可确认），或解答退换货/发货政策（现货24小时内发货；未发货可直接取消并自动退回库存；收货后7天内质量问题包退换）。
                4. 语气亲切自然、条理清晰，控制在 220 字以内。

                【当前意图】：%s
                【用户信息与猫咪档案】：%s
                【用户历史偏好】：%s
                【为您精选的 3 款推荐商品】：
                %s
                【用户最近订单与操作状态】：
                %s
                【近期对话上下文】：
                %s
                【用户当前提问】：%s
                """.formatted(intent, userProfileText, prefText, recText, orderText, historyText, query);

        return qwenChatModel.chat(prompt);
    }

    private String buildLocalReply(String query,
                                   String intent,
                                   Usr user,
                                   PreferenceSignal prefSignal,
                                   List<AiCsRecommendedProduct> recommended,
                                   List<AiCsOrderSummary> orders,
                                   String orderActionNotice) {
        if ("shopping_guide".equals(intent)) {
            if (recommended.isEmpty()) {
                return "抱歉，当前暂无符合该条件的在售现货商品。您可以尝试换个关键词（如「幼猫主粮」「冻干零食」「猫砂」），我再为您挑选中意好物！";
            }
            StringBuilder sb = new StringBuilder();
            if (user != null && (!prefSignal.cats().isEmpty() || !prefSignal.productWeights().isEmpty())) {
                sb.append("结合您的需求与个人偏好");
                if (!prefSignal.cats().isEmpty()) {
                    Cat c = prefSignal.cats().get(0);
                    sb.append("（以及爱猫「").append(safe(c.getCatname())).append("」").append(safe(c.getCatkind()))
                            .append("·").append(c.getCatage()).append("岁的档案）");
                }
                sb.append("，为您精选了以下 ").append(recommended.size()).append(" 款好物：\n\n");
            } else {
                sb.append("根据您的选购需求，为您精选了以下 ").append(recommended.size()).append(" 款高人气商品：\n\n");
            }
            for (int i = 0; i < recommended.size(); i++) {
                AiCsRecommendedProduct p = recommended.get(i);
                sb.append(i + 1).append(". **").append(p.goodname()).append("**（¥")
                        .append(String.format("%.2f", p.price())).append(" · ").append(p.kind()).append("/").append(p.subkind()).append("）\n")
                        .append("   - 推荐理由：").append(p.matchReason()).append("；").append(p.description()).append("\n");
            }
            sb.append("\n您可以直接点击下方商品卡片查看详情或一键加入购物车！");
            return sb.toString();
        }

        if ("order_support".equals(intent)) {
            StringBuilder sb = new StringBuilder();
            if (orderActionNotice != null && !orderActionNotice.isBlank()) {
                sb.append(orderActionNotice).append("\n\n");
            }
            String q = query.toLowerCase(Locale.ROOT);
            if (containsAny(q, "退货", "退款", "换货", "售后", "多久发货", "什么时候发货", "运费", "保质期")) {
                sb.append("📋 **售后服务与发货说明**：\n")
                        .append("• **发货时效**：店内现货商品下单后 24 小时内由商家确认并安排备货发出。\n")
                        .append("• **修改/取消订单**：订单处于「等待商家确认 / 等待备货确认 / 等待发货确认」（未发货）阶段时，您可以直接在下方订单卡片点击「取消订单」，库存将自动恢复。\n")
                        .append("• **退换货保障**：签收后 7 天内如有破袋、变质或发错货问题，支持免费退换。\n\n");
            }
            if (user == null) {
                sb.append("您当前尚未登录，登录后即可在此直接查询您的历史订单、取消未发货订单或确认收货。");
                return sb.toString();
            }
            if (orders.isEmpty()) {
                if (sb.length() == 0) {
                    sb.append("查询到您的账号（").append(safe(user.getUsername())).append("）目前还没有历史订单记录。去商城挑几件心仪的猫咪好物吧！");
                }
                return sb.toString().trim();
            }
            sb.append("为您查到以下最近订单信息（可在下方卡片直接操作取消未发货订单或确认收货）：\n");
            for (AiCsOrderSummary o : orders) {
                sb.append("• **订单 #").append(o.orderid()).append("**：").append(o.goodname())
                        .append(" × ").append(o.number()).append(" 件 — 状态：【").append(o.statusText()).append("】\n");
            }
            return sb.toString().trim();
        }

        // General greeting or FAQ
        String greetingName = user != null ? " " + safe(user.getUsername()) : "";
        return "您好" + greetingName + "！我是猫咪美食坊 AI 智能客服 🐾\n"
                + "我可以为您提供以下贴心服务：\n"
                + "1. **专属导购推荐**：告诉我您的需求（如「推荐适合幼猫的主粮」「推荐3款高性价比零食」「按我的偏好推荐」），我会结合您的偏好与猫咪档案为您推荐 **3 款商品**。\n"
                + "2. **订单查询与处理**：随时查询您的订单状态、直接在对话中**取消未发货订单**或**确认收货**，以及解答退换货与发货疑问。";
    }

    private String buildContextualPromptForDify(String query,
                                                String intent,
                                                String userProfileText,
                                                String prefText,
                                                String recText,
                                                String orderText) {
        return """
                [系统上下文 - 猫咪美食坊AI客服]
                当前识别意图: %s
                用户信息与猫咪档案: %s
                用户偏好画像: %s
                为您匹配好的3款推荐商品(导购需求时请重点向用户介绍这3款商品):
                %s
                用户订单状态与操作记录(订单问题时请基于此回答):
                %s

                [用户问题]
                %s
                """.formatted(intent, userProfileText, prefText, recText, orderText, query);
    }

    private String buildUserProfileSummary(Usr user, PreferenceSignal prefSignal) {
        if (user == null) return "未登录访客";
        StringBuilder sb = new StringBuilder("用户名:").append(safe(user.getUsername()));
        if (!prefSignal.cats().isEmpty()) {
            sb.append(" | 猫咪档案: ");
            sb.append(prefSignal.cats().stream()
                    .map(c -> safe(c.getCatname()) + "(" + safe(c.getCatkind()) + "," + c.getCatage() + "岁,"
                            + c.getCatweight() + "kg," + catStateLabel(c.getCatstate()) + ")")
                    .collect(Collectors.joining("; ")));
        }
        return sb.toString();
    }

    private String buildPreferenceSummary(PreferenceSignal prefSignal) {
        List<String> parts = new ArrayList<>();
        if (!prefSignal.kindWeights().isEmpty()) {
            String topKinds = prefSignal.kindWeights().entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(2)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.joining("、"));
            parts.add("偏好大类:" + topKinds);
        }
        if (!prefSignal.subkindWeights().isEmpty()) {
            String topSub = prefSignal.subkindWeights().entrySet().stream()
                    .filter(e -> !e.getKey().isEmpty() && !"默认子类".equals(e.getKey()))
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.joining("、"));
            if (!topSub.isEmpty()) parts.add("偏好子类:" + topSub);
        }
        if (!prefSignal.likedGoods().isEmpty()) parts.add("已收藏商品数:" + prefSignal.likedGoods().size());
        if (!prefSignal.purchasedGoods().isEmpty()) parts.add("已购商品数:" + prefSignal.purchasedGoods().size());
        if (!prefSignal.viewedGoods().isEmpty()) parts.add("近期浏览商品数:" + prefSignal.viewedGoods().size());
        return parts.isEmpty() ? "暂无历史行为偏好，按热门与需求匹配" : String.join(" | ", parts);
    }

    private String buildRecommendedProductsContext(List<AiCsRecommendedProduct> recommended) {
        if (recommended == null || recommended.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recommended.size(); i++) {
            AiCsRecommendedProduct p = recommended.get(i);
            sb.append(i + 1).append(". [商品#").append(p.goodid()).append("] ")
                    .append(p.goodname()).append(" | 分类:").append(p.kind()).append("/").append(p.subkind())
                    .append(" | 价格:¥").append(String.format("%.2f", p.price()))
                    .append(" | 库存:").append(p.number())
                    .append(" | 适用:").append(p.catkind()).append(" ").append(p.catage()).append("岁 ").append(p.catweight()).append("kg")
                    .append(" | 偏好匹配点:").append(p.matchReason())
                    .append(" | 描述:").append(p.description())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private String buildOrdersContext(List<AiCsOrderSummary> orders, String orderActionNotice) {
        StringBuilder sb = new StringBuilder();
        if (orderActionNotice != null && !orderActionNotice.isBlank()) {
            sb.append("操作结果: ").append(orderActionNotice).append("\n");
        }
        if (orders == null || orders.isEmpty()) {
            sb.append("暂无相关订单记录");
            return sb.toString();
        }
        for (AiCsOrderSummary o : orders) {
            sb.append("订单#").append(o.orderid())
                    .append(" | 商品:").append(o.goodname()).append("(ID:").append(o.goodid()).append(")")
                    .append(" | 数量:").append(o.number())
                    .append(" | 单价:¥").append(String.format("%.2f", o.price()))
                    .append(" | 状态:").append(o.statusText()).append("(code=").append(o.orderstate()).append(")")
                    .append(" | 收货人:").append(o.recipientname()).append(" ").append(o.telephone()).append(" ").append(o.address())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private String detectCategoryFromQuery(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        boolean mainFood = containsAny(q, "主粮", "猫粮", "干粮", "膨化粮", "烘焙粮", "冻干粮", "奶糕", "选粮");
        boolean snack = containsAny(q, "零食", "罐头", "猫条", "冻干", "肉干", "猫草", "化毛膏", "磨牙");
        boolean daily = containsAny(q, "日用", "猫砂", "猫抓板", "玩具", "猫窝", "食盆", "饮水机", "猫爬架", "梳子", "指甲剪", "用品");
        int count = (mainFood ? 1 : 0) + (snack ? 1 : 0) + (daily ? 1 : 0);
        if (count != 1) return null;
        if (mainFood) return "猫咪主粮";
        if (snack) return "猫咪零食";
        return "猫咪日用";
    }

    private Double detectMaxBudget(String query) {
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:元|块|rmb|¥)?\\s*(?:以内|以下|左右|不超过|内)").matcher(query.toLowerCase(Locale.ROOT));
        if (m.find()) {
            try { return Double.parseDouble(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        if (containsAny(query, "便宜", "实惠", "平价", "性价比")) {
            return 80.0;
        }
        return null;
    }

    private int matchQueryKeywords(String query, Good good) {
        String q = query.toLowerCase(Locale.ROOT);
        String doc = (safe(good.getGoodname()) + " " + safe(good.getKind()) + " " + safe(good.getSubkind())
                + " " + safe(good.getDescription()) + " " + safe(good.getCatkind())).toLowerCase(Locale.ROOT);
        int score = 0;
        String[] keywords = {
                "幼猫", "成猫", "老年猫", "全猫", "绝育", "减重", "控重", "低脂", "室内", "孕猫", "母猫", "哺乳",
                "鸡肉", "三文鱼", "鱼", "牛肉", "鸭肉", "羊奶", "乳铁蛋白", "益生菌", "肠胃", "美毛", "化毛",
                "高蛋白", "无谷", "烘焙", "膨化", "冻干", "罐头", "猫条", "猫砂", "豆腐砂", "膨润土",
                "玩具", "猫抓板", "饮水", "除臭", "布偶", "英短", "美短", "橘猫", "暹罗", "波斯", "加菲"
        };
        for (String kw : keywords) {
            if (q.contains(kw) && doc.contains(kw)) {
                score += 12;
            }
        }
        // Check 2-gram overlaps with product name
        String cleanQuery = stripConversationalFiller(q);
        String name = safe(good.getGoodname()).toLowerCase(Locale.ROOT);
        for (int i = 0; i < cleanQuery.length() - 1; i++) {
            String bi = cleanQuery.substring(i, i + 2).trim();
            if (bi.length() == 2 && name.contains(bi)) {
                score += 4;
            }
        }
        return score;
    }

    private String stripConversationalFiller(String query) {
        return query.replaceAll("(请|帮我|给我|推荐|介绍|几款|三款|3款|商品|符合|我的|偏好|适合|想要|买|有什么|好物|吗|呢|吧|的)", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Cat findMatchingCat(Good good, List<Cat> cats, String query) {
        if (cats == null || cats.isEmpty()) return null;
        for (Cat cat : cats) {
            if (!rangeCompatible(cat.getCatage(), good.getCatage())) continue;
            if (!rangeCompatible(cat.getCatweight(), good.getCatweight())) continue;
            String catKind = safe(cat.getCatkind());
            String goodCatKind = safe(good.getCatkind());
            String text = safe(good.getGoodname()) + " " + safe(good.getDescription());
            boolean breedMatch = !catKind.isEmpty() && (goodCatKind.contains(catKind) || goodCatKind.contains("通用") || goodCatKind.contains("全猫"));
            boolean stateMatch = matchesCatStateKeyword(cat.getCatstate(), text);
            boolean mentionedInQuery = query.contains(safe(cat.getCatname())) || (!catKind.isEmpty() && query.contains(catKind));
            if (mentionedInQuery || breedMatch || stateMatch) {
                return cat;
            }
        }
        return null;
    }

    private boolean isExplicitlyIncompatibleWithAllCats(Good good, List<Cat> cats) {
        for (Cat cat : cats) {
            if (rangeCompatible(cat.getCatage(), good.getCatage())
                    && rangeCompatible(cat.getCatweight(), good.getCatweight())) {
                return false;
            }
        }
        return true;
    }

    private boolean rangeCompatible(double value, String range) {
        if (range == null || range.isBlank() || "未标注".equals(range.trim())) return true;
        String[] parts = range.trim().split("\\s*-\\s*");
        if (parts.length != 2) return true;
        try {
            double min = Double.parseDouble(parts[0]);
            double max = Double.parseDouble(parts[1]);
            return value >= min && value <= max;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private boolean matchesCatStateKeyword(int state, String text) {
        return switch (state) {
            case 1 -> containsAny(text, "减重", "体重管理", "低脂", "控重");
            case 2 -> containsAny(text, "室内", "低脂", "体重管理");
            case 3 -> containsAny(text, "绝育", "成猫");
            case 4 -> containsAny(text, "成猫");
            case 5 -> containsAny(text, "妊娠", "孕猫", "孕期", "母猫");
            case 6 -> containsAny(text, "哺乳", "母猫");
            case 7 -> containsAny(text, "幼猫", "奶糕", "离乳");
            default -> false;
        };
    }

    private static String catStateLabel(int state) {
        return switch (state) {
            case 1 -> "需要减重";
            case 2 -> "室内猫";
            case 3 -> "已绝育";
            case 4 -> "正常成猫";
            case 5 -> "妊娠期";
            case 6 -> "哺乳期";
            case 7 -> "幼猫期";
            default -> "普通状态";
        };
    }

    private Integer extractOrderId(String query) {
        Matcher m = ORDER_ID_PATTERN.matcher(query);
        if (m.find()) {
            String g = m.group(1) != null ? m.group(1) : m.group(2);
            try { return Integer.parseInt(g); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private static String safe(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }
}

