package org.example.community.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Set;

// 어떤 요청이 인증 없이 접근 가능한지 판단하는 클래스
@Component
public class PublicEndpointMatcher {

    private record PublicEndpoint(
            String method,
            String path
    ) {
    }

    private static final Set<PublicEndpoint> PUBLIC_ENDPOINTS = Set.of(
            new PublicEndpoint("POST", "/auth"),
            new PublicEndpoint("DELETE", "/auth"),
            new PublicEndpoint("POST", "/auth/reissue"),
            new PublicEndpoint("GET", "/auth/status"),
            new PublicEndpoint("POST", "/users"),
            new PublicEndpoint("GET", "/users/email/check"),
            new PublicEndpoint("GET", "/users/nickname/check"),
            new PublicEndpoint("POST", "/images/profiles")
    );

    public boolean matches(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if ("OPTIONS".equals(method)) {
            return true;
        }

        if (uri.startsWith("/uploads/")) {
            return true;
        }

        return PUBLIC_ENDPOINTS.contains(
                new PublicEndpoint(method, uri)
        );
    }
}