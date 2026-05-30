package org.example.community.global.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;

// @Component -> Spring에서 빈으로 등록
@Component
public class JwtProvider {

    // JWT에 서명할 때 사용하는 비밀키
    private final SecretKey secretKey;

    // Access Token 만료 시간
    private final long accessTokenExpiration;

    // Refresh Token 만료 시간
    private final long refreshTokenExpiration;

    public JwtProvider(
            // @Value -> application.properties값을 가져옴
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        // 문자열로 된 secret키를 JWT 서명에 사용할 수 있는 SecretKey 객체로 바꾸는 역할
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * userId를 받아서 JWT Access Token 문자열을 만들어주는 메소드
     * 나중에 로그인 성공 시
     * String accessToken = jwtProvider.createAccessToken(user.getId());
     * 이런 식으로 씀
     */
    public String createAccessToken(Long userId) {
        return createToken(userId, accessTokenExpiration);
    }

    /**
     * userId를 받아서 JWT Refresh Token 문자열을 만들어주는 메소드
     * 나중에 로그인 성공 시
     * String refreshToken = jwtProvider.createRefreshToken(user.getId());
     * 이런 식으로 씀
     */
    public String createRefreshToken(Long userId) {
        return createToken(userId, refreshTokenExpiration);
    }

    /**
     * Access Token과 Refresh Token을 실제로 생성하는 메소드
     * 두 토큰은 생성 방식은 같고 만료 시간만 다르기 때문에 공통 메소드로 분리
     */
    private String createToken(Long userId, long expirationTime) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                // subject -> 여기에 userId를 넣음, 보통 이 토큰의 주인공이 누구인가를 의미
                .subject(String.valueOf(userId))
                // 토큰 발급 시간
                .issuedAt(now)
                // 토큰 만료 시간
                .expiration(expiration)
                // 서버의 비밀키로 토큰에 서명하는 것
                .signWith(secretKey)
                // 최종적으로 xxxx.yyyy.zzzz 형식으로 문자열을 만들어줌
                .compact();
    }

    /**
     * 토큰이 유효한지 검증하는 메소드
     * 서버의 secretKey로 토큰 서명을 검증하고
     * 토큰이 만료되었는지도 함께 확인
     * 토큰이 정상이라면 true
     * 토큰이 위조되었거나 만료되었거나 형식이 잘못되었으면 false
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 토큰에서 userId를 꺼내는 메소드
     * JWT를 만들 때 subject에 userId를 넣었기 때문에,
     * 토큰을 파싱한 뒤 subject 값을 꺼내 Long 타입으로 변환한다.
     */
    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    /**
     * Refresh Token의 만료 시간을 LocalDateTime으로 계산하는 메소드
     *
     * refresh_token 테이블의 expired_at 컬럼에 저장할 값을 만들 때 사용
     */
    public LocalDateTime getRefreshTokenExpiredAt() {
        return LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000);
    }

    /**
     * JWT 문자열을 파싱해서 Claims를 꺼내는 공통 메소드
     * 여기서 Claims는 페이로드를 뜻함
     * Claims에는 subject, issuedAt, expiration 같은 JWT 내부 정보가 들어 있다.
     * 여기서 secretKey로 서명을 검증하기 때문에,
     * 위조된 토큰이면 예외가 발생한다.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                //parseSignedClaims(token) 여기서 JWT 전체를 검사함
                .parseSignedClaims(token)
                // 검사가 성공했을 때 그때 Payload를 가져옴
                .getPayload();
    }
}