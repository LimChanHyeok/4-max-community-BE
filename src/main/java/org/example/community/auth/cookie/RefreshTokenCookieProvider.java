package org.example.community.auth.cookie;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;


/**
 * 원래는 authController에 넣었었지만 나중에 쿠키를 삭제하는 기능이 추가되기 때문에 따로 만들어서 관리하기로ㅇ
 */
@Component
public class RefreshTokenCookieProvider {

    // 로컬과 배포 환경을 분리하기 위함
    @Value("${app.cookie.secure}")
    private boolean secure;

    @Value("${app.cookie.same-site}")
    private String sameSite;

    private static final int REFRESH_TOKEN_MAX_AGE = 14 * 24 * 60 * 60;

    /**
     * refreshToken 문자열을 받아서 쿠키 객체를 만드는 메소드
     */
     public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                // httpOnly -> 이 설정을 해야 javaScript에서 값을 못읽음, XSS 공격 방지
                .httpOnly(true)
                // 로컬이기 때문에 false, true면 https 환경에서만 쿠키가 저장됨
                .secure(secure)
                // refreshToken이기 때문에 /auth/reissue로 해야하나 했지만 지금 단계에선 /로 두었음
                .path("/")
                .maxAge(REFRESH_TOKEN_MAX_AGE)
                //CSRF 공격을 줄이기 위한 설정
                .sameSite(sameSite)
                .build();
    }

    /**
     * refreshToken 쿠키를 삭제하기 위한 쿠키를 만드는 메서드
     *
     * 쿠키는 서버가 직접 브라우저에서 삭제할 수 없기 때문에,
     * 같은 이름, 같은 path를 가진 쿠키를 maxAge(0)으로 다시 보내서 즉시 만료시킴
     */
    public ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(0)
                .sameSite(sameSite)
                .build();
    }
}