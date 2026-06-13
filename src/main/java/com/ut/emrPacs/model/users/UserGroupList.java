package com.ut.emrPacs.model.users;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserGroupList implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private Long id;
    private String publicKey;

    @JsonIgnore
    private Long userId;

    private String name;
}
