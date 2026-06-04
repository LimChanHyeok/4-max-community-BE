package org.example.community.comment.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 댓글 등록 응답 DTO
 * 필드에 commentWriterRespnose를 추가하여 데이터안에 작성자정보도 응답데이터로 넣었음
 */
@Getter
@AllArgsConstructor
public class CommentCreateResponse {

    @JsonProperty("comment_id")
    private Long commentId;

    private String content;

    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("is_writer")
    private boolean writerStatus;

    private CommentWriterResponse writer;


    /**
     * QueryDSL에서 생성자로 DTO 프로젝션을 이용하였기 때문에 추가
     */
    public CommentCreateResponse(
            Long commentId,
            String content,
            LocalDateTime createdAt,
            Long userId,
            String nickname,
            String profileImage,
            Boolean writerStatus
    ) {
        this.commentId = commentId;
        this.content = content;
        this.createdAt = createdAt;
        this.writer = new CommentWriterResponse(
                userId,
                nickname,
                profileImage
        );
        this.writerStatus = writerStatus;
    }
}