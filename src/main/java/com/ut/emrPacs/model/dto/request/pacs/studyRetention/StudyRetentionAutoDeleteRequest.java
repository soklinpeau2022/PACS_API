package com.ut.emrPacs.model.dto.request.pacs.studyRetention;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class StudyRetentionAutoDeleteRequest {
    @JsonIgnore
    private Long hospitalId;

    @JsonAlias({"hospitalPublicKey", "hospitalUuid", "hospitalUUID"})
    private String hospitalKey;

    @Min(1)
    @Max(500)
    private Integer maxItems;

    @Min(1)
    @Max(100)
    private Integer chunkSize;

    public void setHospitalKey(String hospitalKey) {
        this.hospitalKey = trimToNull(hospitalKey);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
