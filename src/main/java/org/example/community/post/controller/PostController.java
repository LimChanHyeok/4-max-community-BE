package org.example.community.post.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.community.global.response.ApiResponse;
import org.example.community.post.dto.request.PostCreateRequest;
import org.example.community.post.dto.request.PostUpdateRequest;
import org.example.community.post.dto.response.PostCreateResponse;
import org.example.community.post.dto.response.PostDetailResponse;
import org.example.community.post.dto.response.PostListResponse;
import org.example.community.post.dto.response.PostUpdateResponse;
import org.example.community.post.service.PostService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<ApiResponse<PostListResponse>> getPosts(
            /**
             * 여기서 cursor와 size는 없을 수 있기 때문에 required=false로 하였다.
             */
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        PostListResponse response = postService.getPosts(cursor, size);

        return ResponseEntity.ok(
                ApiResponse.success("게시글 목록 조회에 성공했습니다.", response)
        );
    }

    /**
     * consumes = MediaType.MULTIPART_FORM_DATA_VALUE) -> API 가 받을 수 있는 요청 형식 지정
     * @Valid @ModelAttribute PostCreateRequest request -> multipart 요청안엔 필드도 있고 바이너리(이미지)도 있으니까
     * 일반 텍스트값은 DTO에 담겠다 라는것
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PostCreateResponse>> createPost(
            @Valid @ModelAttribute PostCreateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            HttpServletRequest httpServletRequest
    ) {
        Long loginUserId = (Long) httpServletRequest.getAttribute("loginUserId");

        PostCreateResponse response = postService.createPost(loginUserId,request, image);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("게시글 등록에 성공했습니다.", response));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPostDetail(
            @PathVariable Long postId,
            HttpServletRequest httpServletRequest
    ) {
        Long loginUserId = (Long) httpServletRequest.getAttribute("loginUserId");

        PostDetailResponse response = postService.getPostDetail(
                postId,
                loginUserId
        );

        return ResponseEntity.ok(
                ApiResponse.success("게시글 상세 조회에 성공했습니다.", response)
        );
    }

    /**
     * POST의 일부 내용을 수정할 수 있으니 PATCH사용
     */
    @PatchMapping(value = "/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PostUpdateResponse>> updatePost(
            @PathVariable Long postId,
            @Valid @ModelAttribute PostUpdateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            HttpServletRequest httpServletRequest
    ) {
        Long loginUserId = (Long) httpServletRequest.getAttribute("loginUserId");

        PostUpdateResponse response = postService.updatePost(
                postId,
                loginUserId,
                request.getTitle(),
                request.getContent(),
                image
        );

        return ResponseEntity.ok(
                ApiResponse.success("게시글 수정에 성공했습니다.", response)
        );
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable Long postId,
            HttpServletRequest httpServletRequest
    ) {
        Long loginUserId = (Long) httpServletRequest.getAttribute("loginUserId");

        postService.deletePost(postId, loginUserId);

        return ResponseEntity.ok(
                ApiResponse.success("게시글 삭제에 성공했습니다.", null)
        );
    }
}