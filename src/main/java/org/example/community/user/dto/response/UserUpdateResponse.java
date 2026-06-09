package org.example.community.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 회원 정보 수정 응답 DTO
 *
 * 수정된 회원의 기본 공개 정보를 응답
 */
@Getter
@AllArgsConstructor
public class UserUpdateResponse {

    @JsonProperty("user_id")
    private Long userId;

    private String nickname;

    @JsonProperty("profile_image_url")
    private String profileImageUrl;
}