package com.ut.emrPacs.model.dto.request.pacs.study;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StudyStatusUpdateRequest {
    @NotBlank
    private String status;
}
