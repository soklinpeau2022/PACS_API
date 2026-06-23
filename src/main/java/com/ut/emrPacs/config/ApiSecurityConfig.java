package com.ut.emrPacs.config;

import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ut.emrPacs.authentication.filter.ActiveHospitalFilter;
import com.ut.emrPacs.authentication.filter.GlobalRequestSizeLimitFilter;
import com.ut.emrPacs.authentication.filter.ModulePermissionFilter;
import com.ut.emrPacs.authentication.filter.RevokedTokenFilter;
import com.ut.emrPacs.authentication.filter.SecurityRateLimitFilter;
import com.ut.emrPacs.authentication.filter.SecurityThreatDetectionFilter;
import com.ut.emrPacs.authentication.util.AuthorityUtils;
import com.ut.emrPacs.authentication.util.HeaderBearerTokenResolver;
import com.ut.emrPacs.authentication.util.PacsJwtAuthenticationConverter;
import com.ut.emrPacs.authentication.util.RsaKeyLoader;
import com.ut.emrPacs.mapper.user.UserMapper;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.users.CustomUserDetails;
import com.ut.emrPacs.model.users.User;
import com.ut.emrPacs.model.users.UserGroupList;
import com.ut.emrPacs.service.service.AuthService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class ApiSecurityConfig {

    private static final List<String> DEFAULT_ALLOWED_METHODS = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD");
    private static final List<String> DEFAULT_ALLOWED_HEADERS = List.of(
            "Origin",
            "Content-Type",
            "Accept",
            "X-Requested-With",
            "Authorization",
            "X-PACS-VIEWER-ACCESS",
            "X-PACS-VIEWER-API-KEY",
            "X-PACS-RESULT-API-KEY",
            "X-API-KEY",
            "Range",
            "If-None-Match",
            "If-Modified-Since",
            "Cache-Control",
            "Pragma"
    );
    private static final List<String> DEFAULT_EXPOSED_HEADERS = List.of(
            "Authorization",
            "Content-Type",
            "Content-Length",
            "Content-Range",
            "Accept-Ranges",
            "ETag",
            "Last-Modified",
            "Content-Disposition"
    );

    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            ApiConstants.Auth.LOGIN_FULL_PATH,
            ApiConstants.Auth.LOGOUT_FULL_PATH,
            ApiConstants.Auth.REFRESH_FULL_PATH,
            ApiConstants.Auth.CLIENT_CREDENTIALS_FULL_PATH
    };

    private static final String[] PUBLIC_INFRA_ENDPOINTS = {
            "/error",
            "/actuator/health",
            "/actuator/info",
            ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.RECEIVED_STUDY_PATH,
            ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEWER_DICOMWEB_AUTHORIZE_PATH,
            ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEWER_DICOMWEB_PROXY_AUTHORIZE_PATH,
            ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEWER_DICOMWEB_DECODE_PATH,
            ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEWER_DICOMWEB_PROFILE_PATH,
            ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEWER_DICOMWEB_RENEW_PATH,
            ApiConstants.Worklist.BASE_PATH + ApiConstants.Worklist.VIEWER_DICOMWEB_REVOKE_PATH,
            ApiConstants.Worklist.BASE_PATH + "/viewer-dicom-web/**",
            ApiConstants.Worklist.BASE_PATH + "/viewer-dicom-web-proxy/**",
            ApiConstants.PacsResult.BASE_PATH + "/**",
            ApiConstants.PacsResultApi.BASE_PATH + "/**"
    };

    private static final String[] SWAGGER_ENDPOINTS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/api-docs/**",
            "/v3/api-docs/**"
    };

    private final AuthService authService;
    private final UserMapper userMapper;
    private final GlobalRequestSizeLimitFilter globalRequestSizeLimitFilter;
    private final SecurityThreatDetectionFilter securityThreatDetectionFilter;
    private final SecurityRateLimitFilter securityRateLimitFilter;
    private final RevokedTokenFilter revokedTokenFilter;
    private final ActiveHospitalFilter activeHospitalFilter;
    private final ModulePermissionFilter modulePermissionFilter;
    private final RsaKeyLoader rsaKeyLoader;
    private final PacsJwtAuthenticationConverter pacsJwtAuthenticationConverter;
    private final HeaderBearerTokenResolver headerBearerTokenResolver;

    @Value("${app.security.swagger-public:false}")
    private boolean swaggerPublic;

    @Value("${app.security.block-patch:true}")
    private boolean blockPatchMethod;

    @Value("${app.security.block-trace:true}")
    private boolean blockTraceMethod;

    @Value("${app.security.require-https:false}")
    private boolean requireHttps;

    @Value("${app.security.admin.authorities:ROLE_ADMIN,ROLE_SUPER_ADMIN}")
    private String adminAuthoritiesCsv;

    @Value("${cors.allowedOrigins:}")
    private String[] corsAllowedOrigins;

    @Value("${cors.allowCredentials:false}")
    private boolean corsAllowCredentials;

    @Value("${cors.allowedMethods:}")
    private String[] corsAllowedMethods;

    @Value("${cors.allowedHeaders:}")
    private String[] corsAllowedHeaders;

    @Value("${cors.exposedHeaders:}")
    private String[] corsExposedHeaders;

    @Value("${cors.maxAgeSeconds:3600}")
    private long corsMaxAgeSeconds;

    @Value("${security.jwt.issuer:udaya_pacs_api}")
    private String jwtIssuer;

    @Value("${security.jwt.audience:pacs-web}")
    private String jwtAudience;

    @Autowired
    public ApiSecurityConfig(
            @Lazy AuthService authService,
            UserMapper userMapper,
            GlobalRequestSizeLimitFilter globalRequestSizeLimitFilter,
            SecurityThreatDetectionFilter securityThreatDetectionFilter,
            SecurityRateLimitFilter securityRateLimitFilter,
            RevokedTokenFilter revokedTokenFilter,
            ActiveHospitalFilter activeHospitalFilter,
            ModulePermissionFilter modulePermissionFilter,
            RsaKeyLoader rsaKeyLoader,
            PacsJwtAuthenticationConverter pacsJwtAuthenticationConverter,
            HeaderBearerTokenResolver headerBearerTokenResolver
    ) {
        this.authService = authService;
        this.userMapper = userMapper;
        this.globalRequestSizeLimitFilter = globalRequestSizeLimitFilter;
        this.securityThreatDetectionFilter = securityThreatDetectionFilter;
        this.securityRateLimitFilter = securityRateLimitFilter;
        this.revokedTokenFilter = revokedTokenFilter;
        this.activeHospitalFilter = activeHospitalFilter;
        this.modulePermissionFilter = modulePermissionFilter;
        this.rsaKeyLoader = rsaKeyLoader;
        this.pacsJwtAuthenticationConverter = pacsJwtAuthenticationConverter;
        this.headerBearerTokenResolver = headerBearerTokenResolver;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKeyLoader.getPublicKey()).build();

        // Validators: issuer + expiry (via createDefaultWithIssuer), audience, tokenUse, alg, jti, principalType
        var issuerValidator = JwtValidators.createDefaultWithIssuer(jwtIssuer);
        var audienceValidator = (org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt>) token -> {
            List<String> aud = token.getAudience();
            if (aud != null && aud.contains(jwtAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required audience missing", null));
        };
        var tokenUseValidator = (org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt>) token -> {
            String tokenUse = token.getClaimAsString("tokenUse");
            if ("access".equals(tokenUse)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Token is not an access token", null));
        };
        var algorithmValidator = (org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt>) token -> {
            Object alg = token.getHeaders().get("alg");
            if ("RS256".equals(alg)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Unexpected JWT algorithm", null));
        };
        var jtiValidator = (org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt>) token -> {
            String jti = token.getId();
            if (jti != null && !jti.isBlank()) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Missing token ID", null));
        };
        var principalTypeValidator = (org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt>) token -> {
            String principalType = token.getClaimAsString("principalType");
            if ("USER".equals(principalType) || "CLIENT".equals(principalType)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid principal type", null));
        };
        var clientIdValidator = (org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt>) token -> {
            String clientId = token.getClaimAsString("clientId");
            if (clientId != null && !clientId.isBlank()) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Missing clientId", null));
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator,
                tokenUseValidator,
                algorithmValidator,
                jtiValidator,
                principalTypeValidator,
                clientIdValidator
        ));
        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        final String[] adminAuthorities = AuthorityUtils.parseCsvAuthorities(adminAuthoritiesCsv, "ROLE_ADMIN");

        if (requireHttps) {
            http.redirectToHttps(Customizer.withDefaults());
        }

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    if (blockPatchMethod) {
                        auth.requestMatchers(HttpMethod.PATCH, "/**").denyAll();
                    }

                    if (blockTraceMethod) {
                        auth.requestMatchers(HttpMethod.TRACE, "/**").denyAll();
                    }

                    auth.requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll();
                    auth.requestMatchers(PUBLIC_INFRA_ENDPOINTS).permitAll();

                    if (swaggerPublic) {
                        auth.requestMatchers(SWAGGER_ENDPOINTS).permitAll();
                    } else {
                        auth.requestMatchers(SWAGGER_ENDPOINTS).authenticated();
                    }

                    auth.requestMatchers(
                            ApiConstants.User.BASE_PATH + ApiConstants.User.LIST_PATH,
                            ApiConstants.User.BASE_PATH + ApiConstants.User.CREATE_PATH,
                            ApiConstants.User.BASE_PATH + ApiConstants.User.UPDATE_PATH,
                            ApiConstants.User.BASE_PATH + ApiConstants.User.CHANGE_PASSWORD_PATH,
                            ApiConstants.User.BASE_PATH + "/user-find/**",
                            ApiConstants.User.BASE_PATH + "/user-delete/**",
                            "/user-right/**"
                    ).hasAnyAuthority(adminAuthorities);

                    auth.requestMatchers(ApiConstants.SystemActivity.BASE_PATH + "/**").authenticated();
                    auth.requestMatchers(ApiConstants.Notification.BASE_PATH + "/**").authenticated();

                    auth.requestMatchers(ApiConstants.Role.MENU_FULL_PATH).authenticated();
                    auth.requestMatchers(ApiConstants.Role.BASE_PATH + "/**").hasAnyAuthority(adminAuthorities);

                    auth.anyRequest().authenticated();
                })
                // Resource server: validates Bearer JWT using RS256 public key
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(headerBearerTokenResolver)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(pacsJwtAuthenticationConverter)
                        )
                        // Invalid/expired JWT → 401
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Invalid or expired token"))
                )
                .exceptionHandling(exception -> exception
                        // Missing token or other auth failures → 401
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Forbidden"))
                )
                .headers(headers -> headers
                        // StreamingResponseBody begins writing on an async worker while the servlet
                        // filter chain is unwinding. Write security headers first so the worker and
                        // HeaderWriterFilter never mutate Tomcat's response headers concurrently.
                        .withObjectPostProcessor(new ObjectPostProcessor<HeaderWriterFilter>() {
                            @Override
                            public <O extends HeaderWriterFilter> O postProcess(O filter) {
                                filter.setShouldWriteHeadersEagerly(true);
                                return filter;
                            }
                        })
                        .contentTypeOptions(Customizer.withDefaults())
                        .cacheControl(Customizer.withDefaults())
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new StaticHeadersWriter("X-DNS-Prefetch-Control", "off"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin"))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=(), usb=()"))
                        .addHeaderWriter(new StaticHeadersWriter("X-Permitted-Cross-Domain-Policies", "none"))
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Filter order: CorsFilter → GlobalRequestSizeLimit → ThreatDetection → RateLimit
                //               → BearerTokenAuthenticationFilter (added by oauth2ResourceServer)
                //               → RevokedTokenFilter → ModulePermissionFilter
                .addFilterAfter(globalRequestSizeLimitFilter, CorsFilter.class)
                .addFilterBefore(new OncePerRequestFilter() {
                    @Serial
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void doFilterInternal(
                            HttpServletRequest request,
                            HttpServletResponse response,
                            FilterChain filterChain
                    ) throws ServletException, IOException {
                        if (corsAllowCredentials && request.getHeader("Origin") != null) {
                            response.setHeader("Access-Control-Allow-Credentials", "true");
                        }
                        filterChain.doFilter(request, response);
                    }
                }, CorsFilter.class)
                .addFilterAfter(securityThreatDetectionFilter, GlobalRequestSizeLimitFilter.class)
                .addFilterAfter(securityRateLimitFilter, SecurityThreatDetectionFilter.class)
                .addFilterAfter(revokedTokenFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(activeHospitalFilter, RevokedTokenFilter.class)
                .addFilterAfter(modulePermissionFilter, ActiveHospitalFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        Set<String> origins = parseCsvValues(corsAllowedOrigins);
        boolean useWildcardPattern = origins.remove("*");

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(corsAllowCredentials);

        if (useWildcardPattern && corsAllowCredentials) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else if (useWildcardPattern) {
            config.setAllowedOrigins(List.of("*"));
        } else if (!origins.isEmpty()) {
            config.setAllowedOrigins(new ArrayList<>(origins));
        }

        List<String> allowedMethods = normalizeMethods(corsAllowedMethods, DEFAULT_ALLOWED_METHODS);
        config.setAllowedMethods(allowedMethods);

        List<String> allowedHeaders = normalizeHeaders(corsAllowedHeaders, DEFAULT_ALLOWED_HEADERS);
        config.setAllowedHeaders(allowedHeaders);

        List<String> exposedHeaders = normalizeHeaders(corsExposedHeaders, DEFAULT_EXPOSED_HEADERS);
        if (!exposedHeaders.isEmpty()) {
            config.setExposedHeaders(exposedHeaders);
        }

        long maxAge = corsMaxAgeSeconds < 0 ? 0 : corsMaxAgeSeconds;
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public HttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowBackSlash(false);
        firewall.setAllowSemicolon(false);
        firewall.setAllowUrlEncodedSlash(false);
        firewall.setAllowUrlEncodedDoubleSlash(false);
        firewall.setAllowUrlEncodedPeriod(false);
        return firewall;
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer(HttpFirewall httpFirewall) {
        return web -> web.httpFirewall(httpFirewall);
    }

    private static void writeJsonError(HttpServletResponse response, int statusCode, String errorCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String json = new ObjectMapper().writeValueAsString(
                ResponseMessageUtils.makeResponse(false, statusCode, errorCode, message)
        );
        response.getWriter().write(json);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return identifier -> {
            User user = authService.findUserByUsername(identifier);
            if (user == null) {
                user = authService.findUserByEmail(identifier);
            }
            if (user == null) {
                throw new UsernameNotFoundException("User not found for: " + identifier);
            }

            Set<String> roles = new LinkedHashSet<>();
            roles.add("ROLE_USER");

            List<UserGroupList> groups = userMapper.getOneUserGroupList(user.getId());
            if (groups != null) {
                for (UserGroupList group : groups) {
                    String normalized = AuthorityUtils.normalizeRole(group != null ? group.getName() : null);
                    if (normalized != null) {
                        roles.add(normalized);
                    }
                }
            }

            return new CustomUserDetails(
                    user.getId(),
                    user.getUsername(),
                    user.getPassword(),
                    roles.stream().map(SimpleGrantedAuthority::new).toList()
            );
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    private static Set<String> parseCsvValues(String[] rawValues) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (rawValues == null || rawValues.length == 0) {
            return values;
        }
        for (String raw : rawValues) {
            if (raw == null) {
                continue;
            }
            String[] split = raw.split(",");
            for (String token : split) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
        }
        return values;
    }

    private static List<String> normalizeMethods(String[] rawMethods, List<String> defaults) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        for (String method : parseCsvValues(rawMethods)) {
            resolved.add(method.toUpperCase(Locale.ROOT));
        }
        if (resolved.isEmpty()) {
            return defaults;
        }
        if (!resolved.contains("OPTIONS")) {
            resolved.add("OPTIONS");
        }
        return Collections.unmodifiableList(new ArrayList<>(resolved));
    }

    private static List<String> normalizeHeaders(String[] rawHeaders, List<String> defaults) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>(parseCsvValues(rawHeaders));
        if (resolved.isEmpty()) {
            return defaults;
        }
        return Collections.unmodifiableList(new ArrayList<>(resolved));
    }

}
