package org.example.community.post.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 게시글 수정 요청 DTO
 * 게시글 수정은 multipart/form-data 요청으로 받는다.
 * title, content 같은 텍스트 값은 DTO로 받고
 * image 파일은 Controller에서 MultipartFile로 따로 받음
 */
@Getter
@Setter
public class PostUpdateRequest {

    /**
     * 수정할 게시글 제목
     */
    @NotBlank(message = "제목은 필수 입력값입니다.")
    @Size(max = 26, message = "제목은 최대 26자까지 가능합니다.")
    private String title;

    /**
     * 수정할 게시글 본문
     */
    @NotBlank(message = "내용은 필수 입력값입니다.")
    private String content;

    @JsonProperty("image_id")
    private Long imageId;
}