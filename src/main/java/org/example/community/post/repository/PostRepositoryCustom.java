package org.example.community.post.repository;

import org.example.community.post.dto.response.PostDetailResponse;
import org.example.community.post.dto.response.PostSummaryResponse;

import java.util.List;
import java.util.Optional;

public interface PostRepositoryCustom {

    List<PostSummaryResponse> findPostsByCursor(Long cursor, int limit);

    Optional<PostDetailResponse> findPostDetailById(Long postId, Long loginUserId);
}