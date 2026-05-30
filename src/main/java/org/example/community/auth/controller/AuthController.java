package org.example.community.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.community.auth.dto.request.LoginRequest;
import org.example.community.auth.dto.request.TokenReissueRequest;
import org.example.community.auth.dto.response.LoginResponse;
import org.example.community.auth.service.AuthService;
import org.example.community.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(
                request.getEmail(),
                request.getPassword()
        );

        return ResponseEntity.ok(
                ApiResponse.success("로그인에 성공했습니다.", response)
        );
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<LoginResponse>> reissue(
            @Valid @RequestBody TokenReissueRequest request
    ) {
        LoginResponse response = authService.reissue(request.getRefreshToken());

        return ResponseEntity.ok(
                ApiResponse.success("토큰 재발급에 성공했습니다.", response)
        );
    }
}