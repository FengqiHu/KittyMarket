package com.example.maoliang.Config;

import com.example.maoliang.Controller.utils.Result;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Service.RequestRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(10)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> AUTH = Set.of("/usr/login-control", "/usr/register-control",
            "/usr/forgetPwd-control", "/usr/answer-control", "/usr/changedpwd-control");
    private static final Set<String> READ_POSTS = Set.of("/cart/showLike", "/cart/buyer-cart-control",
            "/good/search-list-control", "/good/search-list-nameonly-control", "/order/showorderinfo-control",
            "/order/showbuyerorderinfo-control");
    private final RateLimitProperties config;
    private final RequestRateLimiter limiter;
    private final ObjectMapper mapper;
    private volatile Set<String> trustedAddresses = Set.of();
    private volatile long resolveAgainAt;

    public RateLimitFilter(RateLimitProperties config, RequestRateLimiter limiter, ObjectMapper mapper) {
        this.config = config; this.limiter = limiter; this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !config.enabled() || "OPTIONS".equals(request.getMethod()) || path.equals("/error")
                || path.startsWith("/catalog/images/") || path.startsWith("/img/")
                || path.startsWith("/js/") || path.startsWith("/css/") || path.startsWith("/demo/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        String ip = "ip:" + clientAddress(request);
        var session = request.getSession(false);
        Object admin = session == null ? null : session.getAttribute("admin");
        // Identity comes only from the server-side session, never a userId or guestToken parameter.
        String identity = admin instanceof Usr user ? "user:" + user.getUserid() : ip;
        long wait = limiter.acquire(identity, "general", config.general());
        if (wait == 0 && AUTH.contains(path)) wait = limiter.acquire(ip, "auth", config.auth());
        else if (wait == 0 && (path.equals("/ai-cs/chat") || path.equals("/good/rag-search")
                || path.startsWith("/cat/recommend/") || path.startsWith("/cat/show-recommend/")
                || path.startsWith("/ai-cs/dify-tools/"))) wait = limiter.acquire(identity, "ai", config.ai());
        else if (wait == 0 && isWrite(request, path)) wait = limiter.acquire(identity, "write", config.write());
        if (wait == 0) { chain.doFilter(request, response); return; }
        long seconds = Math.max(1, (wait + 999) / 1000);
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(seconds));
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), new Result("/error", "操作太频繁，请在 " + seconds + " 秒后重试",
                Map.of("code", "RATE_LIMITED", "retryAfter", seconds)));
    }

    private boolean isWrite(HttpServletRequest request, String path) {
        // Several legacy mutation routes accept GET as well as POST.
        if (path.startsWith("/cart/")) return !READ_POSTS.contains(path) && !path.equals("/cart/allCart");
        if (path.startsWith("/order/")) return !READ_POSTS.contains(path);
        if (path.startsWith("/good/delete-good/") || path.startsWith("/good/upload-") || path.equals("/good/modify-price")) return true;
        return !Set.of("GET", "HEAD").contains(request.getMethod()) && !READ_POSTS.contains(path);
    }

    private String clientAddress(HttpServletRequest request) {
        // Only the configured reverse proxy may supply X-Real-IP. Direct requests cannot spoof it.
        if (config.trustedProxy() != null && !config.trustedProxy().isBlank()) {
            refreshTrustedAddresses();
            String forwarded = request.getHeader("X-Real-IP");
            if (trustedAddresses.contains(request.getRemoteAddr()) && forwarded != null
                    && forwarded.length() <= 45 && forwarded.matches("[0-9a-fA-F:.]+")) return forwarded;
        }
        return request.getRemoteAddr();
    }

    private synchronized void refreshTrustedAddresses() {
        long now = System.currentTimeMillis();
        if (now < resolveAgainAt) return;
        try {
            trustedAddresses = Arrays.stream(InetAddress.getAllByName(config.trustedProxy()))
                    .map(InetAddress::getHostAddress).collect(Collectors.toSet());
        } catch (UnknownHostException ignored) { trustedAddresses = Set.of(); }
        resolveAgainAt = now + 30_000;
    }
}
