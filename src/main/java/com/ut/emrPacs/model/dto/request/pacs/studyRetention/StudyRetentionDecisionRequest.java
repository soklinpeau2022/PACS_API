package com.ut.emrPacs.model.dto.request.pacs.studyRetention;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudyRetentionDecisionRequest {
    @Size(max = 1000)
    private String note;

    private Boolean keepPermanent;

    public void setNote(String note) {
        if (note == null) {
            this.note = null;
            return;
        }
        String trimmed = note.trim();
        this.note = trimmed.isEmpty() ? null : trimmed;
    }
}
