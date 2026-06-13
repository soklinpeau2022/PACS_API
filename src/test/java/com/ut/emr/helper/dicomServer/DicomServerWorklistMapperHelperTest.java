package com.ut.emrPacs.helper.dicomServer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DicomServerWorklistMapperHelperTest {

    @Test
    void normalizeModalityShouldResolveFriendlyNames() {
        assertEquals("CT", DicomServerWorklistMapperHelper.normalizeModality("Computed Tomography"));
        assertEquals("MR", DicomServerWorklistMapperHelper.normalizeModality("Magnetic Resonance"));
        assertEquals("PT", DicomServerWorklistMapperHelper.normalizeModality("Positron Emission Tomography"));
        assertEquals("US", DicomServerWorklistMapperHelper.normalizeModality("Ultrasound"));
    }

    @Test
    void normalizeModalityShouldKeepExistingCodes() {
        assertEquals("CT", DicomServerWorklistMapperHelper.normalizeModality("CT"));
        assertEquals("MR", DicomServerWorklistMapperHelper.normalizeModality("MRI"));
        assertEquals("OT", DicomServerWorklistMapperHelper.normalizeModality("External Camera Photography"));
    }
}
