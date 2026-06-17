package com.ut.emrPacs.model.dto.response.pacs.dicomUpload;

import lombok.Data;

@Data
public class DicomChunkUploadInitResponse {
    private String uploadId;
    private String fileName;
    private Long totalSize;
    private Integer totalChunks;
    private Long chunkSize;
    private String expiresAt;
}
