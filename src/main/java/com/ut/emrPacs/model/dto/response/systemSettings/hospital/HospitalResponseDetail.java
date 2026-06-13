package com.ut.emrPacs.model.dto.response.systemSettings.hospital;

import lombok.Data;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class HospitalResponseDetail {
    @JsonIgnore
    private Long id;
    private String publicKey;
    private String code;
    private String abbr;
    private String hospitalName;
    private String nameInKhmer;
    private String timezone;
    @JsonIgnore
    private String logoPath;
    private String logoFileName;
    private String logoFileType;
    private Long logoFileSize;
    private Boolean logoAvailable;
    private List<UserNameResponse> userNameResponseList;
}
