package org.example.community.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.community.auth.cookie.RefreshTokenCookieProvider;
import org.example.community.auth.dto.request.LoginRequest;
import org.example.community.auth.dto.response.AuthTokenResult;
import org.example.community.auth.dto.response.LoginResponse;
import org.example.community.auth.service.AuthService;
import org.example.community.global.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {



    private final AuthService authService;

    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        //여기서 login은 아이디 비번 검증 후 토큰 발급해서 기존 refreshtoken
        //삭제 후 새 refreshtoken DB 저장 후 AuthTokenresult 반환
        AuthTokenResult tokenResult = authService.login(
                request.getEmail(),
                request.getPassword()
        );
        // 응답 바디용 loginResponse생성
        // 여기엔 refreshToken은 들어가지 않음
        LoginResponse response = new LoginResponse(
                tokenResult.getAccessToken(),
                tokenResult.getTokenType()
        );
        // AuthService가 발급한 RefreshToken을 가지고 쿠키를 만듬
        ResponseCookie refreshTokenCookie = refreshTokenCookieProvider.createRefreshTokenCookie(
                tokenResult.getRefreshToken()
        );

        return ResponseEntity.ok()
                // 응답 헤더에 refreshToken이 들어감
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(ApiResponse.success("로그인에 성공했습니다.", response));
    }

    /**
     * 토큰 재발급 API
     * 기존에 프로트가 연결이 안되었을 때는 body로 받았지만
     * 이제는 HttpOnly Cookie에 있기 때문에 쿠키에서 꺼내야함
     */
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<LoginResponse>> reissue(
            @CookieValue(name = "refreshToken") String refreshToken
    ) {
        // 쿠키에서 꺼낸 Refresh Token을 서비스로 넘겨 새 토큰을 재 발급
        AuthTokenResult tokenResult = authService.reissue(refreshToken);

        // LoginResponse는 응답바디 용이므로 마찬가지로 Access Token과 Type만 넘김
        LoginResponse response = new LoginResponse(
                tokenResult.getAccessToken(),
                tokenResult.getTokenType()
        );

        ResponseCookie refreshTokenCookie = refreshTokenCookieProvider.createRefreshTokenCookie(
                tokenResult.getRefreshToken()
        );

        return ResponseEntity.ok()
                // 응답 헤더에 refershToken이 들어감
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(ApiResponse.success("토큰 재발급에 성공했습니다.", response));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken
    ) {
        authService.logout(refreshToken);

        ResponseCookie deleteCookie = refreshTokenCookieProvider.deleteRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body(ApiResponse.success("로그아웃에 성공했습니다.", null));
    }
}