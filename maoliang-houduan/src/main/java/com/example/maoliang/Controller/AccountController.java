package com.example.maoliang.Controller;

import com.example.maoliang.Controller.utils.Result;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Service.AccountService;
import com.example.maoliang.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/account")
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts = accounts; }
    private int userId(HttpSession session) {
        Usr user = (Usr) session.getAttribute("admin");
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return user.getUserid();
    }
    private Result ok(Object data) { return new Result("/success", "操作成功", data); }

    @GetMapping public Result current(HttpSession session) { return ok(accounts.current(userId(session))); }
    @PutMapping("/name") public Result rename(@RequestBody AccountNameData data, HttpSession session) {
        Usr user = accounts.rename(userId(session), data);
        session.setAttribute("admin", user);
        return ok(user);
    }
    @PutMapping("/password") public Result password(@RequestBody AccountPasswordData data, HttpServletRequest request) {
        accounts.changePassword(userId(request.getSession()), data);
        request.changeSessionId();
        return ok(null);
    }
    @GetMapping("/addresses") public Result addresses(HttpSession session) { return ok(accounts.addresses(userId(session))); }
    @PostMapping("/addresses") public Result create(@RequestBody AddressData data, HttpSession session) { return ok(accounts.saveAddress(userId(session), null, data)); }
    @PutMapping("/addresses/{id}") public Result update(@PathVariable long id, @RequestBody AddressData data, HttpSession session) { return ok(accounts.saveAddress(userId(session), id, data)); }
    @DeleteMapping("/addresses/{id}") public Result delete(@PathVariable long id, HttpSession session) { accounts.deleteAddress(userId(session), id); return ok(null); }
    @PutMapping("/addresses/{id}/default") public Result makeDefault(@PathVariable long id, HttpSession session) { accounts.defaultAddress(userId(session), id); return ok(null); }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result> invalid(IllegalArgumentException error) { return ResponseEntity.badRequest().body(new Result("/error", error.getMessage(), null)); }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result> rejected(ResponseStatusException error) { return ResponseEntity.status(error.getStatusCode()).body(new Result("/error", error.getReason(), null)); }
}
