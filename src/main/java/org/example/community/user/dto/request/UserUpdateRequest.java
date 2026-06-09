package org.example.community.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원 정보 수정 요청 DTO이다.
 *
 * 현재는 닉네임만 텍스트 값으로 받고,
 * 프로필 이미지는 Controller에서 MultipartFile로 따로 받는다.
 */
@Getter
@Setter
public class UserUpdateRequest {

    /**
     * 수정할 닉네임
     *
     * multipart/form-data에서 @ModelAttribute로 값을 받기 때문에
     * setter가 필요하다.
     */
    @Size(max = 10, message = "닉네임은 최대 10자까지 가능합니다.")
    private String nickname;

    @JsonProperty("image_id")
    private Long imageId;
}