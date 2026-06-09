package org.example.community.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 회원 공개 프로필 조회 응답 DTO이다.
 *
 * 공개 프로필 조회에서는 비밀번호를 절대 포함하지 않고,
 * 이메일도 개인정보에 가까우므로 응답에서 제외시켰다.
 */
@Getter
@AllArgsConstructor
public class UserProfileResponse {

    @JsonProperty("user_id")
    private Long userId;

    private String nickname;

    @JsonProperty("profile_image_url")
    private String profileImageUrl;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
