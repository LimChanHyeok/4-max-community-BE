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


}