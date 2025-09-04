package com.irondust.search.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class AdminBasicAuthFilter implements WebFilter {
    private final AppProperties appProperties;

    public AdminBasicAuthFilter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        // Protect only /admin and /admin-ui endpoints; leave others untouched
        if (!path.startsWith("/admin") && !path.startsWith("/admin-ui")) {
            return chain.filter(exchange);
        }

        // Allow /admin/raw/** endpoints when a valid x-admin-key is provided (for programmatic tooling/UI)
        if (path.startsWith("/admin/raw")) {
            String key = exchange.getRequest().getHeaders().getFirst("x-admin-key");
            String expected = appProperties.getAdminKey();
            if (expected != null && expected.equals(key)) {
                return chain.filter(exchange);
            }
            // else fall through to Basic auth check
        }

        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Basic ")) {
            return unauthorized(exchange.getResponse());
        }
        String base64 = auth.substring("Basic ".length());
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return unauthorized(exchange.getResponse());
        }
        int sep = decoded.indexOf(":");
        if (sep < 0) return unauthorized(exchange.getResponse());
        String user = decoded.substring(0, sep);
        String pass = decoded.substring(sep + 1);

        String expectedUser = appProperties.getAdminUsername();
        String expectedPass = appProperties.getAdminPassword();
        if (expectedUser == null) expectedUser = "admin";
        if (expectedPass == null) expectedPass = "admin";

        if (!expectedUser.equals(user) || !expectedPass.equals(pass)) {
            return unauthorized(exchange.getResponse());
        }
        return chain.filter(exchange);
    }

    private Mono<Void> unauthorized(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("WWW-Authenticate", "Basic realm=admin");
        return response.setComplete();
    }
}


