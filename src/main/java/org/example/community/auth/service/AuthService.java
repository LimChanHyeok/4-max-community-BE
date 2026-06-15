package org.example.community.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.community.auth.dto.response.AuthTokenResult;
import org.example.community.auth.dto.response.LoginResponse;
import org.example.community.auth.repository.RefreshTokenRepository;
import org.example.community.global.auth.JwtProvider;
import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.example.community.user.entity.User;
import org.example.community.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.community.auth.entity.RefreshToken;

import java.util.Optional;

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
     */
    @Transactional
    public AuthTokenResult login(String email, String password) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }

        // 로그인한 사용자의 id를 담아서 Access Token과 Refresh Token을 생성
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        // 현재 로그인한 사용자의 기존 Refresh Token을 조회한다.
        // refresh_token 테이블은 user_id에 UNIQUE 제약조건이 있기 때문에
        // 같은 user_id로 Refresh Token을 여러 개 저장할 수 없다.
        Optional<RefreshToken> existingRefreshToken =
                refreshTokenRepository.findByUserId(user.getId());

        if (existingRefreshToken.isPresent()) {
            // 기존 Refresh Token이 있으면 새로 INSERT하지 않고 토큰 값만 갱신한다.
            // JPA 변경 감지를 이용해 token, expiredAt 값을 UPDATE한다.
            existingRefreshToken.get().updateToken(
                    refreshToken,
                    jwtProvider.getRefreshTokenExpiredAt()
            );
        } else {
            // 기존 Refresh Token이 없으면 새로 저장
            RefreshToken savedRefreshToken = RefreshToken.create(
                    user.getId(),
                    refreshToken,
                    jwtProvider.getRefreshTokenExpiredAt()
            );

            refreshTokenRepository.save(savedRefreshToken);
        }

        // 응답에서 Bearer 타입과 각 토큰 값을 전달
        return new AuthTokenResult(accessToken, refreshToken, "Bearer");
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
    public AuthTokenResult reissue(String refreshToken) {

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
        // repository에서 조회해온 savedRefreshToken는 영속 상태가 된다.
        // 즉 영속상태가 된 savedRefreshToken의 값을 바꿔주면 jpa가 변경된 값을 감지해서 UPDATE SQL문을 날리게 됨
        savedRefreshToken.updateToken(
                newRefreshToken,
                jwtProvider.getRefreshTokenExpiredAt()
        );

        // 새 토큰 응답
        return new AuthTokenResult(newAccessToken, newRefreshToken, "Bearer");
    }

    @Transactional
    public void logout(String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.deleteByToken(refreshToken);
    }
    // jwt자체가 유효한지, DB에 저장된 refreshToken과 일치하는지 판단하는 메소드
    @Transactional(readOnly = true)
    public boolean isAuthenticated(String refreshToken) {
        // 쿠키 자체가 없는 비로그인 상태
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }

        // JWT 형식, 서명, 만료 여부 확인
        if (!jwtProvider.validateToken(refreshToken)) {
            return false;
        }

        // 검증된 Refresh Token에서 userId 추출
        Long userId = jwtProvider.getUserId(refreshToken);

        // DB에 같은 Refresh Token이 존재하고,
        // DB의 userId와 토큰의 userId가 같은지 확인, 추가 검증을 하는것
        return refreshTokenRepository.findByToken(refreshToken)
                .filter(savedToken -> savedToken.getUserId().equals(userId))
                .isPresent();
    }
}