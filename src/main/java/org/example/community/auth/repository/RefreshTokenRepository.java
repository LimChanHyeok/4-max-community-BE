package org.example.community.auth.repository;

import org.example.community.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Long> {

    // 로그인 성공 시 새 Refresh Token 저장
    // void save(RefreshToken refreshToken); JPA에서 제공함

    // RTR 방식에서 기존 Refresh Token을 새 Refresh Token으로 교체
    //void updateToken(Long userId, String newToken, java.time.LocalDateTime expiredAt); Repository에서 제거하고 Entity 변경 메서드 + 변경 감지로 처리

    // 재발급 요청 때 클라이언트가 보낸 Refresh Token이 DB에 있는지 확인
    Optional<RefreshToken> findByToken(String token);

    // 특정 유저의 Refresh Token 확인
    Optional<RefreshToken> findByUserId(Long userId);

    // 로그인 시 기존 Refresh Token 제거하거나 로그아웃 처리할 때 사용
    void deleteByUserId(Long userId);

    // 로그아웃 요청에서 Refresh Token 기준으로 삭제할 때 사용
    void deleteByToken(String token);
}