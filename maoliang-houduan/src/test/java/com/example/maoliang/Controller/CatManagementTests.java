package com.example.maoliang.Controller;

import com.example.maoliang.Entity.Cat;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Service.CatService;
import com.example.maoliang.Service.CatalogRagService;
import com.example.maoliang.dto.CatRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatManagementTests {
    @Test
    void createUsesLoggedInOwnerInsteadOfSubmittedOwner() {
        CatService service = mock(CatService.class);
        HttpSession session = mock(HttpSession.class);
        CatController controller = controller(service, session);
        Usr user = new Usr();
        user.setUserid(12);
        when(session.getAttribute("admin")).thenReturn(user);

        CatRequest request = validRequest();
        request.setOwner(99);
        request.setCatid(41);
        assertEquals("success", controller.addCat(request).getPage());
        verify(service).addCat(any(Cat.class), eq(12));

        when(session.getAttribute("admin")).thenReturn(null);
        assertEquals("error", controller.addCat(request).getPage());
        verifyNoMoreInteractions(service);
    }

    @Test
    void updateAndDeleteUseLoggedInOwnerAndRejectMissingCat() {
        CatService service = mock(CatService.class);
        HttpSession session = mock(HttpSession.class);
        CatController controller = controller(service, session);
        Usr user = new Usr();
        user.setUserid(12);
        when(session.getAttribute("admin")).thenReturn(user);

        when(service.updateCat(eq(41), any(Cat.class), eq(12))).thenReturn(true);
        assertEquals("success", controller.updateCat(41, validRequest()).getPage());
        assertEquals("error", controller.updateCat(42, validRequest()).getPage());
        when(service.deleteCat(41, 12)).thenReturn(true);
        assertEquals("success", controller.deleteCat(41).getPage());
        assertEquals("error", controller.deleteCat(42).getPage());
    }

    @Test
    void recommendationCannotReadAnotherUsersCat() {
        CatService service = mock(CatService.class);
        CatalogRagService rag = mock(CatalogRagService.class);
        HttpSession session = mock(HttpSession.class);
        CatController controller = controller(service, session);
        ReflectionTestUtils.setField(controller, "catalogRagService", rag);
        Usr user = new Usr();
        user.setUserid(12);
        when(session.getAttribute("admin")).thenReturn(user);

        assertEquals("error", controller.recommendForCat(41, 0).getPage());
        verify(service).getOwnedCat(41, 12);
        verifyNoInteractions(rag);
    }

    private CatController controller(CatService service, HttpSession session) {
        CatController controller = new CatController();
        ReflectionTestUtils.setField(controller, "catService", service);
        ReflectionTestUtils.setField(controller, "session", session);
        return controller;
    }

    private CatRequest validRequest() {
        CatRequest request = new CatRequest();
        request.setCatname("小橘");
        request.setCatkind("橘猫");
        request.setDescription("爱玩球");
        request.setCatage(2);
        request.setCatweight(4.5);
        request.setCatstate(3);
        return request;
    }
}
