package com.ut.emrPacs.model.dto.request.pacs.studyRetention;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StudyRetentionBulkDeleteRequest {
    @JsonAlias({"studyKeys", "publicKeys", "studyPublicKeys"})
    @Size(max = 500)
    private List<String> studyPublicKeys = new ArrayList<>();

    @Min(1)
    @Max(100)
    private Integer chunkSize;

    @Size(max = 1000)
    private String note;

    public void setStudyPublicKeys(List<String> studyPublicKeys) {
        this.studyPublicKeys = studyPublicKeys == null ? new ArrayList<>() : studyPublicKeys;
    }

    public void setNote(String note) {
        this.note = trimToNull(note);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
