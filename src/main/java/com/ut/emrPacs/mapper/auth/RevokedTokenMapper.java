package com.ut.emrPacs.mapper.auth;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * Tracks revoked JWTs (by jti) so logged-out tokens cannot be used until they expire.
 */
@Mapper
public interface RevokedTokenMapper {

    /**
     * MyBatis statement id: {@code countByJti}.
     */
    Long countByJti(@Param("jti") String jti);

    /**
     * MyBatis statement id: {@code revokeToken}.
     */
    void revokeToken(
            @Param("jti") String jti,
            @Param("userId") Long userId,
            @Param("expiresAt") LocalDateTime expiresAt
    );

    /**
     * Deletes revoked token entries that have already passed their expiry date.
     * Safe to call periodically to keep the table small.
     */
    void deleteExpired(@Param("now") LocalDateTime now);

}
