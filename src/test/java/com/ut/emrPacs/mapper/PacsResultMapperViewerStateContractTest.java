package com.ut.emrPacs.mapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PacsResultMapperViewerStateContractTest {

    @Test
    void viewerStateUpdateDetectsPayloadAndContextChanges() throws Exception {
        Path mapperPath = Path.of("src/main/resources/mybatis/postgresql/PacsResultMapper.xml");
        String xml = Files.readString(mapperPath, StandardCharsets.UTF_8);

        assertTrue(xml.contains("payload_sha256 IS DISTINCT FROM #{request.payloadSha256}"));
        assertTrue(xml.contains("modality_id IS DISTINCT FROM #{request.modalityId}"));
        assertTrue(xml.contains("study_id IS DISTINCT FROM #{request.studyId}"));
        assertTrue(xml.contains("worklist_id IS DISTINCT FROM #{request.worklistId}"));
        assertTrue(xml.contains("patient_id IS DISTINCT FROM #{request.patientId}"));
        assertTrue(xml.contains("study_instance_uid IS DISTINCT FROM #{request.studyInstanceUid}"));
        assertTrue(xml.contains("accession_number IS DISTINCT FROM #{request.accessionNumber}"));
        assertTrue(xml.contains("patient_code IS DISTINCT FROM #{request.patientCode}"));
        assertTrue(xml.contains("schema_version IS DISTINCT FROM #{request.schemaVersion}"));
    }
}
