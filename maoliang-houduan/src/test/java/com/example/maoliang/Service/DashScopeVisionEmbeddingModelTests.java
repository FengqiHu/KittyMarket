package com.example.maoliang.Service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DashScopeVisionEmbeddingModelTests {
    private final HttpClient client = mock(HttpClient.class);
    private final DashScopeVisionEmbeddingModel model = new DashScopeVisionEmbeddingModel("test-key",
            "https://dashscope.aliyuncs.com/compatible-mode/v1/", "tongyi-embedding-vision-plus-2026-03-06", client);

    @SuppressWarnings("unchecked")
    private void response(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @Test
    void sendsNativeIndependentTextsAndRestoresInputOrder() throws Exception {
        response(200, "{\"output\":{\"embeddings\":[{\"index\":1,\"embedding\":[0,1]},{\"index\":0,\"embedding\":[1,0]}]}}");
        var result = model.embedAll(List.of(TextSegment.from("猫粮\"\n"), TextSegment.from("幼猫"))).content();
        assertArrayEquals(new float[]{1,0}, result.get(0).vector());
        assertArrayEquals(new float[]{0,1}, result.get(1).vector());
        ArgumentCaptor<HttpRequest> captured = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captured.capture(), any(HttpResponse.BodyHandler.class));
        var request = captured.getValue();
        assertEquals("https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding", request.uri().toString());
        assertEquals("Bearer test-key", request.headers().firstValue("Authorization").orElseThrow());
        var resultBody = new CompletableFuture<String>();
        var bytes = new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk); }
            public void onError(Throwable failure) { resultBody.completeExceptionally(failure); }
            public void onComplete() { resultBody.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        var body = new ObjectMapper().readTree(resultBody.get());
        assertEquals(model.modelName(), body.path("model").asText());
        assertEquals("猫粮\"\n", body.at("/input/contents/0/text").asText());
        assertEquals(1, body.at("/input/contents/0").size());
        assertEquals(2, body.at("/input/contents").size());
    }

    @Test
    void singleQueryAndEmptyBatch() throws Exception {
        response(200, "{\"output\":{\"embeddings\":[{\"index\":0,\"embedding\":[1,2]}]}}");
        assertArrayEquals(new float[]{1,2}, model.embed("猫").content().vector());
        assertTrue(model.embedAll(List.of()).content().isEmpty());
        verify(client, times(1)).send(any(), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void rejectsMalformedResponsesBeforeCaching() throws Exception {
        for (String entries : List.of("[]",
                "[{\"index\":0,\"embedding\":[1]},{\"index\":0,\"embedding\":[2]}]",
                "[{\"index\":0,\"embedding\":[1]},{\"index\":1,\"embedding\":[2,3]}]",
                "[{\"index\":0,\"embedding\":[1]},{\"embedding\":[2]}]",
                "[{\"index\":0,\"embedding\":[1]},{\"index\":1,\"embedding\":[\"oops\"]}]")) {
            response(200, "{\"output\":{\"embeddings\":" + entries + "}}");
            assertThrows(IllegalStateException.class, () -> model.embedAll(List.of(TextSegment.from("a"), TextSegment.from("b"))));
        }
    }

    @Test
    void mapsRetryableErrorsWithoutExposingProviderBody() throws Exception {
        response(429, "private catalogue text");
        assertThrows(RateLimitException.class, () -> model.embed("猫"));
        response(504, "private catalogue text");
        assertThrows(TimeoutException.class, () -> model.embed("猫"));
        response(403, "private catalogue text and key");
        assertEquals("DashScope embedding failed (HTTP 403)", assertThrows(IllegalStateException.class, () -> model.embed("猫")).getMessage());
    }

    @Test
    void mapsTransportTimeoutForIndexRetries() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("timeout"));
        assertThrows(TimeoutException.class, () -> model.embed("猫"));
    }
}
