package com.example.maoliang.Repository;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"app.ai.qwen.api-key=", "app.rag.mq.enabled=false"})
@Transactional
class CatalogEmbeddingRepositoryIntegrationTests {
    @Autowired CatalogEmbeddingRepository repository;

    @Test
    void binaryVectorsSurviveReloadUpdatesAndNamespaceIsolation() {
        String namespace = UUID.randomUUID().toString().replace("-", "").repeat(2);
        String otherNamespace = UUID.randomUUID().toString().replace("-", "").repeat(2);
        repository.save(namespace,List.of(new CatalogEmbeddingRepository.CachedVector(1,"a".repeat(64),
                Embedding.from(new float[]{0.125f,-0.75f,1.0f}))));
        var loaded = repository.load(namespace);
        assertEquals(1,loaded.size());
        assertEquals("a".repeat(64),loaded.get(0).documentHash());
        assertArrayEquals(new float[]{0.125f,-0.75f,1.0f},loaded.get(0).embedding().vector());
        assertTrue(repository.load(otherNamespace).isEmpty());
        repository.save(namespace,List.of(new CatalogEmbeddingRepository.CachedVector(1,"b".repeat(64),
                Embedding.from(new float[]{1,0,0}))));
        assertEquals(1,repository.load(namespace).size());
        assertEquals("b".repeat(64),repository.load(namespace).get(0).documentHash());
        assertArrayEquals(new float[]{1,0,0},repository.load(namespace).get(0).embedding().vector());
    }
}
