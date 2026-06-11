package org.example.community.global.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // refreshToken 쿠키를 포함한 요청을 허용하기 위해 true
        config.setAllowCredentials(true);

        // 프론트 개발 서버 주소
        // 배포 시에는 실제 프론트 도메인으로 변경해야 함
        config.addAllowedOrigin("http://localhost:3000");

        // Authorization, Content-Type 등 모든 요청 헤더 허용
        config.addAllowedHeader("*");

        // GET, POST, PATCH, DELETE, OPTIONS 등 모든 메서드 허용
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        // 모든 경로에 CORS 설정 적용
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean =
                new FilterRegistrationBean<>(new CorsFilter(source));

        // JWT 필터보다 먼저 실행되도록 가장 높은 우선순위 설정
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return bean;
    }
}