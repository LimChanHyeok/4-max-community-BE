package org.example.community.postlike.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

//이 클래스가 다른 엔티티안에 포함될 수 있는 값 타입이라는 걸 명시
@Embeddable
@Getter
// 복합키는 두 객체가 같은 키(객체) 인지 비교할 수 있어야하므로 equals와 hashcode가 필요함, Lombok이 자동으로 넣엊ㅁ
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// Serializable -> 자바에서 객체를 직렬화할 수 있다는 표시 인터페이스, 복합키는 객체 전체가 식별자이기 때문에
// Hibernate가 이 식별자 객체를 다룰 수 있게 하려면 무조건 구현해야함
public class PostLikeId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "post_id")
    private Long postId;

    public PostLikeId(Long userId, Long postId) {
        this.userId = userId;
        this.postId = postId;
    }
}