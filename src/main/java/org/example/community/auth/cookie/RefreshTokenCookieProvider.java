package org.example.community.auth.cookie;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;


/**
 * 원래는 authController에 넣었었지만 나중에 쿠키를 삭제하는 기능이 추가되기 때문에 따로 만들어서 관리하기로ㅇ
 */
@Component
public class RefreshTokenCookieProvider {

    private static final int REFRESH_TOKEN_MAX_AGE = 14 * 24 * 60 * 60;

    /**
     * refreshToken 문자열을 받아서 쿠키 객체를 만드는 메소드
     */
     public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                // httpOnly -> 이 설정을 해야 javaScript에서 값을 못읽음, XSS 공격 방지
                .httpOnly(true)
                // 로컬이기 때문에 false, true면 https 환경에서만 쿠키가 저장됨
                .secure(false)
                // refreshToken이기 때문에 /auth/reissue로 해야하나 했지만 지금 단계에선 /로 두었음
                .path("/")
                .maxAge(REFRESH_TOKEN_MAX_AGE)
                //CSRF 공격을 줄이기 위한 설정
                .sameSite("Lax")
                .build();
    }
}