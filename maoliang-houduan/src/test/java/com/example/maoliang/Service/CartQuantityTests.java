package com.example.maoliang.Service;

import com.example.maoliang.Repository.GoodRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartQuantityTests {
    @Test
    void cartPreservesTheStoredQuantityInsteadOfResettingItToZero() {
        GoodRepository repository = mock(GoodRepository.class);
        var rows = new ArrayList<Map<String, Object>>();
        rows.add(new HashMap<>(Map.of("id", 11, "quantity", 3)));
        when(repository.showBuyerCart(7)).thenReturn(rows);
        GoodService service = new GoodService();
        ReflectionTestUtils.setField(service, "goodRepository", repository);
        assertEquals(3, service.showBuyerCart(7).get(0).get("quantity"));
    }
}
