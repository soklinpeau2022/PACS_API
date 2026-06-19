package com.ut.emrPacs.service.service;

@FunctionalInterface
public interface DicomUploadProgressListener {
    DicomUploadProgressListener NO_OP = (percent, processedItems, totalItems, stage) -> { };

    void onProgress(int percent, int processedItems, int totalItems, String stage);
}
