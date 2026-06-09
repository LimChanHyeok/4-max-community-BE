package org.example.community.comment.repository;

import org.example.community.comment.dto.response.CommentCreateResponse;
import org.example.community.comment.dto.response.CommentSummaryResponse;

import java.util.List;
import java.util.Optional;

public interface CommentRepositoryCustom {

    /**
     * 댓글 등록 후 응답 DTO를 만들기 위해
     * 댓글,작성자정보,로그인 사용자가 작성자인지 확인하는 응답 DTO
     */
    Optional<CommentCreateResponse> findCreateResponseById(Long commentId, Long loginUserId);

    /**
     *특정 게시글에 달린 댓글을 커서 기반 페이징으로 조회하는 메소드
     */
    List<CommentSummaryResponse> findCommentsByCursor(
            Long postId,
            Long cursor,
            Long loginUserId,
            int limit
    );
}
