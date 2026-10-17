package com.example.maoliang.Service;

import com.example.maoliang.Entity.Cat;
import com.example.maoliang.Entity.Good;
import com.example.maoliang.Repository.CatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatRecommendationTests {
    @Test
    void mixedCatalogueRecommendsOnlyAvailableStapleFoodWithValidCalories() {
        CatRepository repository = mock(CatRepository.class);
        CatService service = new CatService();
        ReflectionTestUtils.setField(service, "catRepository", repository);
        Cat cat = new Cat();
        cat.setCatkind("布偶猫");
        cat.setCatage(2);
        cat.setCatweight(4);
        cat.setCatstate(3);
        when(repository.findCatById(7)).thenReturn(cat);
        List<Good> catalogue = List.of(
                product(1, "猫咪主粮", 3.5, 0, 20),
                product(2, "猫咪日用", 0, 0, 20),
                product(3, "猫咪零食", 3.5, 0, 20),
                product(4, "猫咪主粮", 0, 0, 20),
                product(5, "猫咪主粮", 3.5, 1, 20),
                product(6, "猫咪主粮", 3.5, 0, 0),
                product(7, "猫咪主粮", Double.NaN, 0, 20));
        when(repository.findGoodsByCatWeight(4)).thenReturn(catalogue);
        when(repository.findGoodsByCatAge(2)).thenReturn(catalogue);
        when(repository.findGoodsByCatKind("布偶猫")).thenReturn(catalogue);
        var results = service.showRecommend(7);
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).getGoodid());
        assertTrue(Double.isFinite(results.get(0).getWeight()));
        assertTrue(results.get(0).getWeight() > 0);
    }

    private Good product(int id, String kind, double calories, int state, int stock) {
        Good good = new Good();
        good.setGoodid(id);
        good.setKind(kind);
        good.setCalorie(calories);
        good.setState(state);
        good.setNumber(stock);
        return good;
    }
}
