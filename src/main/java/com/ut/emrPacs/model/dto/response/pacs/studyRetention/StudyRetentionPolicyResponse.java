package com.ut.emrPacs.model.dto.response.pacs.studyRetention;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class StudyRetentionPolicyResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;

    @JsonIgnore
    private Long hospitalId;
    private String hospitalPublicKey;
    private String hospitalName;

    @JsonIgnore
    private Long dicomServerId;
    private String dicomServerPublicKey;
    private String dicomServerName;

    @JsonIgnore
    private Long modalityId;
    private String modalityPublicKey;
    private String modalityCode;
    private String modalityName;

    private Integer retentionDays;
    private Integer retentionValue;
    private String retentionUnit;
    private Integer notifyBeforeDays;
    private Boolean requireApproval;
    private Boolean enabled;
    private Boolean autoDelete;
    private String scopeLabel;
    private String notes;
    private String createdAt;
    private String modifiedAt;
}
