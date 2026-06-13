package com.ut.emrPacs.authentication.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ut.emrPacs.helper.security.SecurityAuditLogger;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class GlobalRequestSizeLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalRequestSizeLimitFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long MAX_REQUEST_BYTES = 12L * 1024L * 1024L; // 12 MB global cap

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length > MAX_REQUEST_BYTES) {
            SecurityAuditLogger.logBlocked(LOGGER, request, "payload_too_large", "content_length", Long.toString(length));
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String json = OBJECT_MAPPER.writeValueAsString(
                    ResponseMessageUtils.makeResponse(false, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request payload too large.")
            );
            response.getWriter().write(json);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
