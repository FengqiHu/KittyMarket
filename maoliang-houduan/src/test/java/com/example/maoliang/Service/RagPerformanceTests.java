package com.example.maoliang.Service;

import com.example.maoliang.Entity.Good;
import com.example.maoliang.Repository.GoodListRepository;
import com.example.maoliang.Repository.CatalogEmbeddingRepository;
import com.example.maoliang.Repository.CatalogEmbeddingRepository.CachedVector;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagPerformanceTests {
    @Test
    void paginationAndCategoryChangesReuseRankingAndQueryVector() {
        GoodListRepository goods = mock(GoodListRepository.class);
        when(goods.shownow()).thenReturn(IntStream.rangeClosed(1,21).mapToObj(id -> product(id,"鸡肉"+id,"猫咪主粮",10)).toList());
        EmbeddingModel model = model();
        ChatModel chat = mock(ChatModel.class);
        CatalogRagService service = new CatalogRagService(goods,model,chat);
        var first = service.search("鸡肉","全部",1,8);
        var second = service.search("鸡肉","全部",2,8);
        var third = service.search("鸡肉","猫咪主粮",3,8);
        assertEquals("rag",first.mode());
        assertEquals(21,third.total());
        var ids = new java.util.HashSet<Integer>();
        for(var page : List.of(first,second,third)) page.items().forEach(g -> assertTrue(ids.add(g.getGoodid())));
        assertEquals(21,ids.size());
        verify(model,times(1)).embed("鸡肉");
        verify(model,times(3)).embedAll(anyList());
        verifyNoInteractions(chat);
    }

    @Test
    void restartReusesPersistedVectorsAndNamespaceIsIsolated() {
        GoodListRepository goods = mock(GoodListRepository.class);
        when(goods.shownow()).thenReturn(List.of(product(1,"鸡肉","猫咪主粮",10)));
        CatalogEmbeddingRepository cache = mock(CatalogEmbeddingRepository.class);
        Map<String,Map<Integer,CachedVector>> disk = new ConcurrentHashMap<>();
        when(cache.load(anyString())).thenAnswer(call -> List.copyOf(disk.getOrDefault(call.getArgument(0),Map.of()).values()));
        doAnswer(call -> {
            String namespace = call.getArgument(0);
            List<CachedVector> vectors = call.getArgument(1);
            vectors.forEach(vector -> disk.computeIfAbsent(namespace,key -> new ConcurrentHashMap<>()).put(vector.goodId(),vector));
            return null;
        }).when(cache).save(anyString(),anyList());
        EmbeddingModel firstModel = model();
        new CatalogRagService(goods,firstModel,null,cache,"model-a",1).search("鸡肉","全部");
        EmbeddingModel restartedModel = model();
        var restarted = new CatalogRagService(goods,restartedModel,null,cache,"model-a",1).search("鸡肉","全部");
        assertEquals("rag",restarted.mode());
        verify(restartedModel,never()).embedAll(anyList());
        EmbeddingModel changedModel = model();
        new CatalogRagService(goods,changedModel,null,cache,"model-b",1).search("鸡肉","全部");
        verify(changedModel,times(1)).embedAll(anyList());
    }

    @Test
    void onlyChangedDocumentsAreEmbeddedAgain() {
        GoodListRepository goods = mock(GoodListRepository.class);
        when(goods.shownow()).thenReturn(List.of(product(1,"鸡肉","猫咪主粮",10),product(2,"鱼肉","猫咪主粮",10)),
                List.of(product(1,"新鸡肉配方","猫咪主粮",10),product(2,"鱼肉","猫咪主粮",10)));
        EmbeddingModel model = model();
        CatalogRagService service = new CatalogRagService(goods,model,null);
        service.search("鸡肉","全部");
        service.search("鸡肉","全部");
        var batches = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(model,times(2)).embedAll(batches.capture());
        assertEquals(2,batches.getAllValues().get(0).size());
        assertEquals(1,batches.getAllValues().get(1).size());
        assertTrue(((TextSegment)batches.getAllValues().get(1).get(0)).text().contains("新鸡肉配方"));
        verify(model,times(1)).embed("鸡肉");
    }

    @Test
    void cachedRankingStillUsesLivePricesAvailabilityAndCategory() {
        GoodListRepository goods = mock(GoodListRepository.class);
        Good changedPrice = product(1,"鸡肉","猫咪主粮",10); changedPrice.setPrice(99);
        when(goods.shownow()).thenReturn(List.of(product(1,"鸡肉","猫咪主粮",10),product(2,"零食","猫咪零食",10)),
                List.of(changedPrice,product(2,"零食","猫咪零食",10)),
                List.of(product(1,"鸡肉","猫咪主粮",0),product(2,"零食","猫咪零食",10)),
                List.of(product(2,"零食","猫咪零食",10)));
        EmbeddingModel model = model();
        CatalogRagService service = new CatalogRagService(goods,model,null);
        service.search("鸡肉","全部");
        var price = service.search("鸡肉","猫咪主粮");
        assertEquals(1,price.items().size());
        assertEquals(99,price.items().get(0).getPrice());
        assertEquals(List.of(2),service.search("鸡肉","全部").items().stream().map(Good::getGoodid).toList());
        assertEquals(List.of(2),service.search("鸡肉","全部").items().stream().map(Good::getGoodid).toList());
        verify(model,times(1)).embedAll(anyList());
        verify(model,times(1)).embed("鸡肉");
    }

    @Test
    void productEmbeddingRequestsUseBoundedConcurrencyAndBatchesOfAtMostTen() {
        GoodListRepository goods = mock(GoodListRepository.class);
        when(goods.shownow()).thenReturn(IntStream.rangeClosed(1,41).mapToObj(id -> product(id,"鸡肉"+id,"猫咪主粮",10)).toList());
        EmbeddingModel model = model();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch concurrent = new CountDownLatch(2);
        when(model.embedAll(anyList())).thenAnswer(call -> {
            List<TextSegment> segments = call.getArgument(0);
            assertTrue(segments.size() <= 10);
            peak.accumulateAndGet(active.incrementAndGet(),Math::max);
            concurrent.countDown();
            try { assertTrue(concurrent.await(5,TimeUnit.SECONDS)); }
            finally { active.decrementAndGet(); }
            return Response.from(segments.stream().map(segment -> Embedding.from(new float[]{1,0})).toList());
        });
        var service = new CatalogRagService(goods,model,null,null,"parallel",2);
        assertEquals(41,service.search("鸡肉","全部").total());
        assertEquals(2,peak.get());
    }

    @Test
    void temporaryIndexTimeoutRetriesOnlyTheFailedBatch() {
        GoodListRepository goods = mock(GoodListRepository.class);
        when(goods.shownow()).thenReturn(List.of(product(1,"鸡肉","猫咪主粮",10)));
        EmbeddingModel model = model();
        when(model.embedAll(anyList())).thenThrow(new dev.langchain4j.exception.TimeoutException("temporary"))
                .thenReturn(Response.from(List.of(Embedding.from(new float[]{1,0}))));
        var result = new CatalogRagService(goods,model,null).search("鸡肉","全部");
        assertEquals("rag",result.mode());
        verify(model,times(2)).embedAll(anyList());
    }

    @Test
    void interruptedBuildCanResumePersistedCompletedBatchesAfterRestart() {
        GoodListRepository goods = mock(GoodListRepository.class);
        when(goods.shownow()).thenReturn(IntStream.rangeClosed(1,20)
                .mapToObj(id -> product(id,"鸡肉"+id,"猫咪主粮",10)).toList());
        CatalogEmbeddingRepository cache = mock(CatalogEmbeddingRepository.class);
        Map<Integer,CachedVector> disk = new ConcurrentHashMap<>();
        when(cache.load("resume")).thenAnswer(call -> List.copyOf(disk.values()));
        doAnswer(call -> {
            List<CachedVector> generated = call.getArgument(1);
            generated.forEach(vector -> disk.put(vector.goodId(),vector));
            return null;
        }).when(cache).save(eq("resume"),anyList());
        EmbeddingModel failedModel = model();
        when(failedModel.embedAll(anyList())).thenAnswer(call -> {
            List<TextSegment> batch = call.getArgument(0);
            if(batch.get(0).metadata().getInteger("goodid")==11) throw new IllegalStateException("upstream failure");
            return Response.from(batch.stream().map(segment -> Embedding.from(new float[]{1,0})).toList());
        });
        assertEquals("keyword",new CatalogRagService(goods,failedModel,null,cache,"resume",1).search("鸡肉","全部").mode());
        assertEquals(10,disk.size());
        EmbeddingModel recoveredModel = model();
        var recovered = new CatalogRagService(goods,recoveredModel,null,cache,"resume",1).search("鸡肉","全部");
        assertEquals("rag",recovered.mode());
        var batches = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(recoveredModel,times(1)).embedAll(batches.capture());
        assertEquals(10,batches.getValue().size());
        assertEquals(11,((TextSegment)batches.getValue().get(0)).metadata().getInteger("goodid"));
        assertEquals(20,disk.size());
    }

    private EmbeddingModel model() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embedAll(anyList())).thenAnswer(call -> {
            List<TextSegment> segments = call.getArgument(0);
            return Response.from(segments.stream().map(segment -> Embedding.from(new float[]{1,0})).toList());
        });
        when(model.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{1,0})));
        return model;
    }
    private Good product(int id,String name,String kind,int stock) {
        Good good = new Good(); good.setGoodid(id); good.setGoodname(name); good.setDescription("配方");
        good.setKind(kind); good.setSubkind(kind); good.setNumber(stock); good.setState(0);
        good.setCatkind("通用"); good.setCatage("0-20"); good.setCatweight("0-20"); good.setCalorie(4);
        return good;
    }
}
