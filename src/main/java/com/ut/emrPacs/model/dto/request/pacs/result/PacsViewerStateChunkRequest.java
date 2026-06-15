package com.ut.emrPacs.model.dto.request.pacs.result;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PacsViewerStateChunkRequest extends PacsViewerStateRequest {
    private String uploadId;
    private Integer chunkIndex;
    private Integer chunkCount;
    private Long payloadSizeBytes;
    private String payloadSha256;
    private String chunkData;
}
