package com.ut.emrPacs.model.role;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class Module implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private Long id;

    private String publicKey;

    private String code;

    private String name;

    private String type;

    private String actionKey;

    private Integer orderNo;

    private Boolean checked;

}
