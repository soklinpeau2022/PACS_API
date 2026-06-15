package com.ut.emrPacs.helper.dicomServer;

import com.ut.emrPacs.model.dto.request.pacs.dicomServer.DicomServerWorklistCreateRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicomServer.DicomServerWorklistResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDetailRow;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistDicomWorklistResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DicomServerWorklistMapperHelperTest {

    @Test
    void normalizeModalityShouldResolveFriendlyNames() {
        assertEquals("CT", DicomServerWorklistMapperHelper.normalizeModality("Computed Tomography"));
        assertEquals("MR", DicomServerWorklistMapperHelper.normalizeModality("Magnetic Resonance"));
        assertEquals("PT", DicomServerWorklistMapperHelper.normalizeModality("Positron Emission Tomography"));
        assertEquals("US", DicomServerWorklistMapperHelper.normalizeModality("Ultrasound"));
    }

    @Test
    void normalizeModalityShouldKeepExistingCodes() {
        assertEquals("CT", DicomServerWorklistMapperHelper.normalizeModality("CT"));
        assertEquals("MR", DicomServerWorklistMapperHelper.normalizeModality("MRI"));
        assertEquals("OT", DicomServerWorklistMapperHelper.normalizeModality("External Camera Photography"));
    }

    @Test
    void toCreateRequestShouldUsePatientHnAsDicomPatientId() {
        WorklistDetailRow worklist = new WorklistDetailRow();
        worklist.setPatientUid("26-KSFH-P0000073");
        worklist.setPatientHn("23-014677");
        worklist.setPatientName("HEL SOK");

        DicomServerWorklistCreateRequest request = DicomServerWorklistMapperHelper.toCreateRequest(
                worklist,
                "CT-KSFH-260614-0001",
                "CT",
                "SOFT TISSUE Spine 3.0",
                LocalDate.of(2026, 6, 15),
                LocalTime.of(8, 30),
                "UDAYA_DICOM_SERVER"
        );

        assertEquals("23-014677", request.getTags().getPatientID());
    }

    @Test
    void toWorklistDicomWorklistResponseShouldKeepPatientUidAndMapPatientIdToHn() {
        WorklistDetailRow worklist = new WorklistDetailRow();
        worklist.setPatientUid("26-KSFH-P0000073");
        worklist.setPatientHn("23-014677");
        worklist.setPatientName("HEL SOK");

        DicomServerWorklistResponse serverWorklist = new DicomServerWorklistResponse();
        DicomServerWorklistResponse.Tags tags = new DicomServerWorklistResponse.Tags();
        tags.setPatientID("23-014677");
        tags.setPatientName("HEL SOK");
        serverWorklist.setTags(tags);

        WorklistDicomWorklistResponse response = DicomServerWorklistMapperHelper.toWorklistDicomWorklistResponse(
                worklist,
                serverWorklist,
                "ok"
        );

        assertEquals("26-KSFH-P0000073", response.getPatientUid());
        assertEquals("23-014677", response.getPatientHn());
    }
}
