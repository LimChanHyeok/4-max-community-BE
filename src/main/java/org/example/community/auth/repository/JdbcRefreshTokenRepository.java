package org.example.community.auth.repository;

import lombok.RequiredArgsConstructor;
import org.example.community.auth.domain.RefreshToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcRefreshTokenRepository implements RefreshTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 로그인 성공 시 새로 발급한 Refresh Token을 DB에 저장하는 메서드
     *
     * Access Token은 DB에 저장하지 않지만,
     * Refresh Token은 재발급과 로그아웃 처리를 위해 DB에 저장
     */
    @Override
    public void save(RefreshToken refreshToken) {
        String sql = """
                INSERT INTO refresh_token (
                    user_id,
                    token,
                    expired_at
                )
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                refreshToken.getUserId(),
                refreshToken.getToken(),
                refreshToken.getExpiredAt()
        );
    }

    /**
     * Refresh Token 문자열로 DB에 저장된 토큰을 조회
     *
     * 재발급 요청이 들어왔을 때,
     * 클라이언트가 보낸 Refresh Token이 DB에 존재하는지 확인하기 위해 사용
     */
    @Override
    public Optional<RefreshToken> findByToken(String token) {
        String sql = """
                SELECT
                    id,
                    user_id,
                    token,
                    expired_at,
                    created_at,
                    updated_at
                FROM refresh_token
                WHERE token = ?
                """;

        return jdbcTemplate.query(sql, refreshTokenRowMapper(), token)
                .stream()
                .findFirst();
    }

    /**
     * userId로 해당 사용자의 Refresh Token을 조회
     * user_id는 unique이기 때문에 한사람당 하나의 refresh Token이 조회됨
     */
    @Override
    public Optional<RefreshToken> findByUserId(Long userId) {
        String sql = """
                SELECT
                    id,
                    user_id,
                    token,
                    expired_at,
                    created_at,
                    updated_at
                FROM refresh_token
                WHERE user_id = ?
                """;

        return jdbcTemplate.query(sql, refreshTokenRowMapper(), userId)
                .stream()
                .findFirst();
    }
    /**
     * 기존 Refresh Token을 새 Refresh Token으로 교체
     * RTR 방식에서는 Access Token을 재발급 할 때마다 같이 재발급되기 때문에 갱신하는 메소드
     */
    @Override
    public void updateToken(Long userId, String newToken, LocalDateTime expiredAt) {
        String sql = """
                UPDATE refresh_token
                SET token = ?,
                    expired_at = ?,
                    updated_at = NOW()
                WHERE user_id = ?
                """;

        jdbcTemplate.update(sql, newToken, expiredAt, userId);
    }
    /**
     * userId를 기준으로 Refresh Token을 삭제
     * 로그인 시 기존 토큰을 제거하거나
     * 로그아웃 시 해당 사용자의 Refresh token을 무효화할 때 사용함
     * 즉, 로그인 할 때 기준 토큰을 삭제할 때 유저 기준으로 삭제하는 것 -> RTR 방식으로 구현하기 때문에
     */
    @Override
    public void deleteByUserId(Long userId) {
        String sql = """
                DELETE FROM refresh_token
                WHERE user_id = ?
                """;

        jdbcTemplate.update(sql, userId);
    }

    /**
     * Refresh Token 문자열을 기준으로 DB에서 삭제
     * 클라이언트가 보낸 Refresh Token을 기준으로
     * 해당 토큰을 로그아웃 처리할 때 사용함
     * 즉, 로그아웃 API에서 토큰을 보낼 때 토큰을 기준으로 삭제하는 것
     */
    @Override
    public void deleteByToken(String token) {
        String sql = """
                DELETE FROM refresh_token
                WHERE token = ?
                """;

        jdbcTemplate.update(sql, token);
    }

    /**
     * refresh_token 테이블 조회 결과를 RefreshToken 객체로 변환하는 RowMapper
     * ResultSet에서 컬럼 값을 꺼내 RefreshToken 객체를 직접 생성
     */
    private org.springframework.jdbc.core.RowMapper<RefreshToken> refreshTokenRowMapper() {
        return (rs, rowNum) -> new RefreshToken(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("token"),
                rs.getTimestamp("expired_at").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}