package com.example.maoliang.Service;

import com.example.maoliang.Entity.Cat;
import com.example.maoliang.Entity.Good;
import com.example.maoliang.Repository.GoodListRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogRagServiceTests {
    @Test
    void searchWithoutKeyUsesCurrentInStockCatalogAndChineseTerms() {
        GoodListRepository repository = mock(GoodListRepository.class);
        when(repository.shownow()).thenReturn(List.of(
                product(1, "幼猫成长鸡肉粮", "幼猫鸡肉配方", "猫咪主粮", 10, "布偶猫", "0-1", "1-8", 4.1),
                product(2, "鸡肉猫条", "奖励零食", "猫咪零食", 10, "布偶猫", "1-10", "1-8", 0.8),
                product(3, "幼猫鸡肉主粮", "售罄", "猫咪主粮", 0, "布偶猫", "0-1", "1-8", 4.1)));
        CatalogRagService service = new CatalogRagService(repository, "", "unused", "unused", "unused");

        var result = service.search("适合幼猫的鸡肉主粮", "猫咪主粮");
        assertEquals("keyword", result.mode());
        assertEquals(List.of(1), result.items().stream().map(Good::getGoodid).toList());
        assertEquals(List.of(1), result.sourceIds());
    }

    @Test
    void recommendationFiltersBySelectedCatsProfileBeforeRanking() {
        GoodListRepository repository = mock(GoodListRepository.class);
        when(repository.shownow()).thenReturn(List.of(
                product(1, "适用主粮", "鸡肉", "猫咪主粮", 10, "布偶猫", "1-10", "1-8", 4.0),
                product(2, "其他品种主粮", "鸡肉", "猫咪主粮", 10, "波斯猫", "1-10", "1-8", 4.0),
                product(3, "零食", "鸡肉", "猫咪零食", 10, "布偶猫", "1-10", "1-8", 4.0),
                product(4, "幼猫主粮", "鸡肉", "猫咪主粮", 10, "布偶猫", "0-1", "1-8", 4.0)));
        CatalogRagService service = new CatalogRagService(repository, "", "unused", "unused", "unused");
        Cat cat = new Cat();
        cat.setCatid(7); cat.setCatname("小白"); cat.setCatkind("布偶猫");
        cat.setCatage(3); cat.setCatweight(4); cat.setCatstate(3);

        var result = service.recommend(cat);
        assertEquals(7, result.catid());
        assertEquals(List.of(1), result.sourceIds());
        assertTrue(result.items().get(0).getWeight() > 0);
        assertEquals("rules", result.mode());
    }

    @Test
    void ragRetrievesCatalogIdsAndKeepsGeneratedTextGrounded() {
        GoodListRepository repository = mock(GoodListRepository.class);
        when(repository.shownow()).thenReturn(List.of(
                product(11, "鸡肉主粮", "鲜鸡肉", "猫咪主粮", 10, "布偶猫", "1-10", "1-8", 4.0),
                product(12, "鱼肉主粮", "三文鱼", "猫咪主粮", 10, "布偶猫", "1-10", "1-8", 4.0)));
        EmbeddingModel embeddings = mock(EmbeddingModel.class);
        when(embeddings.embedAll(anyList())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            return Response.from(segments.stream()
                    .map(segment -> Embedding.from(segment.text().contains("鸡肉")
                            ? new float[]{1, 0} : new float[]{0, 1}))
                    .toList());
        });
        when(embeddings.embed(any(String.class))).thenReturn(Response.from(Embedding.from(new float[]{1, 0})));
        ChatModel chat = mock(ChatModel.class);
        when(chat.chat(anyString())).thenReturn("鸡肉商品与检索词匹配。[商品#11]");
        CatalogRagService service = new CatalogRagService(repository, embeddings, chat);

        var result = service.search("鸡肉", "猫咪主粮");
        assertEquals("rag", result.mode());
        assertEquals(11, result.items().get(0).getGoodid());
        assertTrue(result.summary().contains("[商品#11]"));
        verify(chat).chat(contains("商品#11"));
    }

    private Good product(int id, String name, String description, String kind, int stock,
                         String breed, String age, String weight, double calorie) {
        Good good = new Good();
        good.setGoodid(id); good.setGoodname(name); good.setDescription(description);
        good.setKind(kind); good.setSubkind(kind); good.setNumber(stock); good.setState(0);
        good.setCatkind(breed); good.setCatage(age); good.setCatweight(weight); good.setCalorie(calorie);
        return good;
    }
}
