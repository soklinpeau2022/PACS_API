package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerFindRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerInstanceUploadResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerSeriesResponse;
import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerWorklistCreateRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerStudyResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistCreateResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

public interface DicomServerClientService {
    DicomServerWorklistCreateResponse postToDicomServerWorklist(DicomServerWorklistCreateRequest request);

    DicomServerWorklistCreateResponse postToDicomServerWorklist(String worklistUrl, String username, String password, DicomServerWorklistCreateRequest request);

    DicomServerInstanceUploadResponse uploadInstance(String baseUrl, String username, String password, Resource dicomResource, long contentLength);

    DicomServerWorklistResponse getWorklistById(String worklistId);

    DicomServerWorklistResponse getWorklistById(String baseUrl, String username, String password, String worklistId);

    DicomServerWorklistResponse updateWorklistById(String worklistId, DicomServerWorklistCreateRequest request);

    DicomServerWorklistResponse updateWorklistById(String baseUrl, String username, String password, String worklistId, DicomServerWorklistCreateRequest request);

    void deleteWorklistById(String worklistId);

    void deleteWorklistById(String baseUrl, String username, String password, String worklistId);

    List<String> findStudyIdsByAccessionNumber(DicomServerFindRequest request);

    List<String> findStudyIdsByAccessionNumber(String baseUrl, String username, String password, DicomServerFindRequest request);

    DicomServerStudyResponse getStudyById(String studyId);

    DicomServerStudyResponse getStudyById(String baseUrl, String username, String password, String studyId);

    void deleteStudyById(String studyId);

    void deleteStudyById(String baseUrl, String username, String password, String studyId);

    List<DicomServerSeriesResponse> getSeriesByStudyId(String studyId);

    List<DicomServerSeriesResponse> getSeriesByStudyId(String baseUrl, String username, String password, String studyId);

    ResponseEntity<byte[]> getInstancePreview(String instanceId);

    ResponseEntity<byte[]> getInstancePreview(String baseUrl, String username, String password, String instanceId);

    ResponseEntity<byte[]> proxyDicomWeb(
            String dicomwebBaseUrl,
            String username,
            String password,
            String pathAndQuery,
            String acceptHeader
    );

    ResponseEntity<StreamingResponseBody> proxyDicomWebStream(
            String dicomwebBaseUrl,
            String username,
            String password,
            String pathAndQuery,
            String acceptHeader,
            String rangeHeader,
            String requestMethod
    );
}
