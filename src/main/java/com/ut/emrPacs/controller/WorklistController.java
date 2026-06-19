package com.ut.emrPacs.controller;

import com.ut.emrPacs.config.ApiConstants;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver;
import com.ut.emrPacs.helper.security.PublicEntityKeyResolver.Entity;
import com.ut.emrPacs.model.base.BaseResult;
import com.ut.emrPacs.model.base.ResponseMessage;
import com.ut.emrPacs.model.base.filter.WorklistFilter;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistAssignRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistActionRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistFindRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistReceivedStudyRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistRouteAvailabilityRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistRoutedModalityListRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistSendToPacsRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistUpdateRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistViewStudyRequest;
import com.ut.emrPacs.model.dto.request.pacs.worklist.WorklistDicomWorklistUpdateRequest;
import io.swagger.v3.oas.annotations.Hidden;
import com.ut.emrPacs.service.service.WorklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.*;

import java.net.UnknownHostException;
import java.util.Map;

@RestController
@RequestMapping(ApiConstants.Worklist.BASE_PATH)
@Tag(
        name = "11. Worklist Controller",
        description = "Worklist workflow controller. Frontend should use Worklist.status as the business workflow state. Status set: WAITING, IN_PROGRESS, CANCELLED, FAILED."
)
public class WorklistController {

    @Autowired
    private WorklistService worklistService;
    @Autowired
    private PublicEntityKeyResolver publicEntityKeyResolver;

    @PostMapping(ApiConstants.Worklist.LIST_PATH)
    @Operation(summary = "List Worklists", description = "Module -> Worklist. Endpoint -> POST /worklist/worklist-list. Returns active Worklists only; rows with received study/image data are shown in the Study Archive instead.")
    public ResponseMessage<BaseResult> list(@Valid @RequestBody WorklistFilter filter, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.list(filter, httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.ASSIGN_PATH)
    @Operation(summary = "Assign patient to Worklist", description = "Module -> Worklist. Endpoint -> POST /worklist/worklist-assign")
    public ResponseMessage<BaseResult> assignWorklist(@Valid @RequestBody WorklistAssignRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.assignWorklist(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.FIND_PATH)
    @Operation(summary = "Find Worklist detail", description = "Module -> Worklist. Endpoint -> POST /worklist/worklist-find. Returns the current EMR Worklist state.")
    public ResponseMessage<BaseResult> findWorklistById(@Valid @RequestBody WorklistFindRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        Long worklistId = publicEntityKeyResolver.resolve(Entity.WORKLIST, request.getPublicKey(), null);
        Long hospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, request.getHospitalKey(), null);
        return worklistService.findWorklistById(worklistId, hospitalId, httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.UPDATE_PATH)
    @Operation(summary = "Update Worklist", description = "Module -> Worklist. Endpoint -> POST /worklist/worklist-update. WAITING and FAILED update EMR fields. IN_PROGRESS mirrors editable scheduling changes to DicomServer. CANCELLED is read-only.")
    public ResponseMessage<BaseResult> updateWorklist(@Valid @RequestBody WorklistUpdateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        Long worklistId = publicEntityKeyResolver.resolve(Entity.WORKLIST, request.getPublicKey(), null);
        request.setId(worklistId);
        return worklistService.updateWorklist(worklistId, request, httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.ROUTED_MODALITY_LIST_PATH)
    @Operation(summary = "List routed Worklist modalities", description = "Module -> Worklist. Endpoint -> POST /worklist/worklist-routed-modality-list. Returns only modalities that have an active DICOM route, active destination server, and active machine for the selected hospital.")
    public ResponseMessage<BaseResult> listRoutedModalities(@Valid @RequestBody WorklistRoutedModalityListRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.listRoutedModalities(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.ROUTE_AVAILABILITY_PATH)
    @Operation(summary = "Check active DICOM route availability", description = "Module -> Worklist. Endpoint -> POST /worklist/worklist-route-availability. Fast check for Worklist create/update screens; returns counts and distinct destination DICOM servers for the selected hospital and modality.")
    public ResponseMessage<BaseResult> checkRouteAvailability(@Valid @RequestBody WorklistRouteAvailabilityRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.checkRouteAvailability(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.SEND_TO_PACS_PATH)
    @Operation(summary = "Send Worklist to PACS", description = "Module -> Worklist. Endpoint -> POST /worklist/worklist-send-to-pacs. WAITING and FAILED create the DicomServer worklist and move the Worklist to IN_PROGRESS.")
    public ResponseMessage<BaseResult> sendToPacs(@Valid @RequestBody WorklistSendToPacsRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.sendToPacs(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.MACHINE_ROUTES_PATH)
    @Operation(summary = "List Send-to-PACS machine routes", description = "Module -> Worklist. Endpoint -> POST /worklist/worklist-machine-routes. Returns active modality machine routes for a Worklist so the frontend can choose the target machine before send-to-pacs.")
    public ResponseMessage<BaseResult> listMachineRoutesForSend(@Valid @RequestBody WorklistSendToPacsRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.listMachineRoutesForSend(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.SYNC_RESULT_PATH)
    @Operation(summary = "Sync PACS result", description = "Module -> Worklist. Endpoint -> POST /worklist/worklist-sync-result. Manual backup/admin action that looks up DicomServer by accession number. When image data exists, the row leaves the active Worklist list and is shown in the Study Archive.")
    public ResponseMessage<BaseResult> syncWorklistResult(@Valid @RequestBody WorklistActionRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.syncWorklistResult(request, httpServletRequest);
    }

    @GetMapping(ApiConstants.Worklist.WORKLIST_PATH)
    @Hidden
    @Operation(summary = "Get one DicomServer worklist and sync Worklist", description = "Module -> Worklist. Endpoint -> GET /worklist/{worklistId}/worklist. Reads DicomServer worklist and syncs Worklist fields back into EMR.")
    public ResponseMessage<BaseResult> getWorklist(@PathVariable("worklistId") String worklistId, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.getWorklist(publicEntityKeyResolver.resolveFromPath(Entity.WORKLIST, worklistId, "Worklist"), httpServletRequest);
    }

    @PutMapping(ApiConstants.Worklist.WORKLIST_PATH)
    @Hidden
    @Operation(summary = "Update DicomServer worklist from Worklist", description = "Module -> Worklist. Endpoint -> PUT /worklist/{worklistId}/worklist. Updates only editable DicomServer worklist fields.")
    public ResponseMessage<BaseResult> updateWorklist(@PathVariable("worklistId") String worklistId, @Valid @RequestBody WorklistDicomWorklistUpdateRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.updateWorklist(publicEntityKeyResolver.resolveFromPath(Entity.WORKLIST, worklistId, "Worklist"), request, httpServletRequest);
    }

    @DeleteMapping(ApiConstants.Worklist.WORKLIST_PATH)
    @Hidden
    @Operation(summary = "Delete DicomServer worklist", description = "Module -> Worklist. Endpoint -> DELETE /worklist/{worklistId}/worklist. Deletes the DicomServer worklist and keeps EMR Worklist history.")
    public ResponseMessage<BaseResult> deleteWorklist(@PathVariable("worklistId") String worklistId, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.deleteWorklist(publicEntityKeyResolver.resolveFromPath(Entity.WORKLIST, worklistId, "Worklist"), httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.RECEIVED_STUDY_PATH)
    @Operation(summary = "Receive DicomServer stable-study callback", description = "Module -> Worklist. Endpoint -> POST /worklist/worklist-received-study. Internal callback for DicomServer stable-study notifications. Requires a machine-client Bearer token, verifies image instances exist, and links the received study when a matching Worklist exists. Callbacks for direct Study uploads are acknowledged without creating or updating a Worklist.")
    public ResponseMessage<BaseResult> receivedStudy(@Valid @RequestBody WorklistReceivedStudyRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.receivedStudy(request, httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.VIEW_STUDY_PATH)
    @Operation(summary = "Open Worklist study viewer", description = "Module -> Worklist. Endpoint -> POST /worklist/worklist-view-study. Returns viewer metadata and preview image paths for the Worklist study.")
    public ResponseMessage<BaseResult> viewStudy(@Valid @RequestBody WorklistViewStudyRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.viewStudy(request, httpServletRequest);
    }

    @GetMapping(ApiConstants.Worklist.VIEWER_INFO_PATH)
    @Operation(summary = "Get secure viewer launch info", description = "Module -> Worklist. Endpoint -> GET /worklist/{worklistId}/viewer-info. Returns one lightweight viewer URL that prefers direct hospital DicomServer DICOMweb with a short-lived viewer token, with the signed API gateway retained as fallback.")
    public ResponseMessage<BaseResult> getViewerInfo(
            @PathVariable("worklistId") String worklistId,
            @RequestParam(value = "hospitalKey", required = false) String hospitalKey,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "viewerAccess", required = false) String viewerAccess,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        Long resolvedHospitalId = publicEntityKeyResolver.resolve(Entity.HOSPITAL, hospitalKey, null);
        return worklistService.getViewerInfo(publicEntityKeyResolver.resolveFromPath(Entity.WORKLIST, worklistId, "Worklist"), resolvedHospitalId, mode, viewerAccess, httpServletRequest);
    }

    @RequestMapping(value = ApiConstants.Worklist.VIEWER_DICOMWEB_PATH, method = {RequestMethod.GET, RequestMethod.HEAD})
    @Hidden
    @Operation(summary = "Proxy viewer DICOMweb", description = "Internal signed DICOMweb gateway used by OHIF viewer. The browser never receives DicomServer credentials.")
    public ResponseEntity<StreamingResponseBody> proxyViewerDicomWeb(
            @PathVariable("viewerToken") String viewerToken,
            @PathVariable("hospitalId") Long hospitalId,
            @PathVariable("worklistId") String worklistId,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return worklistService.proxyViewerDicomWeb(viewerToken, hospitalId, publicEntityKeyResolver.resolveFromPath(Entity.WORKLIST, worklistId, "Worklist"), httpServletRequest);
    }

    @RequestMapping(value = ApiConstants.Worklist.VIEWER_DICOMWEB_PROXY_PATH, method = {RequestMethod.GET, RequestMethod.HEAD})
    @Hidden
    @Operation(summary = "Proxy token-routed viewer DICOMweb", description = "Internal same-origin OHIF viewer gateway. The viewer nginx validates the token, then UDAYA_PACS_API routes the request to the correct hospital DicomServer server.")
    public ResponseEntity<StreamingResponseBody> proxyViewerDicomWeb(
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return worklistService.proxyViewerDicomWeb(httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.VIEWER_DICOMWEB_AUTHORIZE_PATH)
    @Hidden
    @Operation(summary = "Authorize direct DicomServer DICOMweb viewer request", description = "Internal DicomServer Authorization plugin callback. It validates the short-lived viewer token and grants only the bound study.")
    public ResponseEntity<Map<String, Object>> authorizeViewerDicomWeb(@RequestBody Map<String, Object> request) {
        return worklistService.authorizeViewerDicomWeb(request);
    }

    @GetMapping(ApiConstants.Worklist.VIEWER_DICOMWEB_PROXY_AUTHORIZE_PATH)
    @Hidden
    @Operation(summary = "Authorize viewer DICOMweb proxy request", description = "Internal OHIF nginx auth_request callback. It checks the short-lived viewer token before nginx proxies image traffic directly to DicomServer.")
    public ResponseEntity<Void> authorizeViewerDicomWebProxy(HttpServletRequest httpServletRequest) {
        return worklistService.authorizeViewerDicomWebProxy(httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.VIEWER_DICOMWEB_DECODE_PATH)
    @Hidden
    @Operation(summary = "Decode direct DicomServer DICOMweb viewer token", description = "Internal DicomServer Authorization plugin callback. It exposes only the study resource bound to the short-lived viewer token.")
    public ResponseEntity<Map<String, Object>> decodeViewerDicomWeb(@RequestBody Map<String, Object> request) {
        return worklistService.decodeViewerDicomWeb(request);
    }

    @PostMapping(ApiConstants.Worklist.VIEWER_DICOMWEB_PROFILE_PATH)
    @Hidden
    @Operation(summary = "Resolve direct DicomServer DICOMweb viewer profile", description = "Internal DicomServer Authorization plugin fallback callback. Browser Bearer tokens do not receive broad permissions; configured DicomServer HTTP credentials can receive server-side permissions.")
    public ResponseEntity<Map<String, Object>> profileViewerDicomWeb(@RequestBody(required = false) Map<String, Object> request) {
        return worklistService.profileViewerDicomWeb(request);
    }

    @PostMapping(ApiConstants.Worklist.VIEWER_DICOMWEB_RENEW_PATH)
    @Hidden
    @Operation(summary = "Renew viewer DICOMweb token", description = "Internal OHIF viewer heartbeat endpoint. It renews a scoped DICOMweb token while the viewer tab remains open.")
    public ResponseEntity<Map<String, Object>> renewViewerDicomWeb(@RequestBody(required = false) Map<String, Object> request) {
        return worklistService.renewViewerDicomWeb(request);
    }

    @PostMapping(ApiConstants.Worklist.VIEWER_DICOMWEB_REVOKE_PATH)
    @Hidden
    @Operation(summary = "Revoke viewer DICOMweb token", description = "Internal OHIF viewer lifecycle endpoint. It revokes the current scoped DICOMweb token when the viewer session is closed.")
    public ResponseEntity<Void> revokeViewerDicomWeb(@RequestBody(required = false) Map<String, Object> request) {
        return worklistService.revokeViewerDicomWeb(request);
    }

    @GetMapping(ApiConstants.Worklist.VIEW_STUDY_PREVIEW_PATH)
    @Operation(summary = "Load Worklist study preview image", description = "Module -> Worklist. Endpoint -> GET /worklist/worklist-view-study-preview/{worklistId}/{instanceId}. Proxies a preview image for the selected DicomServer instance.")
    public ResponseEntity<byte[]> viewStudyPreview(
            @PathVariable("worklistId") String worklistId,
            @PathVariable("instanceId") String instanceId,
            HttpServletRequest httpServletRequest
    ) throws UnknownHostException {
        return worklistService.viewStudyPreview(publicEntityKeyResolver.resolveFromPath(Entity.WORKLIST, worklistId, "Worklist"), instanceId, httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.RETURN_PATH)
    @Operation(summary = "Return Worklist (alias of cancel)", description = "Module -> Worklist. Endpoint -> POST /worklist/worklist-return")
    public ResponseMessage<BaseResult> returnWorklist(@Valid @RequestBody WorklistActionRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.updateStatus(request, "CANCELLED", httpServletRequest);
    }

    @PostMapping(ApiConstants.Worklist.CANCEL_PATH)
    @Operation(summary = "Cancel Worklist", description = "Module -> Worklist. Endpoint -> POST /worklist/worklist-cancel")
    public ResponseMessage<BaseResult> cancel(@Valid @RequestBody WorklistActionRequest request, HttpServletRequest httpServletRequest) throws UnknownHostException {
        return worklistService.updateStatus(request, "CANCELLED", httpServletRequest);
    }

}
