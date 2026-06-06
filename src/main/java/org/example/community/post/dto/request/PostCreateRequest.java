package org.example.community.post.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * PostCotroller에서 @ModelAttribute PostCreateRequest request 이 부분에서
 * form-data로 오는 일반 텍스트 값을 dto에 넣어주어야 하기 때문에 Setter를 추가하였다
 * @RequestBody PostCreateRequest request 기존 JSON 방식은 Jackson이 JSON을 객체로 바꿔주는 방식이라
 * @Getter만 있어도 동작한다.
 */
@Getter
@Setter
public class PostCreateRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 30, message = "제목은 30자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "본문은 필수입니다.")
    private String content;

    @JsonProperty("image_id")
    private Long imageId;
}