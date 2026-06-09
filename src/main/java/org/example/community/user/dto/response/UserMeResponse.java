package org.example.community.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
// GET /users/me 전용 응답
public class UserMeResponse {

    @JsonProperty("user_id")
    private Long userId;

    private String email;

    private String nickname;

    @JsonProperty("profile_image_url")
    private String profileImageUrl;
}