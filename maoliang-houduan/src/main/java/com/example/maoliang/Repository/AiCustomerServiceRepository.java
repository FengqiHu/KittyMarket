package com.example.maoliang.Repository;

import com.example.maoliang.Entity.Cat;
import com.example.maoliang.dto.AiCsConversationView;
import com.example.maoliang.dto.AiCsMessageView;
import com.example.maoliang.dto.AiCsOrderSummary;
import com.example.maoliang.dto.AiCsRecommendedProduct;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Repository
public class AiCustomerServiceRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiCustomerServiceRepository.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public record PreferenceSignal(
            List<Cat> cats,
            Map<Integer, Double> productWeights,
            Map<String, Double> kindWeights,
            Map<String, Double> subkindWeights,
            Set<Integer> likedGoods,
            Set<Integer> purchasedGoods,
            Set<Integer> viewedGoods
    ) {}

    public record CartItemSummary(
            int goodid,
            String goodname,
            String kind,
            String subkind,
            double price,
            int quantity
    ) {}

    public AiCustomerServiceRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void ensureTables() {
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS MLorder_item (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        orderid INTEGER NOT NULL,
                        goodid INTEGER NOT NULL,
                        goodname VARCHAR(255) NOT NULL,
                        price DOUBLE NOT NULL,
                        number INTEGER NOT NULL,
                        KEY idx_mlorder_item_order (orderid),
                        KEY idx_mlorder_item_good (goodid),
                        FOREIGN KEY (orderid) REFERENCES MLorder (orderid) ON DELETE CASCADE,
                        FOREIGN KEY (goodid) REFERENCES MLgood (goodid) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            jdbc.execute("""
                    INSERT INTO MLorder_item (orderid, goodid, goodname, price, number)
                    SELECT o.orderid, o.goodid, COALESCE(g.goodname, CONCAT('商品#', o.goodid)), COALESCE(g.price, 0), o.number
                    FROM MLorder o
                    LEFT JOIN MLgood g ON g.goodid = o.goodid
                    WHERE NOT EXISTS (SELECT 1 FROM MLorder_item oi WHERE oi.orderid = o.orderid)
                    """);
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS MLcs_conversation (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        user_key VARCHAR(64) NOT NULL,
                        userid INTEGER NULL,
                        title VARCHAR(100) NOT NULL,
                        dify_conversation_id VARCHAR(128) NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        KEY idx_mlcs_conv_user (user_key, updated_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS MLcs_message (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        conversation_id BIGINT NOT NULL,
                        role VARCHAR(16) NOT NULL,
                        content TEXT NOT NULL,
                        intent VARCHAR(32) NULL,
                        recommendations_json TEXT NULL,
                        orders_json TEXT NULL,
                        provider VARCHAR(32) NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        KEY idx_mlcs_msg_conv (conversation_id, id),
                        FOREIGN KEY (conversation_id) REFERENCES MLcs_conversation (id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
        } catch (RuntimeException e) {
            LOGGER.warn("Could not ensure AI customer service tables on startup", e);
        }
    }

    public List<AiCsConversationView> listConversations(String userKey) {
        String sql = """
                SELECT c.id, c.title, c.dify_conversation_id, c.created_at, c.updated_at,
                       (SELECT m.content FROM MLcs_message m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1) AS last_message,
                       (SELECT COUNT(*) FROM MLcs_message m WHERE m.conversation_id = c.id) AS message_count
                FROM MLcs_conversation c
                WHERE c.user_key = ?
                ORDER BY c.updated_at DESC, c.id DESC
                LIMIT 50
                """;
        return jdbc.query(sql, (rs, rowNum) -> new AiCsConversationView(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("dify_conversation_id"),
                formatTime(rs.getTimestamp("created_at")),
                formatTime(rs.getTimestamp("updated_at")),
                 Optional.ofNullable(rs.getString("last_message")).orElse(""),
                rs.getInt("message_count")
        ), userKey);
    }

    public AiCsConversationView findConversation(long id, String userKey) {
        String sql = """
                SELECT c.id, c.title, c.dify_conversation_id, c.created_at, c.updated_at,
                       (SELECT m.content FROM MLcs_message m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1) AS last_message,
                       (SELECT COUNT(*) FROM MLcs_message m WHERE m.conversation_id = c.id) AS message_count
                FROM MLcs_conversation c
                WHERE c.id = ? AND c.user_key = ?
                """;
        List<AiCsConversationView> rows = jdbc.query(sql, (rs, rowNum) -> new AiCsConversationView(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("dify_conversation_id"),
                formatTime(rs.getTimestamp("created_at")),
                formatTime(rs.getTimestamp("updated_at")),
                Optional.ofNullable(rs.getString("last_message")).orElse(""),
                rs.getInt("message_count")
        ), id, userKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public AiCsConversationView createConversation(String userKey, Integer userId, String title) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String safeTitle = normalizeTitle(title);
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO MLcs_conversation (user_key, userid, title) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, userKey);
            if (userId != null) ps.setInt(2, userId);
            else ps.setNull(2, java.sql.Types.INTEGER);
            ps.setString(3, safeTitle);
            return ps;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findConversation(id, userKey);
    }

    public void updateConversationAfterTurn(long id, String difyConversationId) {
        if (difyConversationId != null && !difyConversationId.isBlank()) {
            jdbc.update("UPDATE MLcs_conversation SET dify_conversation_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    difyConversationId.trim(), id);
        } else {
            jdbc.update("UPDATE MLcs_conversation SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
        }
    }

    public boolean deleteConversation(long id, String userKey) {
        return jdbc.update("DELETE FROM MLcs_conversation WHERE id = ? AND user_key = ?", id, userKey) > 0;
    }

    public List<AiCsMessageView> listMessages(long conversationId) {
        String sql = """
                SELECT id, conversation_id, role, content, intent, recommendations_json, orders_json, provider, created_at
                FROM MLcs_message
                WHERE conversation_id = ?
                ORDER BY id ASC
                """;
        return jdbc.query(sql, (rs, rowNum) -> new AiCsMessageView(
                rs.getLong("id"),
                rs.getLong("conversation_id"),
                rs.getString("role"),
                rs.getString("content"),
                rs.getString("intent"),
                parseRecommendations(rs.getString("recommendations_json")),
                parseOrders(rs.getString("orders_json")),
                rs.getString("provider"),
                formatTime(rs.getTimestamp("created_at"))
        ), conversationId);
    }

    public AiCsMessageView insertMessage(long conversationId,
                                         String role,
                                         String content,
                                         String intent,
                                         List<AiCsRecommendedProduct> recommendations,
                                         List<AiCsOrderSummary> orders,
                                         String provider) {
        String recJson = toJson(recommendations);
        String ordJson = toJson(orders);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO MLcs_message
                        (conversation_id, role, content, intent, recommendations_json, orders_json, provider)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, conversationId);
            ps.setString(2, role);
            ps.setString(3, content);
            ps.setString(4, intent);
            ps.setString(5, recJson);
            ps.setString(6, ordJson);
            ps.setString(7, provider);
            return ps;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        List<AiCsMessageView> rows = jdbc.query("""
                SELECT id, conversation_id, role, content, intent, recommendations_json, orders_json, provider, created_at
                FROM MLcs_message WHERE id = ?
                """, (rs, rowNum) -> new AiCsMessageView(
                rs.getLong("id"),
                rs.getLong("conversation_id"),
                rs.getString("role"),
                rs.getString("content"),
                rs.getString("intent"),
                parseRecommendations(rs.getString("recommendations_json")),
                parseOrders(rs.getString("orders_json")),
                rs.getString("provider"),
                formatTime(rs.getTimestamp("created_at"))
        ), id);
        return rows.get(0);
    }

    public PreferenceSignal loadUserPreferences(Integer userId, String username) {
        if (userId == null || userId < 1) {
            return new PreferenceSignal(List.of(), Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of());
        }
        List<Cat> cats = jdbc.query("SELECT * FROM MLcat WHERE owner = ? ORDER BY catid DESC",
                BeanPropertyRowMapper.newInstance(Cat.class), userId);

        Map<Integer, Double> productWeights = new HashMap<>();
        Map<String, Double> kindWeights = new HashMap<>();
        Map<String, Double> subkindWeights = new HashMap<>();
        Set<Integer> likedGoods = new HashSet<>();
        Set<Integer> purchasedGoods = new HashSet<>();
        Set<Integer> viewedGoods = new HashSet<>();

        jdbc.query("""
                SELECT v.goodid, LEAST(v.visits, 5) AS w, g.kind, g.subkind
                FROM MLproduct_view v
                JOIN MLgood g ON g.goodid = v.goodid
                WHERE v.userid = ?
                """, rs -> {
            int gid = rs.getInt("goodid");
            double w = rs.getDouble("w") * 1.5;
            viewedGoods.add(gid);
            productWeights.merge(gid, w, Double::sum);
            kindWeights.merge(safeTrim(rs.getString("kind")), w, Double::sum);
            subkindWeights.merge(safeTrim(rs.getString("subkind")), w, Double::sum);
        }, userId);

        jdbc.query("""
                SELECT b.goodid, g.kind, g.subkind
                FROM MLbuying b
                JOIN MLgood g ON g.goodid = b.goodid
                WHERE b.buyer = ? AND b.islike = 1
                """, rs -> {
            int gid = rs.getInt("goodid");
            double w = 4.0;
            likedGoods.add(gid);
            productWeights.merge(gid, w, Double::sum);
            kindWeights.merge(safeTrim(rs.getString("kind")), w, Double::sum);
            subkindWeights.merge(safeTrim(rs.getString("subkind")), w, Double::sum);
        }, userId);

        if (username != null && !username.isBlank()) {
            jdbc.query("""
                    SELECT COALESCE(oi.goodid, o.goodid) AS gid,
                           LEAST(SUM(COALESCE(oi.number, o.number)), 5) AS qty,
                           g.kind, g.subkind
                    FROM MLorder o
                    LEFT JOIN MLorder_item oi ON oi.orderid = o.orderid
                    JOIN MLgood g ON g.goodid = COALESCE(oi.goodid, o.goodid)
                    WHERE o.buyername = ? AND o.orderstate > 0
                    GROUP BY COALESCE(oi.goodid, o.goodid), g.kind, g.subkind
                    """, rs -> {
                int gid = rs.getInt("gid");
                double w = rs.getDouble("qty") * 5.0;
                purchasedGoods.add(gid);
                productWeights.merge(gid, w, Double::sum);
                kindWeights.merge(safeTrim(rs.getString("kind")), w, Double::sum);
                subkindWeights.merge(safeTrim(rs.getString("subkind")), w, Double::sum);
            }, username.trim());
        }

        return new PreferenceSignal(cats, productWeights, kindWeights, subkindWeights, likedGoods, purchasedGoods, viewedGoods);
    }

    public List<CartItemSummary> findUserCartItems(Integer userId) {
        if (userId == null || userId < 1) return List.of();
        String sql = """
                SELECT b.goodid, b.number, g.goodname, g.kind, g.subkind, g.price
                FROM MLbuying b
                JOIN MLgood g ON g.goodid = b.goodid
                WHERE b.buyer = ? AND b.number > 0 AND g.state = 0
                ORDER BY b.buyingid DESC
                LIMIT 20
                """;
        return jdbc.query(sql, (rs, rowNum) -> new CartItemSummary(
                rs.getInt("goodid"),
                safeTrim(rs.getString("goodname")),
                safeTrim(rs.getString("kind")),
                safeTrim(rs.getString("subkind")),
                rs.getDouble("price"),
                rs.getInt("number")
        ), userId);
    }

    public List<AiCsOrderSummary> findUserOrders(String buyername) {
        if (buyername == null || buyername.isBlank()) return List.of();
        String sql = """
                SELECT o.orderid, o.goodid, o.number, o.orderstate, o.recipientname, o.buyername, o.telephone, o.address,
                       g.goodname, g.picture, g.price
                FROM MLorder o
                LEFT JOIN MLgood g ON g.goodid = o.goodid
                WHERE o.buyername = ?
                ORDER BY o.orderid DESC
                LIMIT 20
                """;
        return jdbc.query(sql, (rs, rowNum) -> {
            int orderId = rs.getInt("orderid");
            int state = rs.getInt("orderstate");
            String recipient = safeTrim(rs.getString("recipientname"));
            if (recipient.isEmpty()) recipient = safeTrim(rs.getString("buyername"));

            List<Map<String, Object>> itemRows = jdbc.queryForList(
                    "SELECT goodid, goodname, price, number FROM MLorder_item WHERE orderid = ? ORDER BY id ASC",
                    orderId);

            String summaryName;
            double unitOrAvgPrice;
            int totalQty = rs.getInt("number");
            if (itemRows.size() > 1) {
                summaryName = itemRows.stream()
                        .map(r -> String.valueOf(r.get("goodname")).trim() + " ×" + r.get("number"))
                        .reduce((a, b) -> a + "、" + b)
                        .orElse("合并订单");
                double totalAmount = itemRows.stream()
                        .mapToDouble(r -> ((Number) r.get("price")).doubleValue() * ((Number) r.get("number")).intValue())
                        .sum();
                unitOrAvgPrice = totalQty > 0 ? totalAmount / totalQty : totalAmount;
            } else if (itemRows.size() == 1) {
                summaryName = String.valueOf(itemRows.get(0).get("goodname")).trim();
                unitOrAvgPrice = ((Number) itemRows.get(0).get("price")).doubleValue();
            } else {
                summaryName = Optional.ofNullable(rs.getString("goodname")).map(String::trim).orElse("商品#" + rs.getInt("goodid"));
                unitOrAvgPrice = rs.getDouble("price");
            }

            return new AiCsOrderSummary(
                    orderId,
                    rs.getInt("goodid"),
                    summaryName,
                    Optional.ofNullable(rs.getString("picture")).map(String::trim).orElse(""),
                    unitOrAvgPrice,
                    totalQty,
                    state,
                    orderStatusText(state),
                    recipient,
                    safeTrim(rs.getString("telephone")),
                    safeTrim(rs.getString("address")),
                    state >= 1 && state < 4,
                    state == 4
            );
        }, buyername.trim());
    }

    public static String orderStatusText(int state) {
        return switch (state) {
            case -1 -> "交易取消";
            case 0 -> "等待客户下单";
            case 1 -> "等待商家确认";
            case 2 -> "等待备货确认";
            case 3 -> "等待发货确认";
            case 4 -> "商家已发货，等待交易确认";
            case 5 -> "交易成功";
            default -> "未知状态";
        };
    }

    private String normalizeTitle(String title) {
        String cleaned = title == null ? "" : title.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) return "新对话";
        return cleaned.length() > 36 ? cleaned.substring(0, 36) + "..." : cleaned;
    }

    private String formatTime(Timestamp ts) {
        if (ts == null) return "";
        return ts.toLocalDateTime().format(TIME_FMT);
    }

    private String toJson(Object value) {
        if (value == null || (value instanceof Collection<?> col && col.isEmpty())) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            LOGGER.warn("Failed to serialize AI CS payload", e);
            return null;
        }
    }

    private List<AiCsRecommendedProduct> parseRecommendations(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<AiCsRecommendedProduct>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<AiCsOrderSummary> parseOrders(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<AiCsOrderSummary>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
