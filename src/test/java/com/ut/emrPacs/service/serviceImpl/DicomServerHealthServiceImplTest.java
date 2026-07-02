package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.mapper.pacs.DicomServerMapper;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DicomServerHealthServiceImplTest {

    @Mock
    private DicomServerMapper dicomServerMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void transientProbeFailuresKeepLastKnownOnlineStateDuringGraceWindow() {
        DicomServerHealthServiceImpl service = serviceWithOfflinePolicy(3, 180_000L);
        Instant firstCheck = Instant.parse("2026-06-23T00:00:00Z");

        Object first = buildSnapshot(service, true, firstCheck, null, 21L);
        Object second = buildSnapshot(service, false, firstCheck.plusSeconds(5), first, 5_000L);
        Object third = buildSnapshot(service, false, firstCheck.plusSeconds(10), second, 5_000L);

        assertSnapshot(first, "ONLINE", true, 0, null);
        assertSnapshot(second, "ONLINE", true, 1, null);
        assertSnapshot(third, "ONLINE", true, 2, null);
    }

    @Test
    void repeatedFailuresAfterGraceWindowBecomeOffline() {
        DicomServerHealthServiceImpl service = serviceWithOfflinePolicy(2, 0L);
        Instant firstCheck = Instant.parse("2026-06-23T00:00:00Z");

        Object first = buildSnapshot(service, true, firstCheck, null, 21L);
        Object second = buildSnapshot(service, false, firstCheck.plusSeconds(1), first, 5_000L);
        Object third = buildSnapshot(service, false, firstCheck.plusSeconds(2), second, 5_000L);

        assertSnapshot(second, "ONLINE", true, 1, null);
        assertSnapshot(third, "OFFLINE", false, 2, firstCheck.plusSeconds(2));
    }

    @Test
    void rewriteLoopbackHealthUrlForContainerProbe() {
        String rewritten = ReflectionTestUtils.invokeMethod(
                DicomServerHealthServiceImpl.class,
                "rewriteLoopbackUrl",
                "http://127.0.0.1:8042/system",
                "pacs-docker-host.local"
        );

        assertEquals("http://pacs-docker-host.local:8042/system", rewritten);
    }

    @Test
    void rewriteLoopbackHealthUrlKeepsNonLoopbackHost() {
        String original = "http://dicom-ct.example.lan:8042/system";
        String rewritten = ReflectionTestUtils.invokeMethod(
                DicomServerHealthServiceImpl.class,
                "rewriteLoopbackUrl",
                original,
                "pacs-docker-host.local"
        );

        assertEquals(original, rewritten);
    }

    @Test
    @SuppressWarnings("unchecked")
    void healthUrlsShouldIncludeGeneratedDockerAliasFallback() {
        HospitalDicomServerResponse server = new HospitalDicomServerResponse();
        server.setId(4L);
        server.setHospitalName("KSFH Hospital");
        server.setName("UDAYA_DICOM_SERVER KSFH");
        server.setIpAddress("192.168.192.4");
        server.setPort(8042);
        server.setPublicHealthCheckUrl("http://192.168.192.4:8042/system");

        List<String> urls = ReflectionTestUtils.invokeMethod(
                DicomServerHealthServiceImpl.class,
                "buildHealthUrls",
                server
        );

        assertEquals("http://192.168.192.4:8042/system", urls.get(0));
        assertTrue(urls.contains("http://dicom-server-ksfh:8042/system"));
    }

    private DicomServerHealthServiceImpl serviceWithOfflinePolicy(int threshold, long graceMs) {
        DicomServerHealthServiceImpl service = new DicomServerHealthServiceImpl(dicomServerMapper, jdbcTemplate);
        ReflectionTestUtils.setField(service, "offlineFailureThreshold", threshold);
        ReflectionTestUtils.setField(service, "offlineGraceMs", graceMs);
        return service;
    }

    private static Object buildSnapshot(
            DicomServerHealthServiceImpl service,
            boolean online,
            Instant checkedAt,
            Object previous,
            Long responseTimeMs
    ) {
        return ReflectionTestUtils.invokeMethod(
                service,
                "buildSnapshot",
                online,
                checkedAt,
                previous,
                responseTimeMs
        );
    }

    private static void assertSnapshot(
            Object snapshot,
            String status,
            boolean online,
            int consecutiveFailures,
            Instant offlineSince
    ) {
        assertEquals(status, ReflectionTestUtils.getField(snapshot, "status"));
        if (online) {
            assertTrue((Boolean) ReflectionTestUtils.getField(snapshot, "online"));
        } else {
            assertFalse((Boolean) ReflectionTestUtils.getField(snapshot, "online"));
        }
        assertEquals(consecutiveFailures, ReflectionTestUtils.getField(snapshot, "consecutiveFailures"));
        if (offlineSince == null) {
            assertNull(ReflectionTestUtils.getField(snapshot, "offlineSince"));
        } else {
            assertEquals(offlineSince, ReflectionTestUtils.getField(snapshot, "offlineSince"));
        }
    }
}
