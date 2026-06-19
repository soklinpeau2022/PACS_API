package com.ut.emrPacs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class RawByteArrayMessageConverterConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.removeIf(ByteArrayHttpMessageConverter.class::isInstance);
        converters.add(0, new ByteArrayHttpMessageConverter());
    }
}
