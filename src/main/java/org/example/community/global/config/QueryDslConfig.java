package org.example.community.global.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// JPAQueryFactory를 빈으로 등록하는 설정 클래스
@Configuration
public class QueryDslConfig {

    //EntityManager는 JPA에서 실제로 DB 작업을 관리하는 객체
    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}