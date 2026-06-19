package com.ut.emrPacs.controller;

import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistActionRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistDicomWorklistUpdateRequest;
import com.ut.emrPacs.service.service.WorklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.UnknownHostException;

@RestController
@RequestMapping("/worklists")
@Tag(
        name = "11. Worklist REST Controller",
        description = "REST aliases for single Worklist view, update, delete, and cancel actions."
)
public class WorklistRestController {

    @Autowired
    private WorklistService worklistService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @GetMapping("/{worklistId}")
    @Operation(summary = "View Worklist", description = "Module -> Worklist. Endpoint -> GET /worklists/{worklistId}.")
    public ResponseMessage<BaseResult> getWorklist(
            @PathVariable("worklistId") String worklistId,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return worklistService.getWorklist(resolveWorklistPathId(worklistId), httpServletRequest);
    }

    @PutMapping("/{worklistId}")
    @Operation(summary = "Update Worklist", description = "Module -> Worklist. Endpoint -> PUT /worklists/{worklistId}. WAITING updates EMR fields. IN_PROGRESS mirrors the update to the DicomServer worklist.")
    public ResponseMessage<BaseResult> updateWorklist(
            @PathVariable("worklistId") String worklistId,
            @Valid @RequestBody WorklistDicomWorklistUpdateRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return worklistService.updateWorklist(resolveWorklistPathId(worklistId), request, httpServletRequest);
    }

    @DeleteMapping("/{worklistId}")
    @Operation(summary = "Delete DicomServer Worklist", description = "Module -> Worklist. Endpoint -> DELETE /worklists/{worklistId}. Deletes the DicomServer worklist and marks the EMR Worklist cancelled.")
    public ResponseMessage<BaseResult> deleteWorklist(
            @PathVariable("worklistId") String worklistId,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return worklistService.deleteWorklist(resolveWorklistPathId(worklistId), httpServletRequest);
    }

    @PostMapping("/{worklistId}/cancel")
    @Operation(summary = "Cancel Worklist", description = "Module -> Worklist. Endpoint -> POST /worklists/{worklistId}/cancel. WAITING cancels locally. IN_PROGRESS deletes the DicomServer worklist first.")
    public ResponseMessage<BaseResult> cancelWorklist(
            @PathVariable("worklistId") String worklistId,
            @RequestBody(required = false) WorklistActionRequest request,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        WorklistActionRequest actionRequest = request == null ? new WorklistActionRequest() : request;
        actionRequest.setId(resolveWorklistPathId(worklistId));
        return worklistService.updateStatus(actionRequest, "CANCELLED", httpServletRequest);
    }

    private Long resolveWorklistPathId(String worklistId) {
        return publicEntityKeyResolver.resolveFromPath(Entity.WORKLIST, worklistId, "Worklist");
    }
}
