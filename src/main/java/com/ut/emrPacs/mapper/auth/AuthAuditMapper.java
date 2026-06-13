package com.ut.emrPacs.mapper.auth;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis mapper interface for namespace {@code com.ut.emrPacs.mapper.auth.AuthAuditMapper}.
 * Each method maps to an XML statement with the same {@code id}.
 */
@Mapper
public interface AuthAuditMapper {

    /**
     * MyBatis statement id: {@code countExpiredUser}.
     */
    Long countExpiredUser(@Param("userId") Long userId);

    /**
     * MyBatis statement id: {@code updateUserLoginAudit}.
     */
    void updateUserLoginAudit(
            @Param("userId") Long userId,
            @Param("remoteIp") String remoteIp,
            @Param("httpUserAgent") String httpUserAgent
    );

    /**
     * MyBatis statement id: {@code clearUserLoginAudit}.
     */
    void clearUserLoginAudit(@Param("userId") Long userId);

    /**
     * MyBatis statement id: {@code insertUserLog}.
     */
    void insertUserLog(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("httpUserAgent") String httpUserAgent,
            @Param("remoteAddr") String remoteAddr
    );
}
