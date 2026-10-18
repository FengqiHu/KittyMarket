package com.example.maoliang.Service;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogIndexMessagingTests {
    @Test
    void confirmedEventIsRemovedFromOutbox() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(List.of(42L));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));

        new CatalogIndexPublisher(jdbc, rabbit).publishPending();

        verify(rabbit).convertAndSend(eq(CatalogIndexMessaging.EXCHANGE),
                eq(CatalogIndexMessaging.ROUTING_KEY), eq("42"), any(CorrelationData.class));
        verify(jdbc).update("DELETE FROM rag_catalog_outbox WHERE id = ?", 42L);
    }

    @Test
    void brokerFailureKeepsEventForNextAttempt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(List.of(42L));
        doThrow(new AmqpIOException(new IOException("broker unavailable"))).when(rabbit)
                .convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));

        new CatalogIndexPublisher(jdbc, rabbit).publishPending();

        verify(jdbc, never()).update(startsWith("DELETE FROM rag_catalog_outbox"), anyLong());
    }

    @Test
    void listenerRefreshesCurrentCatalogForDeliveredEvent() {
        CatalogRagService rag = mock(CatalogRagService.class);

        new CatalogIndexListener(rag).refresh("42");

        verify(rag).refreshIndex();
    }
}
