package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.cache.config.CacheConfig;
import com.ut.emrPacs.config.ApiConstants;
import static com.ut.emrPacs.helper.FunctionHelper.hasText;
import static com.ut.emrPacs.helper.FunctionHelper.trimToNull;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.modality.ModalityMapper;
import com.ut.emrPacs.mapper.pacs.PacsResultTemplateMapper;
import com.ut.emrPacs.mapper.user.UserMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.PacsResultTemplateFilter;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultTemplateSaveRequest;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultTemplateResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.PacsResultTemplateService;
import com.ut.emrPacs.service.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.UnknownHostException;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PacsResultTemplateServiceImpl implements PacsResultTemplateService {
    private static final String MODULE = "PACS Result Template";

    @Autowired
    private PacsResultTemplateMapper pacsResultTemplateMapper;
    @Autowired
    private ModalityMapper modalityMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;
    @Autowired
    private MessageService messageService;
    @Autowired
    private UserService userService;
    @Autowired
    private ActivityLogService activityLogService;

    @Override
    public ResponseMessage<BaseResult> listTemplates(PacsResultTemplateFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            PacsResultTemplateFilter safeFilter = filter == null ? new PacsResultTemplateFilter() : filter;
            resolveFilterKeys(safeFilter);
            Pagination pagination = PaginationHelper.buildAndApplyOffset(safeFilter, pacsResultTemplateMapper.countTemplates(safeFilter));
            List<PacsResultTemplateResponse> templates = pacsResultTemplateMapper.listTemplates(safeFilter);
            log(ApiConstants.PacsResultTemplate.LIST_PATH, null, null, "View", 1, "Success", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", templates, pagination, true));
        } catch (Exception error) {
            return handleError(ApiConstants.PacsResultTemplate.LIST_PATH, "View", startDuration, httpServletRequest, error);
        }
    }

    @Override
    public ResponseMessage<BaseResult> findTemplate(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (id == null || id <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Template not found.", false));
            }
            PacsResultTemplateResponse template = pacsResultTemplateMapper.findTemplateById(id);
            if (!belongsToActiveHospital(template)) {
                template = null;
            }
            log(ApiConstants.PacsResultTemplate.FIND_PATH, null, null, "View", 1, "Success", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", template == null ? List.of() : List.of(template), true));
        } catch (Exception error) {
            return handleError(ApiConstants.PacsResultTemplate.FIND_PATH, "View", startDuration, httpServletRequest, error);
        }
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PACS_RESULT_TEMPLATES_BY_SCOPE, allEntries = true)
    public ResponseMessage<BaseResult> createTemplate(PacsResultTemplateSaveRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            normalizeAndValidate(request, false);
            Long userId = userService.getUserAuth().getId();
            Boolean inserted = pacsResultTemplateMapper.insertTemplate(request, userId);
            if (!Boolean.TRUE.equals(inserted)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Unable to create template.", false));
            }
            PacsResultTemplateResponse template = pacsResultTemplateMapper.findTemplateById(request.getId());
            log(ApiConstants.PacsResultTemplate.CREATE_PATH, null, null, "Add", 1, "Success", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(template), true));
        } catch (IllegalArgumentException validation) {
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        } catch (Exception error) {
            return handleError(ApiConstants.PacsResultTemplate.CREATE_PATH, "Add", startDuration, httpServletRequest, error);
        }
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PACS_RESULT_TEMPLATES_BY_SCOPE, allEntries = true)
    public ResponseMessage<BaseResult> updateTemplate(PacsResultTemplateSaveRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            normalizeAndValidate(request, true);
            Long userId = userService.getUserAuth().getId();
            Boolean updated = pacsResultTemplateMapper.updateTemplate(request, userId);
            if (!Boolean.TRUE.equals(updated)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Template not found.", false));
            }
            PacsResultTemplateResponse template = pacsResultTemplateMapper.findTemplateById(request.getId());
            log(ApiConstants.PacsResultTemplate.UPDATE_PATH, null, null, "Edit", 1, "Success", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(template), true));
        } catch (IllegalArgumentException validation) {
            return ResponseMessageUtils.makeResponse(false, messageService.message(validation.getMessage(), false));
        } catch (Exception error) {
            return handleError(ApiConstants.PacsResultTemplate.UPDATE_PATH, "Edit", startDuration, httpServletRequest, error);
        }
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PACS_RESULT_TEMPLATES_BY_SCOPE, allEntries = true)
    public ResponseMessage<BaseResult> deleteTemplate(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            if (id == null || id <= 0) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Template not found.", false));
            }
            PacsResultTemplateResponse existing = pacsResultTemplateMapper.findTemplateById(id);
            if (!belongsToActiveHospital(existing)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Template not found.", false));
            }
            Long userId = userService.getUserAuth().getId();
            Boolean deleted = pacsResultTemplateMapper.deactivateTemplate(id, userId);
            if (!Boolean.TRUE.equals(deleted)) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Template not found.", false));
            }
            log(ApiConstants.PacsResultTemplate.DELETE_PATH, null, null, "Delete", 1, "Success", startDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", true));
        } catch (Exception error) {
            return handleError(ApiConstants.PacsResultTemplate.DELETE_PATH, "Delete", startDuration, httpServletRequest, error);
        }
    }

    private void resolveFilterKeys(PacsResultTemplateFilter filter) {
        filter.setHospitalId(resolveScopedHospitalId(filter.getHospitalKey(), filter.getHospitalId(), null));
        filter.setModalityId(publicEntityKeyResolver.resolve(Entity.MODALITY, filter.getModalityKey(), filter.getModalityId()));
    }

    private void normalizeAndValidate(PacsResultTemplateSaveRequest request, boolean update) {
        if (request == null) {
            throw new IllegalArgumentException("Template request is required.");
        }
        PacsResultTemplateResponse existing = null;
        if (update) {
            request.setId(publicEntityKeyResolver.resolve(Entity.PACS_RESULT_TEMPLATE, request.getPublicKey(), request.getId()));
            if (request.getId() == null || request.getId() <= 0) {
                throw new IllegalArgumentException("Template not found.");
            }
            existing = pacsResultTemplateMapper.findTemplateById(request.getId());
            if (existing == null) {
                throw new IllegalArgumentException("Template not found.");
            }
            if (!belongsToAllowedHospital(existing)) {
                throw new IllegalArgumentException("Template not found.");
            }
        } else {
            request.setId(null);
        }

        request.setHospitalId(resolveScopedHospitalId(
                request.getHospitalKey(),
                request.getHospitalId(),
                existing != null ? existing.getHospitalId() : null
        ));
        request.setModalityId(publicEntityKeyResolver.resolveRequired(Entity.MODALITY, request.getModalityKey(), request.getModalityId(), "Modality"));
        Long hospitalModality = modalityMapper.countActiveHospitalModality(request.getHospitalId(), request.getModalityId());
        if (hospitalModality == null || hospitalModality <= 0) {
            throw new IllegalArgumentException("Modality is not assigned to this hospital.");
        }

        String templateName = trimToNull(request.getTemplateName());
        if (!hasText(templateName)) {
            throw new IllegalArgumentException("Template name is required.");
        }
        if (templateName.length() > 180) {
            throw new IllegalArgumentException("Template name must be 180 characters or less.");
        }
        request.setTemplateName(templateName);

        String content = normalizeRichText(request.getTemplateContent());
        if (!hasMeaningfulRichText(content)) {
            throw new IllegalArgumentException("Template content is required.");
        }
        request.setTemplateContent(content);
        request.setIsActive(normalizeActiveFlag(request));

        Long duplicateCount = pacsResultTemplateMapper.countDuplicateTemplateName(
                request.getHospitalId(),
                request.getModalityId(),
                request.getTemplateName(),
                update ? request.getId() : null
        );
        if (duplicateCount != null && duplicateCount > 0) {
            throw new IllegalArgumentException("Duplicate template name for this hospital and modality.");
        }
    }

    private Long normalizeActiveFlag(PacsResultTemplateSaveRequest request) {
        if (request.getActive() != null) {
            return Boolean.TRUE.equals(request.getActive()) ? 1L : 2L;
        }
        Long isActive = request.getIsActive();
        if (isActive == null || isActive == 1L || isActive == 2L) {
            return isActive == null ? 1L : isActive;
        }
        return 1L;
    }

    private ResponseMessage<BaseResult> handleError(String endpointPath, String action, LocalTime startDuration, HttpServletRequest request, Exception error) throws UnknownHostException {
        Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
        log(endpointPath, errorLine, error.toString(), action, 2, "Error", startDuration, request);
        return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
    }

    private boolean belongsToActiveHospital(PacsResultTemplateResponse template) {
        return belongsToAllowedHospital(template);
    }

    private boolean belongsToAllowedHospital(PacsResultTemplateResponse template) {
        if (template == null) {
            return false;
        }
        return isCurrentUserHospital(template.getHospitalId());
    }

    private Long resolveScopedHospitalId(String hospitalKey, Long hospitalId, Long fallbackHospitalId) {
        Long resolvedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, hospitalKey, hospitalId);
        if (resolvedHospitalId == null || resolvedHospitalId <= 0) {
            resolvedHospitalId = fallbackHospitalId;
        }
        if (resolvedHospitalId == null || resolvedHospitalId <= 0) {
            resolvedHospitalId = getActiveHospitalId();
        }
        if (resolvedHospitalId == null || resolvedHospitalId <= 0) {
            resolvedHospitalId = findPrimaryHospitalId();
        }
        if (resolvedHospitalId == null || resolvedHospitalId <= 0) {
            throw new IllegalArgumentException("Hospital is required.");
        }
        if (!isCurrentUserHospital(resolvedHospitalId)) {
            throw new IllegalArgumentException("Hospital is not assigned to this user.");
        }
        return resolvedHospitalId;
    }

    private Long getActiveHospitalId() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null || principal.hospitalId() == null || principal.hospitalId() <= 0) {
            return null;
        }
        return principal.hospitalId();
    }

    private Long findPrimaryHospitalId() {
        Long userId = currentUserId();
        return userId == null ? null : userMapper.findPrimaryHospitalIdByUserId(userId);
    }

    private boolean isCurrentUserHospital(Long hospitalId) {
        if (hospitalId == null || hospitalId <= 0) {
            return false;
        }
        Long userId = currentUserId();
        if (userId == null || userId <= 0) {
            return false;
        }
        Long count = userMapper.countActiveUserInHospital(userId, hospitalId);
        return count != null && count > 0;
    }

    private Long currentUserId() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal != null && principal.userId() != null && principal.userId() > 0) {
            return principal.userId();
        }
        try {
            return userService.getUserAuth().getId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void log(String endpointPath, Long line, String bug, String action, int status, String description, LocalTime startDuration, HttpServletRequest request) throws UnknownHostException {
        activityLogService.insert(
                ApiConstants.PacsResultTemplate.BASE_PATH + endpointPath,
                line,
                bug,
                MODULE,
                MODULE + " (" + action + ")",
                action,
                status,
                description,
                startDuration,
                LocalTime.now(),
                request
        );
    }

    private static String normalizeRichText(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        trimmed = decodeBasicHtmlEntities(trimmed).trim();
        if (startsWithAllowedRichTextTag(trimmed)) {
            return sanitizeBasicRichText(trimmed);
        }
        return "<p>" + escapeBasicHtml(trimmed).replace("\n", "</p><p>") + "</p>";
    }

    private static String sanitizeBasicRichText(String html) {
        String withoutDangerousBlocks = html
                .replaceAll("(?is)<\\s*(script|style)[^>]*>.*?<\\s*/\\s*\\1\\s*>", "")
                .replaceAll("(?is)<\\s*(iframe|object|embed)[^>]*>.*?<\\s*/\\s*\\1\\s*>", "")
                .replaceAll("(?is)<!--.*?-->", "")
                .replaceAll("(?i)<\\s*(meta|link)[^>]*\\/?>", "");
        return stripRichTextTagAttributes(withoutDangerousBlocks)
                .replaceAll("(?i)<p>\\s*</p>", "")
                .trim();
    }

    private static String stripRichTextTagAttributes(String html) {
        Pattern tagPattern = Pattern.compile("<\\s*(/?)\\s*([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*>");
        Matcher matcher = tagPattern.matcher(html);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String slash = matcher.group(1) == null ? "" : matcher.group(1);
            String tag = matcher.group(2).toLowerCase(Locale.ROOT);
            String replacement = isAllowedRichTextTagName(tag) ? "<" + slash + tag + ">" : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean isAllowedRichTextTagName(String tag) {
        return switch (tag) {
            case "p", "div", "br", "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6",
                 "span", "strong", "b", "em", "i", "u", "table", "thead", "tbody", "tfoot",
                 "tr", "td", "th" -> true;
            default -> false;
        };
    }

    private static boolean startsWithAllowedRichTextTag(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.matches("^<\\s*(p|div|ul|ol|li|h[1-6]|span|strong|b|em|i|u|br|table|thead|tbody|tfoot|tr|td|th)\\b.*");
    }

    private static String decodeBasicHtmlEntities(String value) {
        String decoded = value;
        for (int i = 0; i < 5; i++) {
            String previous = decoded;
            decoded = decoded
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&#x27;", "'")
                    .replace("&amp;", "&");
            if (decoded.equals(previous)) {
                break;
            }
        }
        return decoded;
    }

    private static boolean hasMeaningfulRichText(String html) {
        if (!hasText(html)) {
            return false;
        }
        String text = html.replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .trim();
        return !text.isEmpty();
    }

    private static String escapeBasicHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

}
