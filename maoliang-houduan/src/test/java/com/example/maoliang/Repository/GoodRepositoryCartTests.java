package com.example.maoliang.Repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.*;

class GoodRepositoryCartTests {
    @Test
    void addingAnExistingCartItemBindsOnlyItsIdToTheIncrement() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new GoodRepository(jdbc).modifybuynumber(42, -1);
        verify(jdbc).update("UPDATE MLbuying SET number = number + 1 WHERE buyingid = ?", 42);
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void settingQuantityBindsQuantityBeforeTheCartId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new GoodRepository(jdbc).modifybuynumber(42, 3);
        verify(jdbc).update("UPDATE MLbuying SET number = ? WHERE buyingid = ?", 3, 42);
        verifyNoMoreInteractions(jdbc);
    }
}
