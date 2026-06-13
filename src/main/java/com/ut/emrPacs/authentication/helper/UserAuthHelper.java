package com.ut.emrPacs.authentication.helper;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for reading the current Spring Security authentication context.
 *
 * <p>Use {@link com.ut.emrPacs.authentication.session.UserAuthSession} when you need the
 * full {@code CurrentUserPrincipal} (e.g. userId, roles). Use this class only when a raw
 * {@link Authentication} or a simple authenticated/anonymous check is sufficient.
 */
public final class UserAuthHelper {

    private static final Logger logger = LoggerFactory.getLogger(UserAuthHelper.class);

    private UserAuthHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns the raw {@link Authentication} from the security context, or {@code null} if absent.
     */
    public static Authentication getUserAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            logger.debug("No authentication found in security context.");
        } else {
            logger.debug("Authentication retrieved.");
        }
        return auth;
    }

    /**
     * Returns {@code true} when the current request carries a non-anonymous, authenticated principal.
     */
    public static boolean isAuthenticated() {
        Authentication authentication = getUserAuth();
        if (authentication != null && authentication.isAuthenticated()) {
            boolean isAnonymous = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ANONYMOUS".equals(a.getAuthority()));
            logger.debug("Authentication check performed, is anonymous: {}", isAnonymous);
            return !isAnonymous;
        }
        logger.debug("User is not authenticated.");
        return false;
    }

}
