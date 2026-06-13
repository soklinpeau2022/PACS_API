package com.ut.emrPacs.model.dto.response.pacs.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class PacsResultImageResponse {
    @JsonIgnore
    private Long id;
    @JsonIgnore
    private Long resultId;
    @JsonIgnore
    private Long hospitalId;
    @JsonIgnore
    private Long modalityId;
    private String imageKey;
    @JsonIgnore
    private String imagePath;
    private String imageUrl;
    private String originalFileName;
    private String fileType;
    private Long fileSize;
    private Integer sortOrder;
    private String createdAt;
}
