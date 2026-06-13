package com.ut.emrPacs.model.dto.request.pacs.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PacsResultFindByWorklistRequest {
    private String hospitalKey;
    @Positive
    @JsonIgnore
    private Long hospitalId;

    private String worklistKey;
    @Positive
    @JsonIgnore
    private Long worklistId;
}
