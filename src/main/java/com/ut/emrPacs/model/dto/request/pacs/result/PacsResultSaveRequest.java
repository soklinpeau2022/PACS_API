package com.ut.emrPacs.model.dto.request.pacs.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class PacsResultSaveRequest {
    private String resultKey;
    @Schema(hidden = true)
    @Positive
    @JsonIgnore
    private Long id;
    private String hospitalKey;
    @Positive
    @JsonIgnore
    private Long hospitalId;
    private String modalityKey;
    @Positive
    @JsonIgnore
    private Long modalityId;
    private String studyKey;
    @Positive
    @JsonIgnore
    private Long studyId;
    private String worklistKey;
    @Positive
    @JsonIgnore
    private Long worklistId;
    private String worklistCode;
    private String studyInstanceUid;
    private String accessionNumber;
    private String patientKey;
    @Positive
    @JsonIgnore
    private Long patientId;
    private String patientCode;
    private String patientName;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate resultDate;
    @JsonAlias({"templatePublicKey", "templateUuid", "templateUUID"})
    private String templateKey;
    @Positive
    @JsonIgnore
    private Long templateId;
    private String resultText;
    private Boolean completed;
}
