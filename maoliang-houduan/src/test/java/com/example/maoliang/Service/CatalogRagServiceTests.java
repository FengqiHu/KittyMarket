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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogRagServiceTests {
    @Test
    void searchPagesThroughAllMatchesInsteadOfStoppingAtSixteen() {
        GoodListRepository repository = mock(GoodListRepository.class);
        when(repository.shownow()).thenReturn(IntStream.rangeClosed(1, 21)
                .mapToObj(id -> product(id, "鸡肉主粮" + id, "鲜鸡肉", "猫咪主粮", 10,
                        "布偶猫", "1-10", "1-8", 4.0))
                .toList());
        CatalogRagService service = new CatalogRagService(repository, "", "unused", "unused", "unused");

        var first = service.search("鸡肉", "猫咪主粮", 1, 8);
        var second = service.search("鸡肉", "猫咪主粮", 2, 8);
        var third = service.search("鸡肉", "猫咪主粮", 3, 8);

        assertEquals(21, first.total());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), first.items().stream().map(Good::getGoodid).toList());
        assertEquals(List.of(9, 10, 11, 12, 13, 14, 15, 16), second.items().stream().map(Good::getGoodid).toList());
        assertEquals(List.of(17, 18, 19, 20, 21), third.items().stream().map(Good::getGoodid).toList());
        assertEquals(21, service.search("", "全部", 3, 8).total());
        assertTrue(service.search("鸡肉", "全部", 4, 8).items().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.search("鸡肉", "全部", 0, 8));
    }

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
    void ragRetrievesCatalogIdsWithoutGeneratingHiddenSearchExplanation() {
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
        assertEquals(1, result.total());
        assertEquals(11, result.items().get(0).getGoodid());
        assertEquals("", result.summary());
        verifyNoInteractions(chat);
    }

    @Test
    void semanticSynonymsRemainPaginatedWhileIrrelevantProductsAreExcluded() {
        GoodListRepository repository = mock(GoodListRepository.class);
        var catalog = new java.util.ArrayList<>(IntStream.rangeClosed(1, 21)
                .mapToObj(id -> product(id, "鲜禽配方" + id, "营养配方", "猫咪主粮", 10,
                        "通用", "0-20", "0-20", 4)).toList());
        catalog.add(product(22, "猫抓板", "磨爪", "猫咪日用", 10, "通用", "0-20", "0-20", 0));
        EmbeddingModel embeddings = semanticModel(segment -> segment.text().contains("鲜禽")
                ? new float[]{1, 0} : new float[]{0, 1});
        when(repository.shownow()).thenReturn(catalog);
        var service = new CatalogRagService(repository, embeddings, null);
        var first = service.search("鸡肉粮", "全部", 1, 8);
        var third = service.search("鸡肉粮", "全部", 3, 8);
        assertEquals("rag", first.mode());
        assertEquals(21, first.total());
        assertEquals(5, third.items().size());
        assertTrue(first.items().stream().noneMatch(good -> good.getGoodid() == 22));
        assertTrue(service.search("鸡肉粮", "全部", 4, 8).items().isEmpty());
        assertEquals(0, service.search("鸡肉粮", "猫咪日用", 1, 8).total());
        verify(embeddings, times(1)).embed("鸡肉粮");
    }

    @Test
    void scoreWindowExcludesWeakTailEvenAboveAbsoluteFloor() {
        GoodListRepository repository = mock(GoodListRepository.class);
        when(repository.shownow()).thenReturn(List.of(
                product(1, "强相关", "", "猫咪主粮", 10, "通用", "0-20", "0-20", 4),
                product(2, "弱相关", "", "猫咪主粮", 10, "通用", "0-20", "0-20", 4)));
        // The weak result has cosine 0.8 / LangChain4j score 0.9: above 0.85 but outside the top-score window.
        var embeddings = semanticModel(segment -> segment.text().contains("强相关")
                ? new float[]{1, 0} : new float[]{0.8f, 0.6f});
        var result = new CatalogRagService(repository, embeddings, null).search("需求", "全部");
        assertEquals(List.of(1), result.items().stream().map(Good::getGoodid).toList());
        assertEquals(1, result.total());
    }

    @Test
    void successfulSemanticSearchWithNoRelevantMatchStaysEmpty() {
        GoodListRepository repository = mock(GoodListRepository.class);
        when(repository.shownow()).thenReturn(List.of(
                product(1, "手机挂件", "手机字样只是装饰", "猫咪日用", 10, "通用", "0-20", "0-20", 0)));
        var embeddings = semanticModel(segment -> new float[]{0.6f, 0.8f});
        var service = new CatalogRagService(repository, embeddings, null);
        var result = service.search("手机", "全部");
        assertEquals("rag", result.mode());
        assertEquals(0, result.total());
        assertTrue(result.items().isEmpty());
        assertTrue(result.sourceIds().isEmpty());
        // Empty query still browses the catalogue.
        assertEquals(1, service.search("", "全部").total());
    }

    @Test
    void recommendationKeepsProfileEligibleProductsWithoutSearchCutoff() {
        GoodListRepository repository = mock(GoodListRepository.class);
        when(repository.shownow()).thenReturn(List.of(
                product(1, "主粮", "配方", "猫咪主粮", 10, "通用", "0-20", "0-20", 4)));
        var embeddings = semanticModel(segment -> new float[]{0, 1});
        Cat cat = new Cat(); cat.setCatid(1); cat.setCatkind("布偶猫");
        cat.setCatname("小白"); cat.setCatage(3); cat.setCatweight(4); cat.setCatstate(3);
        var result = new CatalogRagService(repository, embeddings, null).recommend(cat);
        assertEquals(List.of(1), result.sourceIds());
    }

    private EmbeddingModel semanticModel(java.util.function.Function<TextSegment, float[]> vectors) {
        EmbeddingModel embeddings = mock(EmbeddingModel.class);
        when(embeddings.embedAll(anyList())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            return Response.from(segments.stream().map(segment -> Embedding.from(vectors.apply(segment))).toList());
        });
        when(embeddings.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{1, 0})));
        return embeddings;
    }

    @Test
    void refreshRebuildsEmbeddingsAfterProductDocumentChanges() {
        GoodListRepository repository = mock(GoodListRepository.class);
        Good initial = product(11, "鸡肉主粮", "鲜鸡肉", "猫咪主粮", 10, "布偶猫", "1-10", "1-8", 4.0);
        Good revised = product(11, "鱼肉主粮", "三文鱼", "猫咪主粮", 10, "布偶猫", "1-10", "1-8", 4.0);
        when(repository.shownow()).thenReturn(List.of(initial), List.of(revised));
        EmbeddingModel embeddings = mock(EmbeddingModel.class);
        when(embeddings.embedAll(anyList())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            return Response.from(segments.stream().map(segment -> Embedding.from(new float[]{1, 0})).toList());
        });
        CatalogRagService service = new CatalogRagService(repository, embeddings, null);

        service.refreshIndex();
        service.refreshIndex();

        verify(embeddings, times(2)).embedAll(anyList());
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
