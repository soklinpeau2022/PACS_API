package com.ut.emrPacs.model.dto.request.pacs.result;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PacsViewerStateChunkCompleteRequest extends PacsViewerStateRequest {
    private String uploadId;
    private Integer chunkCount;
    private Long payloadSizeBytes;
    private String payloadSha256;
}
