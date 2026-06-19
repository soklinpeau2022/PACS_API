package com.ut.emrPacs.model.dto.persistence.pacs;

public record DicomServerCallbackLogEntry(
        Long hospitalId,
        Long dicomServerId,
        String dedupeKey,
        String payloadSha256,
        String event,
        String accessionNumber,
        String dicomServerStudyId,
        String dicomServerPatientId,
        String dicomServerSeriesIdsJson,
        String payloadJson,
        Boolean success,
        String errorMessage,
        String warningMessage,
        String receivedAtIso
) {
}

