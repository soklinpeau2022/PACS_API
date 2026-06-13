package com.ut.emrPacs.service.service;

import com.ut.emrPacs.model.dto.response.pacs.worklist.PacsResultSyncResponse;

import java.util.List;

public interface PacsResultSyncService {
    int autoSendWaitingWorklists();

    PacsResultSyncResponse syncPacsResultByAccessionNumber(String accessionNumber);
    PacsResultSyncResponse syncPacsResultByAccessionNumber(Long hospitalId, String accessionNumber);

    List<PacsResultSyncResponse> syncPendingPacsResults();
}
