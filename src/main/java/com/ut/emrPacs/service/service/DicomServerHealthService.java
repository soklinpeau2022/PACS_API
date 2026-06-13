package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.dto.response.pacs.dicom.DicomServerHealthResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.DicomServerHealthSettingsResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.DicomServerHealthSummaryResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;

import java.util.List;

public interface DicomServerHealthService {
    void enrichDicomServerRows(List<HospitalDicomServerResponse> rows);

    List<DicomServerHealthResponse> listHealth(Long hospitalId);

    DicomServerHealthSummaryResponse getSummary(Long hospitalId);

    DicomServerHealthSettingsResponse getSettings();

    DicomServerHealthSettingsResponse updateSettings(Boolean enabled, Integer pollIntervalSeconds, Long modifiedBy);
}
