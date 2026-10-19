package com.example.maoliang.Service;

import com.example.maoliang.Entity.Cat;
import com.example.maoliang.Entity.Good;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Repository.AiCustomerServiceRepository;
import com.example.maoliang.Repository.AiCustomerServiceRepository.PreferenceSignal;
import com.example.maoliang.Repository.GoodListRepository;
import com.example.maoliang.Repository.RecommendationRepository;
import com.example.maoliang.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiCustomerServiceTests {

    @Test
    void recommendsTopThreeProductsCombiningDemandAndUserPreferences() {
        AiCustomerServiceRepository repo = mock(AiCustomerServiceRepository.class);
        GoodListRepository goodListRepo = mock(GoodListRepository.class);
        RecommendationRepository recRepo = mock(RecommendationRepository.class);
        CatalogRagService ragService = mock(CatalogRagService.class);
        OrderService orderService = mock(OrderService.class);

        Good g1 = product(1, "鲜鸡肉幼猫奶糕粮", "猫咪主粮", "幼猫粮", 68.0, "高蛋白乳铁蛋白幼猫配方", "布偶猫,通用", "0-1", "0.5-5");
        Good g2 = product(2, "三文鱼幼猫烘焙粮", "猫咪主粮", "烘焙粮", 88.0, "低温烘焙美毛护肠胃幼猫主粮", "布偶猫,英短", "0-2", "0.5-6");
        Good g3 = product(3, "全价冻干生骨肉幼猫粮", "猫咪主粮", "冻干粮", 79.0, "鸡肉冻干双拼幼猫成长期主粮", "通用", "0-2", "0.5-8");
        Good g4 = product(4, "老年猫低脂控重粮", "猫咪主粮", "成猫粮", 92.0, "高龄猫咪低脂护肾主粮", "通用", "7-15", "3-10");
        Good g5 = product(5, "天然豆腐猫砂", "猫咪日用", "猫砂", 29.0, "快速结团低尘除臭", "通用", "0-20", "0-20");

        when(goodListRepo.shownow()).thenReturn(List.of(g1, g2, g3, g4, g5));
        when(ragService.search(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new CatalogSearchResult(List.of(g1, g2, g3), "", "keyword", List.of(1, 2, 3), 3, 1, 26));
        when(recRepo.page(anyString(), anyInt(), anyInt(), any()))
                .thenReturn(new GoodPage(List.of(g2, g1, g3, g4, g5), 5));

        Cat cat = new Cat();
        cat.setCatid(10);
        cat.setCatname("团子");
        cat.setCatkind("布偶猫");
        cat.setCatage(1);
        cat.setCatweight(2.5);
        cat.setCatstate(7);

        PreferenceSignal pref = new PreferenceSignal(
                List.of(cat),
                Map.of(2, 10.0, 1, 6.0),
                Map.of("猫咪主粮", 16.0),
                Map.of("烘焙粮", 10.0),
                Set.of(2),
                Set.of(),
                Set.of(1, 2)
        );

        Usr buyer = new Usr();
        buyer.setUserid(1);
        buyer.setUsername("111");
        buyer.setPower(0);

        AiCustomerService service = new AiCustomerService(
                repo, goodListRepo, recRepo, ragService, orderService, new ObjectMapper(),
                "", "https://api.dify.ai/v1", "", "", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus");

        List<AiCsRecommendedProduct> top3 = service.recommendThreeProducts("帮我推荐适合幼猫的主粮", buyer, pref);

        assertEquals(3, top3.size());
        Set<Integer> ids = Set.of(top3.get(0).goodid(), top3.get(1).goodid(), top3.get(2).goodid());
        assertEquals(Set.of(1, 2, 3), ids);
        assertTrue(top3.stream().anyMatch(p -> p.matchReason().contains("团子") || p.matchReason().contains("收藏")));
    }

    @Test
    void handlesOrderSupportAndCancellationInChat() {
        AiCustomerServiceRepository repo = mock(AiCustomerServiceRepository.class);
        GoodListRepository goodListRepo = mock(GoodListRepository.class);
        RecommendationRepository recRepo = mock(RecommendationRepository.class);
        CatalogRagService ragService = mock(CatalogRagService.class);
        OrderService orderService = mock(OrderService.class);

        Usr buyer = new Usr();
        buyer.setUserid(1);
        buyer.setUsername("111");
        buyer.setPower(0);

        AiCsConversationView conv = new AiCsConversationView(100L, "取消订单 #15", null, "2026-10-19 11:00", "2026-10-19 11:00", "", 2);
        when(repo.createConversation(anyString(), any(), anyString())).thenReturn(conv);
        when(repo.findConversation(eq(100L), anyString())).thenReturn(conv);
        when(repo.listMessages(100L)).thenReturn(List.of());
        when(repo.loadUserPreferences(any(), any())).thenReturn(
                new PreferenceSignal(List.of(), Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of()));

        AiCsOrderSummary cancelledOrder = new AiCsOrderSummary(
                15, 1, "鲜鸡肉幼猫奶糕粮", "/demo/products/p1.webp", 68.0, 2, -1, "交易取消",
                "张三", "13800138000", "上海市徐汇区", false, false);
        when(orderService.deleteOrder(15, -1, buyer)).thenReturn(true);
        when(repo.findUserOrders("111")).thenReturn(List.of(cancelledOrder));

        when(repo.insertMessage(eq(100L), anyString(), anyString(), any(), anyList(), anyList(), any()))
                .thenAnswer(inv -> new AiCsMessageView(
                        1L, 100L, inv.getArgument(1), inv.getArgument(2), inv.getArgument(3),
                        inv.getArgument(4), inv.getArgument(5), inv.getArgument(6), "2026-10-19 11:00"));

        AiCustomerService service = new AiCustomerService(
                repo, goodListRepo, recRepo, ragService, orderService, new ObjectMapper(),
                "", "https://api.dify.ai/v1", "", "", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus");

        AiCsChatResponse resp = service.chat(new AiCsChatRequest(null, "帮我取消订单 #15", "g_test"), buyer, "s1");

        verify(orderService).deleteOrder(15, -1, buyer);
        assertEquals("order_support", resp.assistantMessage().intent());
        assertTrue(resp.assistantMessage().content().contains("#15"));
        assertEquals(1, resp.assistantMessage().orders().size());
        assertEquals(-1, resp.assistantMessage().orders().get(0).orderstate());
    }

    private static Good product(int id, String name, String kind, String subkind, double price,
                                String desc, String catkind, String catage, String catweight) {
        Good g = new Good();
        g.setGoodid(id);
        g.setGoodname(name);
        g.setKind(kind);
        g.setSubkind(subkind);
        g.setPrice(price);
        g.setDescription(desc);
        g.setPicture("/demo/products/item.webp");
        g.setState(0);
        g.setNumber(12);
        g.setCatkind(catkind);
        g.setCatage(catage);
        g.setCatweight(catweight);
        return g;
    }
}

