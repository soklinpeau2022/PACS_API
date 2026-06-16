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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class GlobalRequestSizeLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalRequestSizeLimitFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final long maxRequestBytes;
    private final long viewerStateMaxRequestBytes;
    private final long dicomUploadMaxRequestBytes;
    private final String dicomUploadPath;

    public GlobalRequestSizeLimitFilter(
            @Value("${app.security.max-request-bytes:12582912}") long maxRequestBytes,
            @Value("${app.security.viewer-state.max-request-bytes:12582912}") long viewerStateMaxRequestBytes,
            @Value("${app.security.dicom-upload.max-transport-request-bytes:4362076160}") long dicomUploadMaxRequestBytes,
            @Value("${app.security.dicom-upload.path:/dicom-uploads}") String dicomUploadPath
    ) {
        this.maxRequestBytes = maxRequestBytes;
        this.viewerStateMaxRequestBytes = viewerStateMaxRequestBytes;
        this.dicomUploadMaxRequestBytes = dicomUploadMaxRequestBytes;
        this.dicomUploadPath = normalizePath(dicomUploadPath);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        long maxBytes = resolveMaxRequestBytes(request);
        if (length > maxBytes) {
            SecurityAuditLogger.logBlocked(
                    LOGGER,
                    request,
                    "payload_too_large",
                    "content_length",
                    length + "/" + maxBytes
            );
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

    private long resolveMaxRequestBytes(HttpServletRequest request) {
        if (isDicomUploadRequest(request)) {
            return dicomUploadMaxRequestBytes;
        }
        if (isViewerStateRequest(request)) {
            return viewerStateMaxRequestBytes;
        }
        return maxRequestBytes;
    }

    // PACS-OHIF viewer-state saves (measurements, annotations, contours, sparse
    // labelmap voxels) legitimately exceed the strict generic JSON cap. Mirror
    // the carve-out and path matcher used by SecurityThreatDetectionFilter so the
    // two request-size gates stay consistent; keep both limits aligned when tuning.
    private static boolean isViewerStateRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri == null || requestUri.isBlank()) {
            return false;
        }
        return requestUri.contains("/pacs-result-api/pacs-result-viewer-state-")
                || requestUri.contains("/pacs-result/pacs-result-viewer-state-");
    }

    private boolean isDicomUploadRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri == null || requestUri.isBlank()) {
            return false;
        }
        String contextPath = request.getContextPath();
        String path = requestUri;
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        path = normalizePath(path);
        return isConfiguredUploadPath(path) || path.endsWith(dicomUploadPath);
    }

    private boolean isConfiguredUploadPath(String path) {
        return path.equals(dicomUploadPath) || path.startsWith(dicomUploadPath + "/");
    }

    private static String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "/dicom-uploads";
        }
        String normalized = value.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
