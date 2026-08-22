package com.hotelapp.auth;

import com.hotelapp.core.entity.RefreshTokensEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokensEntity, UUID> {

    interface RefreshSessionView {
        Long getUserId();

        String getSessionId();
    }

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT user_id, id::text AS session_id
            FROM refresh_tokens
            WHERE token_hash = :tokenHash AND expires_at > CURRENT_TIMESTAMP
              AND revoked_at IS NULL AND is_revoked = false
            """, nativeQuery = true)
    Optional<RefreshSessionView> findActiveSession(@Param("tokenHash") String tokenHash);

    @Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            INSERT INTO refresh_tokens (id, user_id, token_hash, ip_address, user_agent, expires_at, created_at)
            VALUES (CAST(:id AS uuid), :userId, :tokenHash, CAST(:ipAddress AS inet), :userAgent, :expiresAt, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    void insertSession(@Param("id") UUID id, @Param("userId") long userId,
            @Param("tokenHash") String tokenHash, @Param("ipAddress") String ipAddress,
            @Param("userAgent") String userAgent, @Param("expiresAt") OffsetDateTime expiresAt);

    @Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            UPDATE refresh_tokens
            SET token_hash = :nextHash, expires_at = :expiresAt, last_used_at = CURRENT_TIMESTAMP
            WHERE id = CAST(:sessionId AS uuid) AND token_hash = :previousHash
              AND revoked_at IS NULL AND is_revoked = false
            """, nativeQuery = true)
    int rotateSession(@Param("sessionId") String sessionId,
            @Param("previousHash") String previousHash, @Param("nextHash") String nextHash,
            @Param("expiresAt") OffsetDateTime expiresAt);

    @Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            UPDATE refresh_tokens
            SET is_revoked = true, revoked_at = CURRENT_TIMESTAMP
            WHERE token_hash = :tokenHash
            """, nativeQuery = true)
    int revokeByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            UPDATE refresh_tokens
            SET is_revoked = true, revoked_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId AND revoked_at IS NULL
            """, nativeQuery = true)
    int revokeAllForUser(@Param("userId") long userId);
}
