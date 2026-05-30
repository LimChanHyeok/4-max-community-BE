package org.example.community.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * Access Token 재발급 요청 DTO
 *
 * 현재는 Postman 테스트 단계이므로
 * 클라이언트가 Refresh Token을 request body에 담아서 보냄
 *
 * 나중에 프론트 연결 후 HttpOnly Cookie 방식을 사용하면
 * request body가 아니라 Cookie에서 Refresh Token을 꺼내는 방식으로 변경할 예정
 */
@Getter
public class TokenReissueRequest {

    /**
     * Access Token이 만료되었을 때
     * 새 Access Token과 새 Refresh Token을 발급받기 위해 사용하는 토큰
     */
    @NotBlank(message = "Refresh Token은 필수입니다.")
    @JsonProperty("refresh_token")
    private String refreshToken;
}