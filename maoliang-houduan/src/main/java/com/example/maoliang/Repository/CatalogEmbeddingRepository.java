package com.example.maoliang.Repository;

import dev.langchain4j.data.embedding.Embedding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.util.List;

/** Durable vectors, isolated by provider/model and verified against each product document. */
@Repository
public class CatalogEmbeddingRepository {
    public record CachedVector(int goodId, String documentHash, Embedding embedding) {}

    private final JdbcTemplate jdbc;

    public CatalogEmbeddingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<CachedVector> load(String namespace) {
        return jdbc.query("SELECT good_id, document_hash, vector_data FROM rag_product_embedding WHERE namespace = ?",
                (rs, row) -> {
                    byte[] bytes = rs.getBytes("vector_data");
                    if (bytes == null || bytes.length == 0 || bytes.length % Float.BYTES != 0)
                        throw new IllegalStateException("Invalid cached embedding");
                    float[] vector = new float[bytes.length / Float.BYTES];
                    ByteBuffer.wrap(bytes).asFloatBuffer().get(vector);
                    return new CachedVector(rs.getInt("good_id"), rs.getString("document_hash"), Embedding.from(vector));
                }, namespace);
    }

    public void save(String namespace, List<CachedVector> vectors) {
        jdbc.batchUpdate("""
                INSERT INTO rag_product_embedding(namespace, good_id, document_hash, vector_data)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE document_hash = VALUES(document_hash),
                    vector_data = VALUES(vector_data), updated_at = CURRENT_TIMESTAMP
                """, vectors, 10, (statement, vector) -> {
            ByteBuffer buffer = ByteBuffer.allocate(vector.embedding().dimension() * Float.BYTES);
            buffer.asFloatBuffer().put(vector.embedding().vector());
            statement.setString(1, namespace);
            statement.setInt(2, vector.goodId());
            statement.setString(3, vector.documentHash());
            statement.setBytes(4, buffer.array());
        });
    }
}
