package com.ut.emrPacs.authentication.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GlobalRequestSizeLimitFilterTest {

    private static final long GLOBAL_LIMIT_BYTES = 12L * 1024L * 1024L;
    private static final long VIEWER_STATE_LIMIT_BYTES = 16L * 1024L * 1024L;
    private static final long DICOM_UPLOAD_LIMIT_BYTES = 256L * 1024L * 1024L;

    private GlobalRequestSizeLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GlobalRequestSizeLimitFilter(GLOBAL_LIMIT_BYTES, VIEWER_STATE_LIMIT_BYTES, DICOM_UPLOAD_LIMIT_BYTES, "/dicom-uploads");
    }

    @Test
    void shouldBlockRequestWhenContentLengthExceedsGlobalLimit() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/file/file-upload");
        request.setContentType("application/octet-stream");
        request.setContent(new byte[13 * 1024 * 1024]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldAllowLargeDicomUploadWithinDicomUploadLimit() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/dicom-uploads");
        request.setContextPath("/pacsApi");
        request.setContentType("multipart/form-data; boundary=----pacs");
        request.addHeader("Content-Length", Long.toString(73L * 1024L * 1024L));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldBlockDicomUploadWhenContentLengthExceedsDicomUploadLimit() throws Exception {
        filter = new GlobalRequestSizeLimitFilter(GLOBAL_LIMIT_BYTES, VIEWER_STATE_LIMIT_BYTES, 16, "/dicom-uploads");
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/dicom-uploads");
        request.setContextPath("/pacsApi");
        request.setContentType("multipart/form-data; boundary=----pacs");
        request.setContent("01234567890123456".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldApplyDicomUploadLimitToNestedUploadPaths() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/dicom-uploads/retry");
        request.setContextPath("/pacsApi");
        request.setContentType("multipart/form-data; boundary=----pacs");
        request.addHeader("Content-Length", Long.toString(73L * 1024L * 1024L));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldAllowViewerStateSaveAboveGlobalLimit() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/pacs-result/pacs-result-viewer-state-save");
        request.setContextPath("/pacsApi");
        request.setContentType("application/json");
        // Above the 12 MB generic cap but within the 16 MB viewer-state cap.
        request.setContent(new byte[13 * 1024 * 1024]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldAllowViewerStateSaveViaApiPathAboveGlobalLimit() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/pacs-result-api/pacs-result-viewer-state-save-chunk");
        request.setContextPath("/pacsApi");
        request.setContentType("application/json");
        request.setContent(new byte[13 * 1024 * 1024]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldBlockViewerStateSaveAboveViewerStateLimit() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/pacs-result/pacs-result-viewer-state-save");
        request.setContextPath("/pacsApi");
        request.setContentType("application/json");
        // Above the 16 MB viewer-state cap.
        request.setContent(new byte[17 * 1024 * 1024]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldApplyGlobalLimitToNonViewerStatePacsResultPath() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pacsApi/pacs-result/pacs-result-create");
        request.setContextPath("/pacsApi");
        request.setContentType("application/json");
        // 13 MB on a non viewer-state path stays bound by the 12 MB generic cap.
        request.setContent(new byte[13 * 1024 * 1024]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldAllowRequestWithinGlobalLimit() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/study/study-list");
        request.setContentType("application/json");
        request.setContent("{}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }
}
