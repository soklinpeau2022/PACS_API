package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.mapper.notification.NotificationMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.NotificationFilter;
import com.ut.emrPacs.model.dto.response.notification.NotificationResponse;
import com.ut.emrPacs.service.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ut.emrPacs.helper.FunctionHelper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final int DEFAULT_NOTIFICATION_DAYS = 14;
    private static final int MAX_NOTIFICATION_DAYS = 90;
    private static final Set<String> ALLOWED_NOTIFICATION_SOURCES = Set.of("WORKLIST", "STUDY");

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private MessageService messageService;

    @Override
    public ResponseMessage<BaseResult> listNotifications(NotificationFilter filter, HttpServletRequest httpServletRequest) {
        try {
            NotificationFilter safeFilter = filter == null ? new NotificationFilter() : filter;
            Long hospitalId = currentHospitalId();
            safeFilter.setHospitalId(hospitalId);
            safeFilter.setDays(resolveNotificationDays(safeFilter.getDays()));
            boolean explicitSourceSelection = safeFilter.getSources() != null
                    || FunctionHelper.hasText(safeFilter.getSource());
            List<String> sources = normalizeSources(safeFilter);
            if (explicitSourceSelection && sources.isEmpty()) {
                Pagination pagination = PaginationHelper.buildAndApplyOffsetOrDefault(safeFilter);
                return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(), pagination, true));
            }
            safeFilter.setSources(sources.isEmpty() ? null : sources);
            safeFilter.setSource(null);

            Pagination pagination = PaginationHelper.buildAndApplyOffsetOrDefault(safeFilter);
            List<NotificationResponse> notifications = notificationMapper.listNotifications(safeFilter);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", notifications, pagination, true));
        } catch (Exception error) {
            LOGGER.error("Failed to list notifications", error);
            return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to load notifications.", null, false));
        }
    }

    private static Long currentHospitalId() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null || principal.hospitalId() == null || principal.hospitalId() <= 0) {
            throw new IllegalStateException("Hospital context not found in OAuth2 token claims.");
        }
        return principal.hospitalId();
    }

    private static int resolveNotificationDays(Integer requestedDays) {
        if (requestedDays == null || requestedDays <= 0) {
            return DEFAULT_NOTIFICATION_DAYS;
        }
        return Math.min(requestedDays, MAX_NOTIFICATION_DAYS);
    }

    private static List<String> normalizeSources(NotificationFilter filter) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (filter == null) {
            return List.of();
        }
        addNormalizedSource(normalized, filter.getSource());
        if (filter.getSources() != null) {
            filter.getSources().forEach(source -> addNormalizedSource(normalized, source));
        }
        return new ArrayList<>(normalized);
    }

    private static void addNormalizedSource(Set<String> normalized, String source) {
        if (source == null) {
            return;
        }
        String value = source.trim().toUpperCase(Locale.ROOT);
        if (ALLOWED_NOTIFICATION_SOURCES.contains(value)) {
            normalized.add(value);
        }
    }
}
