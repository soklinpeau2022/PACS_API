package com.ut.emrPacs.model.dto.response.pacs.studyRetention;

import lombok.Data;

@Data
public class StudyRetentionDeleteItemResult {
    private String studyPublicKey;
    private String patientName;
    private String accessionNumber;
    private String previousStatus;
    private String result;
    private String message;

    public static StudyRetentionDeleteItemResult of(StudyRetentionReviewResponse candidate, String result, String message) {
        StudyRetentionDeleteItemResult item = new StudyRetentionDeleteItemResult();
        if (candidate != null) {
            item.setStudyPublicKey(candidate.getStudyPublicKey());
            item.setPatientName(candidate.getPatientName());
            item.setAccessionNumber(candidate.getAccessionNumber());
            item.setPreviousStatus(candidate.getStatus());
        }
        item.setResult(result);
        item.setMessage(message);
        return item;
    }

    public static StudyRetentionDeleteItemResult missing(String studyPublicKey, String message) {
        StudyRetentionDeleteItemResult item = new StudyRetentionDeleteItemResult();
        item.setStudyPublicKey(studyPublicKey);
        item.setResult("NOT_FOUND");
        item.setMessage(message);
        return item;
    }
}
