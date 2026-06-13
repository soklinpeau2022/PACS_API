package com.ut.emrPacs.authentication.principal;

public record CurrentUserPrincipal(
        Long userId,
        String username,
        Long hospitalId,
        String hospitalCode,
        String clientId,
        String jti,
        Long permissionVersion
) {
}
