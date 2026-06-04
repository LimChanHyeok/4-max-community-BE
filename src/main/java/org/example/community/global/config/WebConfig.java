package org.example.community.global.config;

import lombok.RequiredArgsConstructor;
import org.example.community.global.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 프론트가 image_url을 받으면 그 값을 그대로 이미지 src에 넣는것을 즉, 요청을 실제 파일과 연결해주는것
 * Springboot는 기본 상태에서 URL을 보고 자동으로 프로젝트폴더/uploads/posts/abc.png 파일을 찾아서 보내줘야지 라는 걸 알지 못함
 * 따라서 이 클래스는 URL주소와 실제 파일 위치를 맞춰주는 클래스
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /**
     * 정적 리소스(이미지 파일) 경로를 추가 설정하는 메소드
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        /**
         * Path.get(프로젝트 루트에 uploads 경로를 가리킴)
         * toAbsolutepath()-> 상대경로를 절대 경로로 바꾸는 것
         * toUri() -> file:/Users/max/project/community/uploads/ 이런식으로 바꿈
         */
        String uploadPath = Paths.get("uploads").toAbsolutePath().toUri().toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 이 경로들은 다 AuthInterceptor을 거치게 한다는 것
                .addPathPatterns(
                        "**"
                )
                .excludePathPatterns(
                        "/auth",
                        "/auth/reissue",
                        "/users",
                        "/uploads/**"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 브라우저가 HttpOnly Refresh Token쿠키를 저장하고, 재발급 요청 때 쿠키를 같이 보낼 수 있음
                .allowCredentials(true);
    }

}