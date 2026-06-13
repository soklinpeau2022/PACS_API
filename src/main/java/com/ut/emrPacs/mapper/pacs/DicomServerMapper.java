package com.ut.emrPacs.mapper.pacs;

import com.ut.emrPacs.model.base.filter.DicomServerFilter;
import com.ut.emrPacs.model.components.pacs.dicom.HospitalDicomServer;
import com.ut.emrPacs.model.components.pacs.dicom.HospitalDicomMachine;
import com.ut.emrPacs.model.components.pacs.dicom.HospitalDicomRoutingConfig;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalDicomMachineListRequest;
import com.ut.emrPacs.model.dto.request.pacs.dicom.HospitalModalityServerRouteListRequest;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomMachineResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomServerResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalDicomRoutingConfigResponse;
import com.ut.emrPacs.model.dto.response.pacs.dicom.HospitalModalityServerRouteResponse;
import com.ut.emrPacs.model.dto.response.dropDown.DropDownModelResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistMachineRouteChoiceResponse;
import com.ut.emrPacs.model.dto.response.pacs.worklist.WorklistRouteServerOptionResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DicomServerMapper {
    Long countDicomServers(@Param("hospitalId") Long hospitalId, @Param("filter") DicomServerFilter filter);

    List<HospitalDicomServerResponse> listDicomServers(@Param("hospitalId") Long hospitalId, @Param("filter") DicomServerFilter filter);

    List<HospitalDicomServerResponse> getDicomServerById(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    Long countActiveDicomServerNameDuplicate(@Param("hospitalId") Long hospitalId, @Param("name") String name, @Param("excludeId") Long excludeId);

    Long countActiveDicomServerEndpointDuplicate(
            @Param("hospitalId") Long hospitalId,
            @Param("ipAddress") String ipAddress,
            @Param("port") Integer port,
            @Param("dicomPort") Integer dicomPort,
            @Param("aeTitle") String aeTitle,
            @Param("excludeId") Long excludeId
    );

    Boolean createDicomServer(HospitalDicomServer server);

    Boolean updateDicomServer(HospitalDicomServer server);

    Integer updateDicomServerHttpCredential(
            @Param("id") Long id,
            @Param("hospitalId") Long hospitalId,
            @Param("username") String username,
            @Param("password") String password,
            @Param("modifiedBy") Long modifiedBy
    );

    Integer updateDicomServerPacsResultApiKeyHash(
            @Param("id") Long id,
            @Param("hospitalId") Long hospitalId,
            @Param("apiKeyHash") String apiKeyHash,
            @Param("modifiedBy") Long modifiedBy
    );

    List<String> listActiveRouteUsageByDicomServerId(@Param("hospitalId") Long hospitalId, @Param("dicomServerId") Long dicomServerId);

    Boolean deleteDicomServer(@Param("id") Long id, @Param("hospitalId") Long hospitalId, @Param("modifiedBy") Long modifiedBy);

    Long countActiveDicomServersByHospital(@Param("hospitalId") Long hospitalId, @Param("serverIds") List<Long> serverIds);

    Long countDicomMachines(@Param("hospitalId") Long hospitalId, @Param("request") HospitalDicomMachineListRequest request);

    List<HospitalDicomMachineResponse> listDicomMachines(@Param("hospitalId") Long hospitalId, @Param("request") HospitalDicomMachineListRequest request);

    HospitalDicomMachineResponse getDicomMachineById(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    Long countActiveDicomMachineEndpointDuplicate(
            @Param("hospitalId") Long hospitalId,
            @Param("modalityId") Long modalityId,
            @Param("machineAeTitle") String machineAeTitle,
            @Param("machineHost") String machineHost,
            @Param("machinePort") Integer machinePort,
            @Param("excludeId") Long excludeId
    );

    Long countRoutesByMachineId(@Param("hospitalId") Long hospitalId, @Param("machineId") Long machineId);

    List<String> listActiveRouteUsageByMachineId(@Param("hospitalId") Long hospitalId, @Param("machineId") Long machineId);

    Boolean createDicomMachine(HospitalDicomMachine machine);

    Boolean updateDicomMachine(HospitalDicomMachine machine);

    Integer deleteDicomMachine(@Param("id") Long id, @Param("hospitalId") Long hospitalId, @Param("modifiedBy") Long modifiedBy);

    Long countRoutingRoutes(@Param("hospitalId") Long hospitalId, @Param("request") HospitalModalityServerRouteListRequest request);

    List<HospitalModalityServerRouteResponse> listRoutingRoutes(@Param("hospitalId") Long hospitalId, @Param("request") HospitalModalityServerRouteListRequest request);

    Long countRoutingConfigs(@Param("hospitalId") Long hospitalId, @Param("request") HospitalModalityServerRouteListRequest request);

    List<HospitalDicomRoutingConfigResponse> listRoutingConfigs(@Param("hospitalId") Long hospitalId, @Param("request") HospitalModalityServerRouteListRequest request);

    HospitalDicomRoutingConfigResponse getRoutingConfigById(@Param("id") Long id, @Param("hospitalId") Long hospitalId);

    Long countActiveRoutingConfigByHospitalAndServer(
            @Param("hospitalId") Long hospitalId,
            @Param("dicomServerId") Long dicomServerId,
            @Param("excludeId") Long excludeId
    );

    Long findActiveRoutingConfigIdByHospital(@Param("hospitalId") Long hospitalId);

    Boolean createRoutingConfig(HospitalDicomRoutingConfig config);

    Integer touchRoutingConfig(
            @Param("id") Long id,
            @Param("hospitalId") Long hospitalId,
            @Param("dicomServerId") Long dicomServerId,
            @Param("modifiedBy") Long modifiedBy
    );

    Integer deleteRoutingConfigById(@Param("id") Long id, @Param("hospitalId") Long hospitalId, @Param("modifiedBy") Long modifiedBy);

    Integer deactivateRoutesByRoutingConfigId(@Param("routingConfigId") Long routingConfigId, @Param("hospitalId") Long hospitalId, @Param("modifiedBy") Long modifiedBy);

    List<HospitalModalityServerRouteResponse> listRoutesByRoutingConfigIds(
            @Param("routingConfigIds") List<Long> routingConfigIds,
            @Param("hospitalId") Long hospitalId,
            @Param("modalityId") Long modalityId
    );

    List<HospitalModalityServerRouteResponse> listRoutesByHospitalAndModality(@Param("hospitalId") Long hospitalId, @Param("modalityId") Long modalityId);

    List<HospitalModalityServerRouteResponse> listActiveRoutesByHospitalAndModality(@Param("hospitalId") Long hospitalId, @Param("modalityId") Long modalityId);

    List<WorklistMachineRouteChoiceResponse> listActiveRouteChoicesByHospitalAndModality(@Param("hospitalId") Long hospitalId, @Param("modalityId") Long modalityId);

    List<WorklistRouteServerOptionResponse> listActiveRouteServerOptionsByHospitalAndModality(@Param("hospitalId") Long hospitalId, @Param("modalityId") Long modalityId);

    List<DropDownModelResponse> listActiveRoutedModalityOptionsByHospital(@Param("hospitalId") Long hospitalId);

    Integer upsertSingleRoute(
            @Param("routingConfigId") Long routingConfigId,
            @Param("hospitalId") Long hospitalId,
            @Param("modalityId") Long modalityId,
            @Param("machineId") Long machineId,
            @Param("createdBy") Long createdBy,
            @Param("modifiedBy") Long modifiedBy
    );

    HospitalDicomServerResponse findActiveDicomServerByWorklist(@Param("hospitalId") Long hospitalId, @Param("worklistId") Long worklistId);

    HospitalDicomServerResponse findActiveDicomServerByHospitalAndAeTitle(@Param("hospitalId") Long hospitalId, @Param("aeTitle") String aeTitle);

    List<HospitalDicomServerResponse> listActiveDicomServersByHttpUsername(@Param("username") String username);

    List<HospitalDicomServerResponse> listActiveDicomServersByHospital(@Param("hospitalId") Long hospitalId);

    List<HospitalDicomServerResponse> listActiveDicomServersForHealth(@Param("hospitalId") Long hospitalId);
}
