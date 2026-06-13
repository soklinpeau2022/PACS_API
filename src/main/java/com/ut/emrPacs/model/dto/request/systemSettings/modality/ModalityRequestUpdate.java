package com.ut.emrPacs.model.dto.request.systemSettings.modality;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ModalityRequestUpdate {
    @JsonIgnore
    private Long id;
    @JsonAlias({"key", "uuid"})
    private String publicKey;
    @Size(max = 20)
    private String abbr;
    @Size(max = 255)
    private String name;
    private Long isActive;
}
