package com.example.maoliang.Controller;

import com.example.maoliang.Controller.utils.Result;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Repository.AiCustomerServiceRepository;
import com.example.maoliang.Service.AccountService;
import com.example.maoliang.Service.AiCustomerService;
import com.example.maoliang.dto.*;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai-cs")
@CrossOrigin
public class AiCustomerServiceController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiCustomerServiceController.class);

    @Autowired
    private AiCustomerService aiCustomerService;

    @Autowired
    private AiCustomerServiceRepository repository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private HttpSession session;

    @GetMapping("/conversations")
    public Result listConversations(@RequestParam(defaultValue = "") String guestToken) {
        Usr user = (Usr) session.getAttribute("admin");
        String userKey = AiCustomerService.resolveUserKey(user, guestToken, session.getId());
        List<AiCsConversationView> conversations = aiCustomerService.listConversations(userKey);
        return new Result("success", "获取对话历史成功", conversations);
    }

    @GetMapping("/conversations/{id}/messages")
    public Result getMessages(@PathVariable long id,
                              @RequestParam(defaultValue = "") String guestToken) {
        try {
            Usr user = (Usr) session.getAttribute("admin");
            String userKey = AiCustomerService.resolveUserKey(user, guestToken, session.getId());
            List<AiCsMessageView> messages = aiCustomerService.getConversationMessages(id, userKey);
            return new Result("success", "获取消息记录成功", messages);
        } catch (IllegalArgumentException e) {
            return new Result("error", e.getMessage(), null);
        }
    }

    @DeleteMapping("/conversations/{id}")
    public Result deleteConversation(@PathVariable long id,
                                     @RequestParam(defaultValue = "") String guestToken) {
        Usr user = (Usr) session.getAttribute("admin");
        String userKey = AiCustomerService.resolveUserKey(user, guestToken, session.getId());
        boolean deleted = aiCustomerService.deleteConversation(id, userKey);
        return deleted
                ? new Result("success", "对话已删除", true)
                : new Result("error", "对话不存在或无权删除", false);
    }

    @PostMapping("/chat")
    public Result chat(@RequestBody AiCsChatRequest request) {
        try {
            Usr user = (Usr) session.getAttribute("admin");
            AiCsChatResponse response = aiCustomerService.chat(request, user, session.getId());
            return new Result("success", "回复成功", response);
        } catch (IllegalArgumentException e) {
            return new Result("error", e.getMessage(), null);
        } catch (Exception e) {
            LOGGER.error("AI 客服对话处理失败", e);
            return new Result("error", "客服繁忙，请稍后再试", null);
        }
    }

    @PostMapping("/order-action")
    public Result orderAction(@RequestBody AiCsOrderActionRequest request) {
        try {
            Usr user = (Usr) session.getAttribute("admin");
            AiCsChatResponse response = aiCustomerService.performOrderActionInChat(request, user, session.getId());
            return new Result("success", "订单操作已执行", response);
        } catch (IllegalArgumentException e) {
            return new Result("error", e.getMessage(), null);
        } catch (Exception e) {
            LOGGER.error("AI 客服订单操作失败", e);
            return new Result("error", "订单操作失败，请稍后重试", null);
        }
    }

    /**
     * Dify 外部 HTTP 工具接口：根据用户需求与偏好推荐 3 款商品。
     */
    @GetMapping("/dify-tools/recommend")
    public Result difyToolRecommend(@RequestParam(defaultValue = "") String query,
                                    @RequestParam(required = false) Integer userId,
                                    @RequestHeader(value = "X-Dify-Tool-Token", defaultValue = "") String token) {
        if (!aiCustomerService.verifyToolToken(token)) {
            return new Result("error", "Invalid Dify tool token", null);
        }
        Usr user = null;
        if (userId != null && userId > 0) {
            try { user = accountService.current(userId); } catch (Exception ignored) {}
        }
        if (user == null) {
            user = (Usr) session.getAttribute("admin");
        }
        var prefSignal = repository.loadUserPreferences(
                user != null ? user.getUserid() : null,
                user != null ? user.getUsername() : null);
        List<AiCsRecommendedProduct> products = aiCustomerService.recommendThreeProducts(query, user, prefSignal);
        return new Result("success", "推荐成功", products);
    }

    /**
     * Dify 外部 HTTP 工具接口：查询指定买家的历史订单与状态。
     */
    @GetMapping("/dify-tools/orders")
    public Result difyToolOrders(@RequestParam(defaultValue = "") String username,
                                 @RequestHeader(value = "X-Dify-Tool-Token", defaultValue = "") String token) {
        if (!aiCustomerService.verifyToolToken(token)) {
            return new Result("error", "Invalid Dify tool token", null);
        }
        String buyerName = username;
        if (buyerName == null || buyerName.isBlank()) {
            Usr user = (Usr) session.getAttribute("admin");
            buyerName = user != null ? user.getUsername() : "";
        }
        List<AiCsOrderSummary> orders = repository.findUserOrders(buyerName);
        return new Result("success", "查询订单成功", orders);
    }
}
