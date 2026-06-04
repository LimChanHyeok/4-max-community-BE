package org.example.community.post.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 게시글 상세 조회 응답 DTO이다.
 *
 * 게시글 상세 정보,작성자 최소 정보 응답
 */
@Getter
@AllArgsConstructor
public class PostDetailResponse {

    @JsonProperty("post_id")
    private Long postId;

    private String title;

    private String content;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("like_count")
    private Long likeCount;

    @JsonProperty("comment_count")
    private Long commentCount;

    @JsonProperty("view_count")
    private Long viewCount;

    @JsonProperty("is_liked")
    private boolean liked;

    @JsonProperty("is_writer")
    private boolean writerStatus;

    private PostWriterResponse writer;
}