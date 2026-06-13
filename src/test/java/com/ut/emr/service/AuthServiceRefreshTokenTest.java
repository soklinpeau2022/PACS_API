package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.util.JwtTokenService;
import com.ut.emrPacs.authentication.util.LoginAttemptTracker;
import com.ut.emrPacs.authentication.util.RefreshTokenCryptoService;
import com.ut.emrPacs.authentication.util.RefreshTokenService;
import com.ut.emrPacs.mapper.auth.AuthAuditMapper;
import com.ut.emrPacs.mapper.auth.AuthMapper;
import com.ut.emrPacs.mapper.auth.OAuth2ClientMapper;
import com.ut.emrPacs.mapper.auth.RevokedTokenMapper;
import com.ut.emrPacs.mapper.user.UserMapper;
import com.ut.emrPacs.model.auth.OAuth2ClientRow;
import com.ut.emrPacs.model.auth.RefreshTokenRow;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.authentication.token.RefreshTokenRequest;
import com.ut.emrPacs.model.dto.response.authentication.token.AccessTokenResponse;
import com.ut.emrPacs.model.users.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTokenTest {

    @Mock
    private AuthMapper authMapper;
    @Mock
    private AuthAuditMapper authAuditMapper;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private OAuth2ClientMapper oauth2ClientMapper;
    @Mock
    private RevokedTokenMapper revokedTokenMapper;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserMapper userMapper;
    @Mock
    private com.ut.emrPacs.service.service.ActivityLogService activityLogService;
    @Mock
    private RefreshTokenCryptoService refreshTokenCryptoService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private LoginAttemptTracker loginAttemptTracker;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl();

        ReflectionTestUtils.setField(authService, "authMapper", authMapper);
        ReflectionTestUtils.setField(authService, "authAuditMapper", authAuditMapper);
        ReflectionTestUtils.setField(authService, "jwtTokenService", jwtTokenService);
        ReflectionTestUtils.setField(authService, "oauth2ClientMapper", oauth2ClientMapper);
        ReflectionTestUtils.setField(authService, "revokedTokenMapper", revokedTokenMapper);
        ReflectionTestUtils.setField(authService, "authenticationManager", authenticationManager);
        ReflectionTestUtils.setField(authService, "messageService", new MessageService());
        ReflectionTestUtils.setField(authService, "userMapper", userMapper);
        ReflectionTestUtils.setField(authService, "activityLogService", activityLogService);
        ReflectionTestUtils.setField(authService, "refreshTokenCryptoService", refreshTokenCryptoService);
        ReflectionTestUtils.setField(authService, "refreshTokenService", refreshTokenService);
        ReflectionTestUtils.setField(authService, "loginAttemptTracker", loginAttemptTracker);
        ReflectionTestUtils.setField(authService, "passwordEncoder", passwordEncoder);

        ReflectionTestUtils.setField(authService, "refreshTokenExpirationMs", 2_592_000_000L);
        ReflectionTestUtils.setField(authService, "allowRefreshTokenInBody", false);
        ReflectionTestUtils.setField(authService, "returnRefreshTokenInBody", false);
        ReflectionTestUtils.setField(authService, "authCookieSameSite", "Lax");
        ReflectionTestUtils.setField(authService, "authCookieSecure", false);
    }

    @Test
    void refreshShouldReturnUnauthorizedWhenRefreshTokenMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseMessage<BaseResult> result = authService.refreshAccessToken(null, request, response);

        assertFalse(result.getHeader().getResult());
        assertEquals(401, result.getHeader().getStatusCode());
        List<String> setCookies = response.getHeaders("Set-Cookie");
        assertTrue(setCookies.stream().anyMatch(value -> value.startsWith("accessToken=")));
        assertTrue(setCookies.stream().anyMatch(value -> value.startsWith("refreshToken=")));
    }

    @Test
    void refreshShouldReturnUnauthorizedWhenRefreshTokenInvalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", "invalid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(refreshTokenCryptoService.decrypt("invalid-token")).thenReturn(null);
        when(refreshTokenService.validate("invalid-token")).thenReturn(null);

        ResponseMessage<BaseResult> result = authService.refreshAccessToken(null, request, response);

        assertFalse(result.getHeader().getResult());
        assertEquals(401, result.getHeader().getStatusCode());
    }

    @Test
    void refreshShouldRotateRefreshTokenAndReturnNewAccessToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", "old-refresh-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        RefreshTokenRequest refreshTokenRequest = new RefreshTokenRequest();
        refreshTokenRequest.setClientId("web-client");

        RefreshTokenRow refreshTokenRow = new RefreshTokenRow();
        refreshTokenRow.setId(77L);
        refreshTokenRow.setUserId(10L);
        refreshTokenRow.setHospitalId(20L);
        refreshTokenRow.setClientId("web-client");
        refreshTokenRow.setClientName("PACS Web");
        refreshTokenRow.setExpiresAt(LocalDateTime.now().plusMinutes(30));

        OAuth2ClientRow clientRow = new OAuth2ClientRow();
        clientRow.setClientId("web-client");
        clientRow.setIsActive(true);
        clientRow.setAllowedGrantTypes("password_login,refresh_token");
        clientRow.setAllowedScopes("pacs.api");
        clientRow.setRefreshTokenLifetimeMs(1_800_000L);

        User user = new User();
        user.setId(10L);
        user.setUsername("demo.user");
        user.setIsActive(1L);

        when(refreshTokenCryptoService.decrypt("old-refresh-token")).thenReturn(null);
        when(refreshTokenService.validate("old-refresh-token")).thenReturn(refreshTokenRow);
        when(oauth2ClientMapper.findByClientId("web-client")).thenReturn(clientRow);
        when(authMapper.findAuthUserById(10L)).thenReturn(user);
        when(userMapper.getOneUserGroupList(10L)).thenReturn(Collections.emptyList());
        when(authMapper.findHospitalCodeByHospitalId(20L)).thenReturn("HSP01");
        when(authMapper.findPermissionVersionByUserId(10L)).thenReturn(2L);
        when(jwtTokenService.issueUserAccessToken(anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(new AccessTokenResponse("Bearer", "new-access-token", null, 900L, "pacs.api"));
        when(refreshTokenService.issue(eq(10L), eq(20L), eq("web-client"), eq("PACS Web"), eq(77L), anyString(), anyString(), any()))
                .thenReturn("rotated-refresh-token");

        ResponseMessage<BaseResult> result = authService.refreshAccessToken(refreshTokenRequest, request, response);

        assertTrue(result.getHeader().getResult());
        assertEquals(200, result.getHeader().getStatusCode());
        assertNotNull(result.getBody());
        assertNotNull(result.getBody().getData());
        assertEquals(1, result.getBody().getData().size());

        AccessTokenResponse tokenResponse = (AccessTokenResponse) result.getBody().getData().get(0);
        assertEquals("new-access-token", tokenResponse.getAccessToken());
        assertNull(tokenResponse.getRefreshToken(), "Refresh token must not be returned in body");
        assertNull(tokenResponse.getRefreshExpiresIn(), "Refresh expiry metadata must be hidden in refresh response");

        verify(refreshTokenService).revoke(77L, "ROTATED");
        List<String> setCookies = response.getHeaders("Set-Cookie");
        assertTrue(setCookies.stream().anyMatch(value -> value.contains("accessToken=new-access-token")));
        assertTrue(setCookies.stream().anyMatch(value -> value.contains("refreshToken=rotated-refresh-token")));
    }
}
