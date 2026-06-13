package com.ut.emrPacs.helper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionCodeGenerateTest {

    @Test
    void buildsHospitalScopedPatientCode() {
        assertEquals("26-KSFH-P0000173", FunctionCodeGenerate.buildPatientCode("26", "KSFH", 173L));
        assertEquals("26-NMCHC-P0000001", FunctionCodeGenerate.buildPatientCode("26", "NMCHC", 1L));
    }

    @Test
    void buildsHospitalScopedVisitCode() {
        String visitCode = FunctionCodeGenerate.buildVisitCode("CT", "KSFH", "20260526", 1L);

        assertEquals("CT-KSFH-260526-0001", visitCode);
        assertTrue(visitCode.length() <= 32);
        assertEquals("DX-NMCHC-260601-0042", FunctionCodeGenerate.buildVisitCode("DX", "NMCHC", "20260601", 42L));
    }
}
