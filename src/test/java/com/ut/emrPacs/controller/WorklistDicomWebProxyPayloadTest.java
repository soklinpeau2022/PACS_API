package com.ut.emrPacs.controller;

import com.ut.emrPacs.config.ResponseBodySanitizerAdvice;
import com.ut.emrPacs.config.RawByteArrayMessageConverterConfig;
import com.ut.emrPacs.service.service.WorklistService;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorklistDicomWebProxyPayloadTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        byte[] dicomJson = "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3\"]}}]"
                .getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/dicom+json"));
        headers.setContentLength(dicomJson.length);
        StreamingResponseBody body = outputStream -> outputStream.write(dicomJson);

        WorklistService worklistService = mock(WorklistService.class);
        when(worklistService.proxyViewerDicomWeb(any()))
                .thenReturn(new ResponseEntity<>(body, headers, 200));

        WorklistController controller = new WorklistController();
        ReflectionTestUtils.setField(controller, "worklistService", worklistService);
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        converters.add(new MappingJackson2HttpMessageConverter());
        new RawByteArrayMessageConverterConfig().extendMessageConverters(converters);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ResponseBodySanitizerAdvice())
                .setMessageConverters(converters.toArray(HttpMessageConverter[]::new))
                .build();
    }

    @Test
    void proxyShouldWriteDicomJsonResourceWithoutBase64EncodingOrTruncation() throws Exception {
        byte[] expected = "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3\"]}}]"
                .getBytes(StandardCharsets.UTF_8);

        MvcResult result = mockMvc.perform(get("/worklist/viewer-dicom-web-proxy/studies")
                        .queryParam("StudyInstanceUID", "1.2.3")
                        .accept("application/dicom+json"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/dicom+json"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, expected.length))
                .andExpect(content().bytes(expected));
    }
}
