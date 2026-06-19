package org.example.community.post.repository;

import org.example.community.post.dto.response.PostSummaryResponse;
import org.example.community.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long>,PostRepositoryCustom {


    /**
     * 인메모리 버퍼에 쌓아둔 조회수를 나중에 DB에 한번에 반영하기 위한 쿼리
     * @Modifying -> Spring Data JPA에서 사용되는 어노테이션으로 @Query를 통해 변경이 일어나는 쿼리를 실행할 때 사용
     *
     */
    @Modifying
    @Query("""
    update Post p
    set p.viewCount = p.viewCount + :count
    where p.id = :postId
""")
    int increaseViewCountBy(
            @Param("postId") Long postId,
            @Param("count") Long count
    );
    // flushAutomatically= true -> 벌크 update 실행 전에 영속성 컨텍스트의 변경 사항을 먼저 DB에 반영하게 해준다.
    // 이 메소드를 실행하기전 flush를 한다는 의미
    @Modifying(flushAutomatically = true)
    @Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :postId")
    int increaseCommentCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Post p
            set p.commentCount = case
                when p.commentCount > 0 then p.commentCount - 1
                else 0
            end
            where p.id = :postId
            """)
    int decreaseCommentCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :postId")
    int increaseLikeCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Post p
            set p.likeCount = case
                when p.likeCount > 0 then p.likeCount - 1
                else 0
            end
            where p.id = :postId
            """)
    int decreaseLikeCount(@Param("postId") Long postId);

    /**
     * 좋아요 수 벌크 업데이트 이후 이미 조회해둔 post엔티티의 좋아수는 남아있는 이전값이기 때문에
     * 새로 조회하여 최신 값을 반영하기 위한 메소드
     */
    @Query("select p.likeCount from Post p where p.id = :postId")
    Long findLikeCountById(@Param("postId") Long postId);


}