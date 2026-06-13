package com.ut.emrPacs.service.serviceImpl;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.helper.TelegramHelper;
import com.ut.emrPacs.config.ApiConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.mapper.log.ActivityLogMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.ActivityFilter;
import com.ut.emrPacs.model.log.ActivityLog;
import com.ut.emrPacs.service.service.ActivityLogService;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ActivityLogServiceImpl implements ActivityLogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityLogServiceImpl.class);
    private static final int MAX_ENDPOINT_LENGTH = 255;
    private static final String ERROR_SHORT_UNKNOWN = "Unknown Error";
    private static final String ERROR_SHORT_SQL_GRAMMAR = "SQL Grammar Error";
    private static final String ERROR_SHORT_NULL_POINTER = "Null Pointer";
    private static final String ERROR_SHORT_SQL_CONSTRAINT = "SQL Constraint Violation";
    private static final String ERROR_SHORT_NUMBER_FORMAT = "Invalid Number Format";
    private static final String ERROR_SHORT_MYBATIS = "MyBatis SelectOne Multiple Found";

    @Autowired
    private ActivityLogMapper activityLogMapper;

    @Autowired
    private Environment environment;


    @Autowired
    private MessageService messageService;

    @Autowired
    private TelegramHelper telegramUtils;

    @Autowired
    @Lazy
    private ActivityLogService activityLogService;

    @Value("${telegram.chat.id}")
    private String telegramChatId;

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void insert(String endpoint, Long line, String bug, String moduleName, String moduleId, String act,
                       int status, String description, LocalTime startDuration, LocalTime endDuration,
                       HttpServletRequest httpServletRequest) throws UnknownHostException {
        try {
            // Resolve caller userId from SecurityContext (no DB call — avoids aborting the transaction).
            Long userId = 1L;
            try {
                var currentUser = UserAuthSession.getCurrentUser();
                if (currentUser != null && currentUser.userId() != null && currentUser.userId() > 0) {
                    userId = currentUser.userId();
                }
            } catch (Exception ex) {
                LOGGER.debug("Could not resolve current user from security context: {}", ex.getMessage());
            }

            String ip = "127.0.0.1";
            String hostname = "localhost";
            try {
                InetAddress inetAddress = InetAddress.getLocalHost();
                ip = inetAddress.getHostAddress();
                hostname = inetAddress.getHostName();
            } catch (Exception ex) {
                LOGGER.debug("Could not resolve local host address, using defaults: {}", ex.getMessage());
            }

            String operatingSystem = System.getProperty("os.name").toLowerCase();
            String userAgent = httpServletRequest != null && httpServletRequest.getHeader("user-agent") != null ? httpServletRequest.getHeader("user-agent") : "";
            String browserName = detectBrowser(userAgent);

            Duration duration = Duration.between(startDuration, endDuration);
            long durationSeconds = duration.toSeconds();

            Long moduleDbId = activityLogMapper.getModuleId(moduleName != null ? moduleName : "Unknown Module");
            String username = activityLogMapper.getFullName(userId);
            if (username == null || username.trim().isEmpty()) {
                username = "Guest";
            }

            String shortBug = bug;
            String cleanDescription = description;
            if (status == 2) {
                shortBug = ERROR_SHORT_UNKNOWN;
                cleanDescription = description != null ? description : "Error";
                if (bug != null) {
                    String bugLower = bug.toLowerCase();
                    if (bugLower.contains("badsqlgrammarexception") || bugLower.contains("sqlsyntaxerrorexception")) {
                        shortBug = ERROR_SHORT_SQL_GRAMMAR;
                        cleanDescription = "Invalid SQL Syntax";
                    } else if (bugLower.contains("nullpointerexception")) {
                        shortBug = ERROR_SHORT_NULL_POINTER;
                        cleanDescription = "Missing Object or Variable";
                    } else if (bugLower.contains("dataintegrityviolationexception")) {
                        shortBug = ERROR_SHORT_SQL_CONSTRAINT;
                        cleanDescription = "Missing Required SQL Value";
                    } else if (bugLower.contains("numberformatexception")) {
                        shortBug = ERROR_SHORT_NUMBER_FORMAT;
                        cleanDescription = "Wrong Numeric Data";
                    } else if (bugLower.contains("mybatissystemexception")) {
                        shortBug = ERROR_SHORT_MYBATIS;
                        cleanDescription = "Expected Single Result";
                    } else {
                        shortBug = bug.length() > 80 ? bug.substring(0, 80) + "..." : bug;
                        cleanDescription = "Unexpected Error";
                    }
                }
            }

            String safeEndpoint = truncate(endpoint, MAX_ENDPOINT_LENGTH);

            ActivityLog activityLog = new ActivityLog();
            activityLog.setEndpoint(safeEndpoint);
            activityLog.setLine(line);
            activityLog.setModule(moduleName);
            activityLog.setModuleId(moduleDbId);
            activityLog.setAct(act);
            activityLog.setDescription(cleanDescription);
            activityLog.setBug(shortBug);
            activityLog.setBrowser(browserName);
            activityLog.setOperatingSystem(operatingSystem);
            activityLog.setIp(ip);
            activityLog.setDuration(durationSeconds);
            activityLog.setCreatedBy(userId);
            activityLog.setStatus(status);
            activityLog.setHostName(hostname);

            activityLogMapper.createActivityLog(activityLog);

            if (status == 2) {
                try {
                    sendTelegramAlert(activityLog, username, safeEndpoint, moduleId, cleanDescription, line, shortBug, durationSeconds, hostname);
                } catch (Exception telegramEx) {
                    // Telegram failures must not roll back the already-committed audit row.
                    LOGGER.warn("Telegram alert sending failed for endpoint {}: {}", safeEndpoint, telegramEx.getMessage());
                }
            }
        } catch (Exception ex) {
            // Never break business endpoints because audit logging failed.
            LOGGER.warn("Activity log insert failed for endpoint {}: {}", endpoint, ex.getMessage());
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String detectBrowser(String userAgent) {
        if (userAgent.contains("Chrome")) return "Chrome";
        if (userAgent.contains("Firefox")) return "Firefox";
        if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) return "Safari";
        if (userAgent.contains("Edge")) return "Edge";
        return "Unknown Browser";
    }

    private void sendTelegramAlert(ActivityLog activityLog, String username, String endpoint, String moduleId,
                                   String description, Long line, String bug, long durationSeconds, String hostname) {
        LocalDateTime now = LocalDateTime.now();
        String cleanDescription = description != null ? description : "Error";
        String fullBug = bug != null ? bug : ERROR_SHORT_UNKNOWN;
        long safeDuration = durationSeconds;
        long safeLine = line != null ? line : -1L;

        String message = String.format("""
                    <b>🚨 EMR API System Alert</b>
                    ━━━━━━━━━━━━━━━━━━━━
                    <b>Activity ID:</b> %s
                    <b>Server:</b> <u>%s</u>
                    <b>Swagger URL:</b> <a href="%s">%s</a>
                    <b>DateTime:</b> %s
                    <b>Hostname:</b> %s
                    <b>Endpoint:</b> <code>%s</code>
                    <b>Username:</b> %s
                    <b>Duration:</b> %d seconds
                    <b>Line Code:</b> %d
                    <b>Module:</b> %s
                    <b>Description:</b> %s
                    <b>Bug Message:</b> <i>%s</i>
                    ━━━━━━━━━━━━━━━━━━━━
                    """,
                activityLog.getId() != null ? activityLog.getId() : "-",
                environment.getProperty("server.from"),
                environment.getProperty("api.authUrl"),
                environment.getProperty("api.authUrl"),
                now,
                hostname,
                endpoint,
                username,
                safeDuration,
                safeLine,
                moduleId,
                cleanDescription,
                fullBug
        );

        telegramUtils.sendTextMessage(message, telegramChatId);
    }

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> listActivityLog(ActivityFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load activity log and return API response.
        LocalTime startDuration = LocalTime.now();
        try {
            ActivityFilter safeFilter = filter == null ? new ActivityFilter() : filter;
            Pagination pagination = PaginationHelper.buildAndApplyOffset(safeFilter, activityLogMapper.countList(safeFilter));

            List<ActivityLog> activityLogs = activityLogMapper.listActivityLog(safeFilter);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.SystemActivity.BASE_PATH + ApiConstants.SystemActivity.LIST_PATH, null, null, "System Activity", "System Activity (View)", "View",
                    1, "Success", startDuration, endDuration, httpServletRequest);

            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", activityLogs, pagination, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (long) error.getStackTrace()[0].getLineNumber();
            activityLogService.insert(ApiConstants.SystemActivity.BASE_PATH + ApiConstants.SystemActivity.LIST_PATH, errorLine, error.toString(), "System Activity",
                    "System Activity (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);

            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    /** {@inheritDoc} */
    @Override
    public ResponseMessage<BaseResult> getActivityLogById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        // Query flow: load activity log by id and return API response.
        LocalTime startDuration = LocalTime.now();
        try {
            List<ActivityLog> activityLogs = activityLogMapper.getActivityLogById(id);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.SystemActivity.BASE_PATH + ApiConstants.SystemActivity.FIND_PATH, null, null, "System Activity", "System Activity (View)", "View",
                    1, "Success", startDuration, endDuration, httpServletRequest);

            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", activityLogs, true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (long) error.getStackTrace()[0].getLineNumber();
            activityLogService.insert(ApiConstants.SystemActivity.BASE_PATH + ApiConstants.SystemActivity.FIND_PATH, errorLine, error.toString(), "System Activity",
                    "System Activity (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);

            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }
}

