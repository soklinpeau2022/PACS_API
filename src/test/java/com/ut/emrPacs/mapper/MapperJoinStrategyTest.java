package com.ut.emrPacs.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperJoinStrategyTest {

    @Test
    void worklistProjectionShouldUseDirectOptionalRouteJoins() throws IOException {
        String xml = readMapper("WorklistMapper.xml");

        assertTrue(xml.contains("LEFT JOIN hospital_modality_server_routes selected_route"));
        assertTrue(xml.contains("LEFT JOIN hospital_dicom_routing_configs selected_cfg"));
        assertTrue(xml.contains("LEFT JOIN hospital_dicom_machines selected_machine"));
        assertFalse(xml.contains(") route_info ON TRUE"));
    }

    @Test
    void worklistVisitCodeLookupsShouldRemainEligibleForPartialIndexes() throws IOException {
        String xml = readMapper("WorklistMapper.xml");

        assertTrue(countOccurrences(xml, "BTRIM(q.visit_code) &lt;&gt; ''") >= 7);
        assertTrue(xml.contains("BTRIM(visit_code) &lt;&gt; ''"));
    }

    @Test
    void studyProjectionShouldKeepOneLinkedWorklistAndUseExistsForFilters() throws IOException {
        String xml = readMapper("StudyMapper.xml");

        assertTrue(xml.contains("INNER JOIN hospitals h ON h.id = s.hospital_id"));
        assertTrue(xml.contains("LEFT JOIN LATERAL ("));
        assertTrue(xml.contains("FROM pacs_worklist_study_links search_qsl"));
        assertTrue(xml.contains("FROM pacs_worklist_study_links modality_qsl"));
        assertFalse(xml.contains("STUDY_Worklist_FILTER_JOINS"));
    }

    @Test
    void routingConfigFiltersShouldNotMultiplySummaryRows() throws IOException {
        String xml = readMapper("DicomServerMapper.xml");

        assertTrue(xml.contains("AND EXISTS ("));
        assertTrue(xml.contains("FROM hospital_modality_server_routes route_filter"));
        assertFalse(xml.contains("ROUTING_CONFIG_FILTER_JOINS"));
    }

    private static String readMapper(String fileName) throws IOException {
        Path path = Path.of("src/main/resources/mybatis/postgresql", fileName);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
