package com.ut.emrPacs.model.dto.response.pacs.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PacsResultTemplateResponse {
    @JsonIgnore
    private Long id;
    private String publicKey;
    private String templateKey;
    @JsonIgnore
    private Long hospitalId;
    private String hospitalKey;
    private String hospitalCode;
    private String hospitalName;
    @JsonIgnore
    private Long modalityId;
    private String modalityKey;
    private String modalityCode;
    private String modalityName;
    private String templateName;
    private String templateContent;
    private Boolean active;
    private String createdAt;
    private String updatedAt;
    private String createdBy;
    private String modifiedBy;
}
