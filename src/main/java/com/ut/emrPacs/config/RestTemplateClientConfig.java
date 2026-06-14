package com.ut.emrPacs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class RestTemplateClientConfig {

    @Bean
    public RestTemplate restTemplate(
            @Value("${pacs.dicom-server.client.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${pacs.dicom-server.client.read-timeout-ms:1800000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
