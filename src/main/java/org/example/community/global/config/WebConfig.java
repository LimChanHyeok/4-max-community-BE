package org.example.community.global.config;

import lombok.RequiredArgsConstructor;
import org.example.community.global.auth.resolver.LoginUserArgumentResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.List;

/**
 * 프론트가 image_url을 받으면 그 값을 그대로 이미지 src에 넣는것을 즉, 요청을 실제 파일과 연결해주는것
 * Springboot는 기본 상태에서 URL을 보고 자동으로 프로젝트폴더/uploads/posts/abc.png 파일을 찾아서 보내줘야지 라는 걸 알지 못함
 * 따라서 이 클래스는 URL주소와 실제 파일 위치를 맞춰주는 클래스
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginUserArgumentResolver loginUserArgumentResolver;

    /**
     * 직접 만든 HandlerMethodArgumentResolver를 Spring MVC에 등록
     */
    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> resolvers
    ) {
        resolvers.add(loginUserArgumentResolver);
    }
}