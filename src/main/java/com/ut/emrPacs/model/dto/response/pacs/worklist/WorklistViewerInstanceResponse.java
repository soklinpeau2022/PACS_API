package com.ut.emrPacs.model.dto.response.pacs.worklist;

import lombok.Data;

@Data
public class WorklistViewerInstanceResponse {
    private String instanceId;
    private String label;
    private String previewPath;
}
