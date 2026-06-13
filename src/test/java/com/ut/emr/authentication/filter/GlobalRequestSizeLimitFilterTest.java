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

    private GlobalRequestSizeLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GlobalRequestSizeLimitFilter();
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
