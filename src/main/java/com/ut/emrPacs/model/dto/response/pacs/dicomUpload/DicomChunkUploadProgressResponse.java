package com.ut.emrPacs.model.dto.response.pacs.dicomUpload;

import lombok.Data;

@Data
public class DicomChunkUploadProgressResponse {
    private String uploadId;
    private Integer index;
    private Integer receivedChunks;
    private Integer totalChunks;
    private Long receivedBytes;
    private Long totalSize;
    private Boolean complete;
    private String state;
    private Integer uploadPercent;
    private Integer processingPercent;
    private Integer processedItems;
    private Integer totalItems;
    private String stage;
    private String message;
    private Boolean successful;
}
