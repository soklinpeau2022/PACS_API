package com.ut.emrPacs.authentication.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Dual-key (IP + username) account lockout tracker with progressive lockout.
 *
 * <p>Two independent locks are applied:</p>
 * <ul>
 *   <li><b>IP lock</b>: blocks a source IP that generates too many failures from any username.</li>
 *   <li><b>Username lock</b>: blocks attacks on a specific account from distributed IPs (VPN/TOR/botnet).</li>
 * </ul>
 *
 * <p>Progressive lockout tiers (each successive lockout doubles the duration):</p>
 * <ol>
 *   <li>1st lockout: {@code lockDurationSeconds} (default 5 min)</li>
 *   <li>2nd lockout: 2× = 10 min</li>
 *   <li>3rd lockout: 4× = 20 min</li>
 *   <li>4th+ lockout: capped at {@code maxLockDurationSeconds} (default 60 min)</li>
 * </ol>
 *
 * <p>Memory: a background scheduler evicts stale records every 10 minutes.</p>
 */
@Component
public class LoginAttemptTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginAttemptTracker.class);

    /** Max failures before lockout applies (per IP and per username independently). */
    @Value("${app.security.lockout.max-failures:5}")
    private int maxFailures;

    /** Base lockout duration in seconds (doubles on each repeat lockout). */
    @Value("${app.security.lockout.lock-duration-seconds:300}")
    private int lockDurationSeconds;

    /** Maximum lockout cap in seconds regardless of repeat count. */
    @Value("${app.security.lockout.max-lock-duration-seconds:3600}")
    private int maxLockDurationSeconds;

    /** How long an idle record is kept before eviction (seconds). */
    private static final long IDLE_EVICTION_SECONDS = 7200; // 2 hours

    // Two separate maps: one keyed by IP, one by username
    private final ConcurrentHashMap<String, AttemptRecord> ipRecords       = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AttemptRecord> usernameRecords = new ConcurrentHashMap<>();

    public LoginAttemptTracker() {
        // Background cleanup every 10 minutes to prevent memory exhaustion under sustained attack
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lockout-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::evictStale, 10, 10, TimeUnit.MINUTES);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Records a failed login for both the IP and the target username. */
    public void recordFailure(String ip, String username) {
        recordFailureForKey(ip, ipRecords, "ip");
        if (username != null && !username.isBlank()) {
            recordFailureForKey(username.toLowerCase().trim(), usernameRecords, "username");
        }
    }

    /** Clears counters for both the IP and username on successful login. */
    public void recordSuccess(String ip, String username) {
        ipRecords.remove(ip);
        if (username != null && !username.isBlank()) {
            usernameRecords.remove(username.toLowerCase().trim());
        }
    }

    /**
     * Returns true if the IP OR the username is locked.
     * Call this BEFORE authenticating so locked requests never reach bcrypt.
     */
    public boolean isLocked(String ip, String username) {
        if (isKeyLocked(ip, ipRecords)) return true;
        if (username != null && !username.isBlank()) {
            return isKeyLocked(username.toLowerCase().trim(), usernameRecords);
        }
        return false;
    }

    /** Returns remaining lockout seconds for whichever lock is longer (IP or username). */
    public long getLockRemainingSeconds(String ip, String username) {
        long ipRemaining  = getRemainingForKey(ip, ipRecords);
        long usrRemaining = (username != null && !username.isBlank())
                ? getRemainingForKey(username.toLowerCase().trim(), usernameRecords) : 0;
        return Math.max(ipRemaining, usrRemaining);
    }

    /** Returns a human-readable reason string for logging. */
    public String getLockReason(String ip, String username) {
        boolean ipLocked  = isKeyLocked(ip, ipRecords);
        boolean usrLocked = (username != null && !username.isBlank())
                && isKeyLocked(username.toLowerCase().trim(), usernameRecords);
        if (ipLocked && usrLocked) return "ip_and_username_locked";
        if (ipLocked)              return "ip_locked";
        if (usrLocked)             return "username_locked";
        return "not_locked";
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void recordFailureForKey(String key, ConcurrentHashMap<String, AttemptRecord> map, String keyType) {
        AttemptRecord record = map.computeIfAbsent(key, k -> new AttemptRecord());
        synchronized (record) {
            long now = System.currentTimeMillis();
            record.lastSeenMs = now;
            // If the previous lock already expired, start a new lockout tier
            if (record.lockedUntilMs > 0 && now >= record.lockedUntilMs) {
                record.failures = 0;
                record.lockedUntilMs = 0;
                // Keep lockTier to remember how many times this key has been locked
            }
            record.failures++;
            if (record.failures >= maxFailures) {
                record.lockTier++;
                // Progressive: base × 2^(tier-1), capped at max
                long durationSeconds = Math.min(
                        (long) lockDurationSeconds * (1L << (record.lockTier - 1)),
                        maxLockDurationSeconds
                );
                record.lockedUntilMs = now + durationSeconds * 1000L;
                LOGGER.warn("SECURITY_EVENT {{\"event\":\"account_lockout\",\"keyType\":\"{}\",\"failures\":{},\"tier\":{},\"lockSeconds\":{}}}",
                        keyType, record.failures, record.lockTier, durationSeconds);
            } else {
                LOGGER.warn("SECURITY_EVENT {{\"event\":\"failed_login\",\"keyType\":\"{}\",\"failures\":{}}}",
                        keyType, record.failures);
            }
        }
    }

    private boolean isKeyLocked(String key, ConcurrentHashMap<String, AttemptRecord> map) {
        AttemptRecord record = map.get(key);
        if (record == null) return false;
        synchronized (record) {
            if (record.lockedUntilMs <= 0) return false;
            long now = System.currentTimeMillis();
            if (now >= record.lockedUntilMs) {
                // Lock expired — reset failures but keep tier for progressive escalation
                record.failures = 0;
                record.lockedUntilMs = 0;
                return false;
            }
            return true;
        }
    }

    private long getRemainingForKey(String key, ConcurrentHashMap<String, AttemptRecord> map) {
        AttemptRecord record = map.get(key);
        if (record == null) return 0;
        synchronized (record) {
            if (record.lockedUntilMs <= 0) return 0;
            long remaining = (record.lockedUntilMs - System.currentTimeMillis()) / 1000;
            return Math.max(0, remaining);
        }
    }

    /** Evicts records that have been idle longer than IDLE_EVICTION_SECONDS and are not locked. */
    private void evictStale() {
        long now = System.currentTimeMillis();
        long cutoffMs = IDLE_EVICTION_SECONDS * 1000L;
        int ipEvicted  = evictFrom(ipRecords, now, cutoffMs);
        int usrEvicted = evictFrom(usernameRecords, now, cutoffMs);
        if (ipEvicted > 0 || usrEvicted > 0) {
            LOGGER.debug("Lockout cleanup: evicted ip={} username={} records", ipEvicted, usrEvicted);
        }
    }

    private int evictFrom(ConcurrentHashMap<String, AttemptRecord> map, long now, long cutoffMs) {
        int[] count = {0};
        map.entrySet().removeIf(entry -> {
            AttemptRecord r = entry.getValue();
            synchronized (r) {
                // Only evict if NOT currently locked and idle long enough
                boolean notLocked = r.lockedUntilMs <= 0 || now >= r.lockedUntilMs;
                boolean idle = (now - r.lastSeenMs) > cutoffMs;
                if (notLocked && idle) {
                    count[0]++;
                    return true;
                }
                return false;
            }
        });
        return count[0];
    }

    private static final class AttemptRecord {
        int  failures     = 0;
        int  lockTier     = 0;     // increments on each successive lockout
        long lockedUntilMs = 0;
        long lastSeenMs   = System.currentTimeMillis();
    }
}
