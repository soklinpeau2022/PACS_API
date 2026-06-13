package com.ut.emrPacs.service.serviceImpl;

import com.ut.emrPacs.authentication.session.UserAuthSession;
import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.pagination.PaginationHelper;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.mapper.pacs.StudyMapper;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.MessageService;
import com.ut.emrPacs.model.base.Pagination;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.ResponseMessageUtils;
import com.ut.emrPacs.model.base.filter.StudyListFilter;
import com.ut.emrPacs.model.dto.response.pacs.study.StudyResponse;
import com.ut.emrPacs.service.service.ActivityLogService;
import com.ut.emrPacs.service.service.StudyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import static com.ut.emrPacs.authentication.util.AuthorityUtils.isAdminUser;

import java.net.UnknownHostException;
import java.time.LocalTime;
import java.util.List;

@Service
public class StudyServiceImpl implements StudyService {

    @Autowired
    private StudyMapper studyMapper;
    @Autowired
    private MessageService messageService;
    @Autowired
    private ActivityLogService activityLogService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @Override
    public ResponseMessage<BaseResult> list(StudyListFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            StudyListFilter safeFilter = filter != null ? filter : new StudyListFilter();
            Long requestedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, safeFilter.getHospitalKey(), null);
            Long hospitalId = resolveHospitalId(requestedHospitalId);
            Long modalityId = publicEntityKeyResolver.resolve(Entity.MODALITY, safeFilter.getModalityKey(), null);
            safeFilter.setHospitalId(hospitalId);
            safeFilter.setModalityId(modalityId);
            Pagination pagination = PaginationHelper.buildAndApplyOffset(safeFilter, studyMapper.count(hospitalId, safeFilter));

            List<StudyResponse> studies = studyMapper.list(hospitalId, safeFilter);

            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Study.BASE_PATH + ApiConstants.Study.LIST_PATH, null, null, "Study", "Study (List)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message(
                    "Success",
                    studies,
                    pagination,
                    true
            ));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Study.BASE_PATH + ApiConstants.Study.LIST_PATH, errorLine, error.toString(), "Study", "Study (List)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    @Override
    public ResponseMessage<BaseResult> findById(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException {
        LocalTime startDuration = LocalTime.now();
        try {
            var study = studyMapper.findById(currentHospitalId(), id);
            if (study == null) {
                return ResponseMessageUtils.makeResponse(false, messageService.message("Study not found.", false));
            }
            LocalTime endDuration = LocalTime.now();
            activityLogService.insert(ApiConstants.Study.BASE_PATH + ApiConstants.Study.FIND_PATH, null, null, "Study", "Study (View)", "View", 1, "Success", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(true, messageService.message("Success", List.of(study), true));
        } catch (Exception error) {
            LocalTime endDuration = LocalTime.now();
            Long errorLine = (error.getStackTrace() != null && error.getStackTrace().length > 0) ? (long) error.getStackTrace()[0].getLineNumber() : null;
            activityLogService.insert(ApiConstants.Study.BASE_PATH + ApiConstants.Study.FIND_PATH, errorLine, error.toString(), "Study", "Study (View)", "View", 2, "Error", startDuration, endDuration, httpServletRequest);
            return ResponseMessageUtils.makeResponse(false, messageService.message("An unexpected error occurred. Please try again.", null, false));
        }
    }

    private static Long currentHospitalId() {
        var principal = UserAuthSession.getCurrentUser();
        if (principal == null || principal.hospitalId() == null) {
            throw new IllegalStateException("Hospital context not found in OAuth2 token claims.");
        }
        return principal.hospitalId();
    }

    private static Long resolveHospitalId(Long requestedHospitalId) {
        if (requestedHospitalId != null && requestedHospitalId > 0) {
            return isAdminUser() ? requestedHospitalId : currentHospitalId();
        }
        return currentHospitalId();
    }

}

