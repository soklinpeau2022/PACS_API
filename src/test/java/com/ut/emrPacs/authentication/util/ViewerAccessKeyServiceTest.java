package com.ut.emrPacs.authentication.util;

import com.ut.emrPacs.model.dto.response.authentication.token.AccessTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViewerAccessKeyServiceTest {

    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private JwtDecoder jwtDecoder;

    private ViewerAccessKeyService service;

    @BeforeEach
    void setUp() {
        service = new ViewerAccessKeyService();
        ReflectionTestUtils.setField(service, "jwtTokenService", jwtTokenService);
        ReflectionTestUtils.setField(service, "jwtDecoder", jwtDecoder);
        ReflectionTestUtils.setField(service, "editLifetimeMs", 86_400_000L);
        ReflectionTestUtils.setField(service, "readLifetimeMs", 86_400_000L);
        ReflectionTestUtils.setField(service, "publicLifetimeMs", 86_400_000L);
    }

    @Test
    void issuesAllViewerAccessModesWithOneDayLifetime() {
        when(jwtTokenService.issueViewerApiKey(
                eq(11L), eq(22L), eq(33L), eq(44L), eq("1.2.3"),
                isNull(), isNull(), eq(ViewerAccessKeyService.ACCESS_EDIT), eq(86_400_000L)
        )).thenReturn(token("edit-token"));
        when(jwtTokenService.issueViewerApiKey(
                eq(11L), eq(22L), eq(33L), eq(44L), eq("1.2.3"),
                isNull(), isNull(), eq(ViewerAccessKeyService.ACCESS_READ), eq(86_400_000L)
        )).thenReturn(token("read-token"));
        when(jwtTokenService.issueViewerApiKey(
                eq(11L), eq(22L), eq(33L), eq(44L), eq("1.2.3"),
                isNull(), isNull(), eq(ViewerAccessKeyService.ACCESS_PUBLIC), eq(86_400_000L)
        )).thenReturn(token("public-token"));

        assertEquals("edit-token", issue(ViewerAccessKeyService.ACCESS_EDIT));
        assertEquals("read-token", issue(ViewerAccessKeyService.ACCESS_READ));
        assertEquals("public-token", issue(ViewerAccessKeyService.ACCESS_PUBLIC));

        verify(jwtTokenService).issueViewerApiKey(
                11L, 22L, 33L, 44L, "1.2.3",
                null, null, ViewerAccessKeyService.ACCESS_EDIT, 86_400_000L
        );
        verify(jwtTokenService).issueViewerApiKey(
                11L, 22L, 33L, 44L, "1.2.3",
                null, null, ViewerAccessKeyService.ACCESS_READ, 86_400_000L
        );
        verify(jwtTokenService).issueViewerApiKey(
                11L, 22L, 33L, 44L, "1.2.3",
                null, null, ViewerAccessKeyService.ACCESS_PUBLIC, 86_400_000L
        );
    }

    @Test
    void scopeMatchingRejectsMissingOrDifferentResourceBindings() {
        ViewerAccessKeyService.ViewerAccessClaims scopedClaims =
                new ViewerAccessKeyService.ViewerAccessClaims(
                        11L,
                        22L,
                        33L,
                        44L,
                        "1.2.3",
                        null,
                        null,
                        ViewerAccessKeyService.ACCESS_PUBLIC
                );
        ViewerAccessKeyService.ViewerAccessClaims missingBindings =
                new ViewerAccessKeyService.ViewerAccessClaims(
                        11L,
                        null,
                        null,
                        null,
                        "1.2.3",
                        null,
                        null,
                        ViewerAccessKeyService.ACCESS_PUBLIC
                );

        assertTrue(ViewerAccessKeyService.matchesScope(
                scopedClaims, 11L, 22L, 33L, 44L, "1.2.3"
        ));
        assertFalse(ViewerAccessKeyService.matchesScope(
                missingBindings, 11L, 22L, 33L, 44L, "1.2.3"
        ));
        assertFalse(ViewerAccessKeyService.matchesScope(
                scopedClaims, 11L, 99L, 33L, 44L, "1.2.3"
        ));
    }

    private String issue(String accessMode) {
        return service.issue(11L, 22L, 33L, 44L, "1.2.3", null, null, accessMode);
    }

    private static AccessTokenResponse token(String value) {
        return new AccessTokenResponse(
                "Bearer",
                value,
                null,
                86_400L,
                ViewerAccessKeyService.SCOPE
        );
    }
}
