package org.example.community.postlike.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.community.post.entity.Post;
import org.example.community.user.entity.User;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_like")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike {

    // user_id와 post_id 복합키를 PostLikeId 객체로 가진다는 뜻
    @EmbeddedId
    private PostLikeId id;

    // PostLikeId와 User 연관관계를 연결하는 코드
    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // PostLikeId와 Post 연관관계를 연결하는 코드
    @MapsId("postId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // User,Post 객체를 받아 id를 채우도록 설정
    // 어차피 user와 post를 조회하는 일이 많기 때문에(연관 관계이기 때문에) 미리 조회한 값을 넣는것이 좋다고 판단
    private PostLike(User user, Post post) {
        this.id = new PostLikeId(user.getId(), post.getId());
        this.user = user;
        this.post = post;
    }


    public static PostLike create(User user, Post post) {
        return new PostLike(user, post);
    }
}