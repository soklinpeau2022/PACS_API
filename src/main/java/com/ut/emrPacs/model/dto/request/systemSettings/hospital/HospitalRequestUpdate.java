package com.ut.emrPacs.model.dto.request.systemSettings.hospital;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;

import jakarta.validation.constraints.Size;
@Data
public class HospitalRequestUpdate {
    @Schema(description = "Hospital ID for update. Omit for create.")
    @JsonIgnore
    private Long id;
    @Schema(description = "Public hospital UUID for update. Preferred over numeric id.")
    @JsonAlias({"key", "uuid"})
    private String publicKey;

    @Schema(description = "Unique hospital code.", example = "HSP001")
    @Size(max = 50)
    private String code;

    @Schema(description = "Hospital name in English.", example = "City General Hospital")
    @Size(max = 255)
    private String name;

    @Schema(description = "Hospital abbreviation.", example = "CGH")
    @Size(max = 20)
    private String abbr;

    @Schema(description = "Hospital name in Khmer.", example = "មន្ទីរពេទ្យស៊ីធី")
    @Size(max = 255)
    private String nameKhmer;

    @Schema(description = "IANA timezone for hospital operations.", example = "Asia/Phnom_Penh")
    @Size(max = 80)
    private String timezone;

    @JsonAlias("hospitalUserIds")
    @Schema(
            description = "User IDs assigned to this hospital. Preferred format: [1,2,3]. Legacy format [{\"userId\":1}] is still accepted.",
            example = "[1,2,3]"
    )
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<Long> hospitalUserList;
    @JsonAlias({"hospitalUserPublicKeys", "hospitalUserUuids", "userKeys"})
    private List<String> hospitalUserKeys;

    @JsonAlias("modalityIds")
    @Schema(description = "Modality IDs assigned to this hospital.", example = "[1,2,3]")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<Long> modalityIds;
    @JsonAlias({"modalityPublicKeys", "modalityUuids"})
    private List<String> modalityKeys;

    @Schema(description = "Set true to remove the current hospital logo.")
    private Boolean removeLogo;

    public void setHospitalUserList(List<?> hospitalUsers) {
        if (hospitalUsers == null) {
            this.hospitalUserList = null;
            return;
        }

        List<Long> normalizedUserIds = new ArrayList<>();
        for (Object item : hospitalUsers) {
            Long userId = extractUserId(item);
            if (userId != null) {
                normalizedUserIds.add(userId);
            }
        }
        this.hospitalUserList = normalizedUserIds;
    }

    private Long extractUserId(Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof Number number) {
            return number.longValue();
        }
        if (item instanceof String str) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (item instanceof Map<?, ?> map) {
            Object userId = map.get("userId");
            if (userId instanceof Number number) {
                return number.longValue();
            }
            if (userId instanceof String str) {
                try {
                    return Long.parseLong(str.trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
