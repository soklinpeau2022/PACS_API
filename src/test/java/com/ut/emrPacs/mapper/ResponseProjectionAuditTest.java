package com.ut.emrPacs.mapper;

import com.ut.emrPacs.model.dto.response.dropDown.DropDownModelResponse;
import com.ut.emrPacs.model.dto.response.pacs.patient.PatientResponse;
import com.ut.emrPacs.model.dto.response.systemSettings.hospital.HospitalResponse;
import com.ut.emrPacs.model.dto.response.systemSettings.hospital.HospitalResponseDetail;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseProjectionAuditTest {

    @Test
    void patientResponseShouldMatchPatientMapperProjection() {
        assertResponseFieldsContain(
                PatientResponse.class,
                Set.of(
                        "id", "hospitalId", "hospitalPublicKey", "hospitalCode", "hospitalName",
                        "publicKey", "patientCode", "patientHn", "firstName", "lastName", "phoneNumber", "gender", "dateOfBirth"
                )
        );
    }

    @Test
    void dropDownResponseShouldMatchDropDownMapperProjection() {
        assertResponseFieldsContain(
                DropDownModelResponse.class,
                Set.of("value", "publicKey", "label")
        );
    }

    @Test
    void hospitalListResponseShouldMatchMapperProjectionAndAllowedDerivedFields() {
        assertResponseFieldsContain(
                HospitalResponse.class,
                Set.of(
                        "id", "publicKey", "code", "abbr", "name", "nameKhmer", "timezone",
                        "logoFileName", "logoFileType", "logoFileSize", "logoAvailable",
                        "deploymentLocked", "packageBuiltAt",
                        "createdById", "createdBy", "created", "modifiedById", "modifiedBy", "modified",
                        "hospitalUserList"
                )
        );
    }

    @Test
    void hospitalDetailResponseShouldMatchMapperProjectionAndAllowedDerivedFields() {
        assertResponseFieldsContain(
                HospitalResponseDetail.class,
                Set.of(
                        "id", "publicKey", "code", "abbr", "hospitalName", "nameInKhmer", "timezone",
                        "logoPath", "logoFileName", "logoFileType", "logoFileSize", "logoAvailable",
                        "deploymentLocked", "packageBuiltAt",
                        "userNameResponseList"
                )
        );
    }

    private static void assertResponseFieldsContain(Class<?> responseType, Set<String> allowedFields) {
        Set<String> actualFields = Arrays.stream(responseType.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(
                allowedFields.containsAll(actualFields),
                () -> responseType.getSimpleName() + " contains fields not audited by mapper projection. Actual="
                        + actualFields + ", allowed=" + allowedFields
        );
    }
}
