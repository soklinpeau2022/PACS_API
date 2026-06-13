package com.ut.emrPacs.model.dto.request.pacs.dicom;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ut.emrPacs.model.base.filter.Filter;
import lombok.EqualsAndHashCode;
import lombok.Data;

@EqualsAndHashCode(callSuper = true)
@Data
public class HospitalModalityServerRouteListRequest extends Filter {
    @JsonAlias("modalityId")
    @JsonIgnore
    private Long modalityId;
    @JsonAlias({"modalityPublicKey", "modalityUuid", "modalityUUID"})
    private String modalityKey;
}
