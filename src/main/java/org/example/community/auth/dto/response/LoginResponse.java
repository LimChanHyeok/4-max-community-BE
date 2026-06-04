package org.example.community.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 로그인 성공 시 클라이언트에게 반환하는 응답 DTO
 *
 * JWT 인증 방식에서는 로그인 성공 후 user_id만 내려주는 것이 아니라,
 * 이후 인증 요청에 사용할 Access Token, Refresh Token을 발급해줌
 * 하지만 지금은 Postman 테스트를 위해 access_token, refresh_token을 모두 body로 반환
 * 나중에 프론트 연결 후에는 refresh_token은 HttpOnly Cookie로
 * 응답 body에서는 access_token만 내려주는 방식으로 변경할 예정
 */
@Getter
@AllArgsConstructor
public class LoginResponse {

    /**
     * 인증이 필요한 API 요청에 사용할 토큰
     *
     * 클라이언트는 이 값을 저장해두었다가
     * 게시글 등록, 댓글 작성, 좋아요 같은 인증이 필요한 요청을 보낼 때
     * Authorization 헤더에 담아서 보낸다
     * ex)
     * Authorization: Bearer access_token값
     */
    @JsonProperty("access_token")
    private String accessToken;

//    /**
//     * Access Token이 만료되었을 때 재발급을 위한 Refresh Token
//     *
//     * 지금은 Postman 테스트를 위해 body로 반환
//     * 나중에 프론트 연결 후에는 HttpOnly Cookie로 내려줄 예정
//     */
//    @JsonProperty("refresh_token")
//    private String refreshToken;

    /**
     * 토큰 타입
     *
     * JWT를 Authorization 헤더에 보낼 때 보통 Bearer 방식을 사용하므로
     * 이 값은 "Bearer"로 고정함
     * 서로 Bearer을 쓰기로 약속하면 안써도 상관없지만 명확하게 알려주기 위해?
     */
    @JsonProperty("token_type")
    private String tokenType;
}