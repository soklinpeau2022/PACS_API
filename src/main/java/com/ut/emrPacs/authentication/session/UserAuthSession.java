package com.ut.emrPacs.authentication.session;

import com.ut.emrPacs.authentication.principal.CurrentUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class UserAuthSession {

    private UserAuthSession() {
    }

    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public static CurrentUserPrincipal getCurrentUser() {
        Authentication authentication = getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication.getDetails() instanceof CurrentUserPrincipal principal) {
            return principal;
        }
        return null;
    }
}
