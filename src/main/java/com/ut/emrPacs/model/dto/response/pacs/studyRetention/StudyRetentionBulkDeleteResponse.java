package com.ut.emrPacs.model.dto.response.pacs.studyRetention;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StudyRetentionBulkDeleteResponse {
    private String mode;
    private Integer requested;
    private Integer processed;
    private Integer deleted;
    private Integer failed;
    private Integer skipped;
    private Integer chunkSize;
    private Integer chunks;
    private List<StudyRetentionDeleteItemResult> results = new ArrayList<>();

    public void addResult(StudyRetentionDeleteItemResult result) {
        if (result == null) {
            return;
        }
        results.add(result);
        processed = nullSafe(processed) + 1;
        if ("DELETED".equals(result.getResult())) {
            deleted = nullSafe(deleted) + 1;
        } else if ("FAILED".equals(result.getResult())) {
            failed = nullSafe(failed) + 1;
        } else {
            skipped = nullSafe(skipped) + 1;
        }
    }

    private static int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }
}
