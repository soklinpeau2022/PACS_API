package com.ut.emrPacs.controller;

import com.ut.emrPacs.mapper.pacs.PatientMapper;
import com.ut.emrPacs.model.dto.request.pacs.patient.PatientCreateRequest;
import com.ut.emrPacs.model.dto.request.pacs.patient.PatientUpdateRequest;
import com.ut.emrPacs.model.dto.response.pacs.patient.PatientResponse;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientCrudNameContractTest {

    @Test
    void patientCrudRequestsShouldUseFirstAndLastNameOnly() throws Exception {
        assertHasField(PatientCreateRequest.class, "firstName");
        assertHasField(PatientCreateRequest.class, "lastName");
        assertHasAnnotation(PatientCreateRequest.class, "firstName", "NotBlank");
        assertNoField(PatientCreateRequest.class, "patientName");

        assertHasField(PatientUpdateRequest.class, "firstName");
        assertHasField(PatientUpdateRequest.class, "lastName");
        assertNoField(PatientUpdateRequest.class, "patientName");
    }

    @Test
    void patientCrudResponseShouldUseFirstAndLastNameOnly() {
        assertHasField(PatientResponse.class, "firstName");
        assertHasField(PatientResponse.class, "lastName");
        assertNoField(PatientResponse.class, "patientName");
    }

    @Test
    void patientCreateShouldReturnCreatedPatientIdForWorklistHandoff() throws Exception {
        Method create = PatientMapper.class.getDeclaredMethod("create", Long.class, PatientCreateRequest.class);
        assertEquals(Long.class, create.getReturnType(), "Patient create must return the created database id.");

        InputStream stream = PatientCrudNameContractTest.class.getClassLoader()
                .getResourceAsStream("mybatis/postgresql/PatientMapper.xml");
        assertTrue(stream != null, "PatientMapper.xml must be available to audit create SQL.");
        String mapperXml;
        try (stream) {
            mapperXml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(mapperXml.contains("<select id=\"create\" resultType=\"java.lang.Long\">"),
                "Patient create SQL must be mapped as a returning select.");
        assertTrue(mapperXml.contains("RETURNING id"), "Patient create SQL must return the inserted patient id.");
    }

    private static void assertHasField(Class<?> type, String fieldName) {
        boolean exists = Arrays.stream(type.getDeclaredFields()).anyMatch(field -> fieldName.equals(field.getName()));
        assertTrue(exists, () -> type.getSimpleName() + " must declare field: " + fieldName);
    }

    private static void assertNoField(Class<?> type, String fieldName) {
        boolean exists = Arrays.stream(type.getDeclaredFields()).anyMatch(field -> fieldName.equals(field.getName()));
        assertTrue(!exists, () -> type.getSimpleName() + " must not declare field: " + fieldName);
    }

    private static void assertHasAnnotation(Class<?> type, String fieldName, String annotationSimpleName) throws Exception {
        boolean present = Arrays.stream(type.getDeclaredField(fieldName).getAnnotations())
                .anyMatch(annotation -> annotation.annotationType().getSimpleName().equals(annotationSimpleName));
        assertTrue(present, () -> type.getSimpleName() + "." + fieldName + " must have @" + annotationSimpleName);
    }
}
