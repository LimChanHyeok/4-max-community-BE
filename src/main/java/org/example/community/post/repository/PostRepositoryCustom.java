package org.example.community.post.repository;

import org.example.community.post.dto.response.PostCreateResponse;
import org.example.community.post.dto.response.PostDetailResponse;
import org.example.community.post.dto.response.PostSummaryResponse;

import java.util.List;
import java.util.Optional;

public interface PostRepositoryCustom {

    List<PostSummaryResponse> findPostsByCursor(Long cursor, int limit);

    Optional<PostDetailResponse> findPostDetailById(Long postId, Long loginUserId);

    // 서비스에서 조회하는 로직을 담당하고 있어서 따로 빼기 위해서 추가
    Optional<PostCreateResponse> findCreateResponseById(Long postId);
}