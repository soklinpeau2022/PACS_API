package com.ut.emrPacs.model.dto.response.dropDown;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class DropDownModelResponse {

    @JsonIgnore
    private Long value;
    private String publicKey;
    private String label;

}
