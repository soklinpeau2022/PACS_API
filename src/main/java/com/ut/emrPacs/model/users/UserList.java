package com.ut.emrPacs.model.users;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserList implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;

    private String username;

}
