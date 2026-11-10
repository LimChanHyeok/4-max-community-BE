package org.example.community.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
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
            new PublicEndpoint("POST", "/images/profiles"),
            new PublicEndpoint("GET", "/health"),
            new PublicEndpoint("GET", "/health/live"),
            new PublicEndpoint("GET", "/health/ready")
    );

    @Value("${management.server.port:0}")
    private int managementPort;

    public boolean matches(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if ("OPTIONS".equals(method)) {
            return true;
        }

        // 공개 HTTP 포트에서는 Prometheus 경로를 익명으로 노출하지 않는다.
        if (managementPort > 0 && request.getLocalPort() == managementPort
                && "GET".equals(method) && "/actuator/prometheus".equals(uri)) {
            return true;
        }

        if (uri.startsWith("/uploads/")) {
            return true;
        }

        if ("GET".equals(method) && uri.startsWith("/images/public/")) {
            return true;
        }

        return PUBLIC_ENDPOINTS.contains(
                new PublicEndpoint(method, uri)
        );
    }
}