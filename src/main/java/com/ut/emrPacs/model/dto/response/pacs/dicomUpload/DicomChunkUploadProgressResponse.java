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
}
