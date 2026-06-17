package com.ut.emrPacs.model.dto.request.pacs.dicomUpload;

import lombok.Data;

@Data
public class DicomChunkUploadInitRequest {
    private String hospitalKey;
    private String dicomServerKey;
    private String fileName;
    private Long totalSize;
    private Integer totalChunks;
    private Long chunkSize;
}
