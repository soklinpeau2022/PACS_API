package com.ut.emrPacs.model.dto.response.pacs.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class PacsResultContextResponse {
    @JsonIgnore
    private Long hospitalId;
    private String hospitalKey;
    private String hospitalCode;
    private String hospitalName;
    @JsonIgnore
    private String hospitalLogoPath;
    private String hospitalLogoFileName;
    private String hospitalLogoFileType;
    private Boolean hospitalLogoAvailable;
    @JsonIgnore
    private Long modalityId;
    private String modalityKey;
    private String modalityCode;
    private String modalityName;
    @JsonIgnore
    private Long studyId;
    private String studyKey;
    @JsonIgnore
    private Long worklistId;
    private String worklistKey;
    private String worklistCode;
    private String studyInstanceUid;
    private String accessionNumber;
    @JsonIgnore
    private Long patientId;
    private String patientKey;
    private String patientCode;
    private String patientName;
    private String patientPhone;
    private String status;
    private String worklistStatus;
    private String dicomServerStudyId;
    private String dicomServerPatientId;
    private String dicomServerSeriesId;
    private String imageReceivedAt;
}
