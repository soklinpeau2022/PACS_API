package com.ut.emrPacs.mapper.auth;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ut.emrPacs.model.auth.RefreshTokenRow;

@Mapper
public interface RefreshTokenMapper {

    Long insert(RefreshTokenRow row);

    RefreshTokenRow findActiveByTokenHash(@Param("tokenHash") String tokenHash);

    void revokeById(@Param("id") Long id, @Param("reason") String reason);

    void revokeExpired(@Param("now") LocalDateTime now);

    /**
     * Finds any refresh token row by hash regardless of revocation status.
     * Used for reuse-detection: if a token is found but already revoked, it was reused.
     */
    RefreshTokenRow findByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Revokes all active refresh tokens for the given user+client combination.
     * Called on refresh token reuse detection to invalidate the entire token family.
     */
    void revokeAllByUserIdAndClientId(
            @Param("userId") Long userId,
            @Param("clientId") String clientId,
            @Param("reason") String reason
    );
}
