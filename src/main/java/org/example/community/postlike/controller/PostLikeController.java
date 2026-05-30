package org.example.community.postlike.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.community.global.response.ApiResponse;
import org.example.community.postlike.dto.response.PostLikeResponse;
import org.example.community.postlike.service.PostLikeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/posts/{postId}/likes")
public class PostLikeController {

    private final PostLikeService postLikeService;

    @PostMapping
    public ResponseEntity<ApiResponse<PostLikeResponse>> likePost(
            @PathVariable Long postId,
            HttpServletRequest httpServletRequest

    ) {
        Long loginUserId = (Long) httpServletRequest.getAttribute("loginUserId");

        PostLikeResponse response = postLikeService.likePost(postId, loginUserId);

        return ResponseEntity.ok(
                ApiResponse.success("게시글 좋아요 등록에 성공했습니다.", response)
        );
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<PostLikeResponse>> unlikePost(
            @PathVariable Long postId,
            HttpServletRequest httpServletRequest
    ) {
        Long loginUserId = (Long) httpServletRequest.getAttribute("loginUserId");

        PostLikeResponse response = postLikeService.unlikePost(postId, loginUserId);

        return ResponseEntity.ok(
                ApiResponse.success("게시글 좋아요 취소에 성공했습니다.", response)
        );
    }
}