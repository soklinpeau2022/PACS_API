package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.StudyRetentionPolicyFilter;
import com.ut.emrPacs.model.base.filter.StudyRetentionReviewFilter;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionDecisionRequest;
import com.ut.emrPacs.model.dto.request.pacs.studyRetention.StudyRetentionPolicySaveRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.net.UnknownHostException;

public interface StudyRetentionService {
    ResponseMessage<BaseResult> listPolicies(StudyRetentionPolicyFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> savePolicy(StudyRetentionPolicySaveRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> deletePolicy(Long id, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> listReview(StudyRetentionReviewFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> summary(StudyRetentionReviewFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> approveDelete(Long studyId, StudyRetentionDecisionRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;

    ResponseMessage<BaseResult> rejectDelete(Long studyId, StudyRetentionDecisionRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException;
}
