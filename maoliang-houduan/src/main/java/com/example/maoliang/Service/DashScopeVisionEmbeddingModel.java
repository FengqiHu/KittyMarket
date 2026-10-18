package com.example.maoliang.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Text-only independent vectors through DashScope's native multimodal endpoint. */
final class DashScopeVisionEmbeddingModel implements EmbeddingModel {
    private static final String PATH = "/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding";
    private final String apiKey;
    private final String model;
    private final URI endpoint;
    private final HttpClient client;
    private final ObjectMapper json = new ObjectMapper();

    DashScopeVisionEmbeddingModel(String apiKey, String baseUrl, String model) {
        this(apiKey, baseUrl, model, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    DashScopeVisionEmbeddingModel(String apiKey, String baseUrl, String model, HttpClient client) {
        this.apiKey = apiKey;
        this.model = model;
        this.client = client;
        String root = baseUrl.replaceAll("/+$", "").replaceFirst("/compatible-mode/v1$", "");
        this.endpoint = URI.create(root + PATH);
    }

    @Override
    public String modelName() { return model; }

    @Override
    public Response<Embedding> embed(String text) {
        return Response.from(embedAll(List.of(TextSegment.from(text))).content().get(0));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        if (segments.isEmpty()) return Response.from(List.of());
        if (segments.size() > 20) throw new IllegalArgumentException("DashScope supports at most 20 text inputs per request");
        try {
            String body = json.writeValueAsString(Map.of("model", model, "input", Map.of("contents",
                    segments.stream().map(segment -> Map.of("text", segment.text())).toList())));
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            // Avoid logging provider response bodies: they can echo submitted catalogue text.
            if (status == 429) throw new RateLimitException("DashScope embedding rate limit (HTTP 429)");
            if (status == 408 || status == 504) throw new TimeoutException("DashScope embedding timeout (HTTP " + status + ")");
            if (status < 200 || status >= 300) throw new IllegalStateException("DashScope embedding failed (HTTP " + status + ")");
            return Response.from(parseVectors(json.readTree(response.body()), segments.size()));
        } catch (HttpTimeoutException e) {
            throw new TimeoutException("DashScope embedding request timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DashScope embedding interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("DashScope embedding transport or JSON error", e);
        }
    }

    private List<Embedding> parseVectors(JsonNode root, int count) {
        JsonNode entries = root.path("output").path("embeddings");
        if (!entries.isArray() || entries.size() != count)
            throw new IllegalStateException("DashScope returned an unexpected embedding count");
        Embedding[] ordered = new Embedding[count];
        int dimensions = -1;
        for (JsonNode entry : entries) {
            JsonNode index = entry.path("index");
            int position = index.asInt(-1);
            JsonNode vector = entry.path("embedding");
            if (!index.isIntegralNumber() || position < 0 || position >= count || ordered[position] != null
                    || !vector.isArray() || vector.isEmpty()
                    || (dimensions != -1 && dimensions != vector.size()))
                throw new IllegalStateException("DashScope returned an invalid embedding index or dimension");
            dimensions = vector.size();
            float[] values = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                JsonNode value = vector.get(i);
                values[i] = (float) value.asDouble();
                if (!value.isNumber() || !Float.isFinite(values[i]))
                    throw new IllegalStateException("DashScope returned an invalid embedding value");
            }
            ordered[position] = Embedding.from(values);
        }
        return Arrays.asList(ordered);
    }
}
