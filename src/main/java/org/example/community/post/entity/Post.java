package org.example.community.post.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.community.user.entity.User;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 게시글 작성자
    // posts.user_id FK와 users.id를 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false, length = 30)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 이미지 테이블을 따로 만들어서 관리하기 때문에 삭제
    // @Column(name = "image_url", length = 500)
    // private String imageUrl;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "comment_count", nullable = false)
    private Long commentCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 새 게시글을 생성할 때 필요한 생성자
     * private로 설정하여 create가 실행될때만 쓸 수 있게 함
     * id,created_at,updated_at 같은 경우는 DB와 JPA(Hibernate)가 자동으로 관리하도록 둠
     *
     * viewcount,likecount,commentcount는 처음 생성될 때 0으로 초기화
     * DB에서 defalut 0으로 설정을 해 놓았았고 기본형 변수라 알아서 0이 들어가지만
     * 읽는 사람에게 의도를 보여주기 위해 사용
     */
    private Post(User user, String title, String content) {
        this.user = user;
        this.title = title;
        this.content = content;
        this.viewCount = 0L;
        this.likeCount = 0L;
        this.commentCount = 0L;
    }

    /**
     *새 게시글을 생성하기 위한 메소드
     */
    public static Post create(User user, String title, String content) {
        return new Post(user, title, content);
    }

    /**
     * 게시글 수정 시 사용하는 메소드
     * 엔티티의 값을 변경하면 변경감지를 통해 알아서 UPDATE SQL을 날려줌
     */
    public void updatePost(String title, String content) {
        this.title = title;
        this.content = content;
    }


    // 밑에 메소드들은 Service에서 해도 되지만 그렇게 된다면 Setter를 써야한다는 점과
    // 감소 시키는 경우 감소할 때 0밑으로 내려가지 않게 하는걸 Service에 추가적으로 작성해주어야해서 엔티티에 정의함
    /**
     * 게시글 조회수를 1증가시킴
     */
    public void increaseViewCount() {
        this.viewCount++;
    }

    /**
     * 게시글 좋아요를 1증가시킴
     */
    public void increaseLikeCount() {
        this.likeCount++;
    }

    /**
     * 게시글 좋아요 1감소시킴
     */
    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    /**
     * 댓글 개수 1증가시킴
     */
    public void increaseCommentCount() {
        this.commentCount++;
    }

    /**
     * 댓글 개수 1 감소시킴
     */
    public void decreaseCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }
}