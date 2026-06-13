package com.ut.emrPacs.config.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonLocalDateConfig {
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        JavaTimeModule module = new JavaTimeModule();
        module.addDeserializer(LocalDate.class, new FlexibleLocalDateDeserializer());
        module.addSerializer(LocalDate.class, new LocalDateSerializer(ISO_DATE));
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
                .modules(module)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        return new MappingJackson2HttpMessageConverter(mapper);
    }
}
