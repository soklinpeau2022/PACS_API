package com.ut.emrPacs.config.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class FlexibleLocalDateDeserializer extends JsonDeserializer<LocalDate> {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd
    private static final DateTimeFormatter DICOM = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String raw = p.getValueAsString();
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value, ISO);
        } catch (DateTimeParseException ignored) {
            // try DICOM format below
        }
        try {
            return LocalDate.parse(value, DICOM);
        } catch (DateTimeParseException ex) {
            throw ctxt.weirdStringException(value, LocalDate.class, "Invalid date format. Use yyyy-MM-dd or yyyyMMdd.");
        }
    }
}
