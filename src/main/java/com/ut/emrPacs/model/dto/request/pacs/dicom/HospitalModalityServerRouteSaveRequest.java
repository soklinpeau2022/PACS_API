package com.ut.emrPacs.model.dto.request.pacs.dicom;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class HospitalModalityServerRouteSaveRequest {
    @JsonIgnore
    private Long hospitalId;
    private String hospitalKey;

    @JsonIgnore
    private Long dicomServerId;
    private String dicomServerKey;

    @Size(max = 200)
    private List<RouteItem> routes;

    @Data
    public static class RouteItem {
        @JsonIgnore
        private Long machineId;
        private String machineKey;

        @JsonAlias("modalityId")
        @JsonIgnore
        private Long modalityId;
        private String modalityKey;
    }
}
