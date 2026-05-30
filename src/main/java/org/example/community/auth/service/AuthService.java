package org.example.community.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.community.auth.domain.RefreshToken;
import org.example.community.auth.dto.response.LoginResponse;
import org.example.community.auth.repository.RefreshTokenRepository;
import org.example.community.global.auth.JwtProvider;
import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.example.community.user.domain.User;
import org.example.community.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * 로그인 요청이 들어오면 이메일과 비밀번호를 검증한다.
     *
     * 검증에 성공하면 Access Token과 Refresh Token을 발급한다.
     * Access Token은 인증이 필요한 API 요청에 사용하고,
     * Refresh Token은 Access Token 재발급에 사용한다.
     *
     * 현재는 Postman에서 테스트하고 프론트가 연결이 안됐기 때문에 access, refresh 모두 바디로 응답함
     * 나중에 프론트가 연결되면 access는 바디에, refresh는 HttpOnly 쿠키로 응답할 계획
     */
    @Transactional
    public LoginResponse login(String email, String password) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }

        // 로그인한 사용자의 id를 담아서 Access Token과 Refresh Token을 생성
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        // RTR 방식이기 때문에 기존 Refresh Token이 있으면 제거
        refreshTokenRepository.deleteByUserId(user.getId());

        // DB에 새로 저장하기 위해 RefreshToken 객체 생성
        RefreshToken savedRefreshToken = new RefreshToken(
                user.getId(),
                refreshToken,
                jwtProvider.getRefreshTokenExpiredAt()
        );

        // 새 Refresh Token DB 저장
        refreshTokenRepository.save(savedRefreshToken);

        // 응답에서 Bearer 타입과 각 토큰 값을 전달
        return new LoginResponse(accessToken, refreshToken, "Bearer");
    }

    /**
     * Access Token 재발급 메서드
     *
     * 클라이언트가 보낸 Refresh Token을 검증하고,
     * DB에 저장된 Refresh Token인지 확인한다.
     *
     * RTR 방식이므로 재발급에 성공하면
     * 새 Access Token과 새 Refresh Token을 모두 발급 후에
     * DB의 Refresh Token도 삭제하고 다시 재발급해야함
     */
    @Transactional
    public LoginResponse reissue(String refreshToken) {

        // Refresh Token 자체가 유효한 JWT인지 확인
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        // Refresh Token에서 userId 추출
        Long userId = jwtProvider.getUserId(refreshToken);

        // DB에 저장된 Refresh Token인지 확인
        RefreshToken savedRefreshToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        // 토큰에서 꺼낸 userId와 DB에 저장된 userId가 같은지 확인
        if (!savedRefreshToken.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        // 새 Access Token과 새 Refresh Token 발급
        String newAccessToken = jwtProvider.createAccessToken(userId);
        String newRefreshToken = jwtProvider.createRefreshToken(userId);

        // RTR 방식이므로 DB의 Refresh Token을 새 값으로 교체
        refreshTokenRepository.updateToken(
                userId,
                newRefreshToken,
                jwtProvider.getRefreshTokenExpiredAt()
        );

        // 새 토큰 응답
        return new LoginResponse(newAccessToken, newRefreshToken, "Bearer");
    }
}