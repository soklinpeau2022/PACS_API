package com.ut.emrPacs.mapper.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ut.emrPacs.model.auth.OAuth2ClientRow;

/**
 * MyBatis mapper for the oauth2_clients table.
 */
@Mapper
public interface OAuth2ClientMapper {

    /**
     * Find an OAuth2 client by its client_id.
     */
    OAuth2ClientRow findByClientId(@Param("clientId") String clientId);

    /**
     * True when the OAuth client is a generated active callback client for an
     * active DICOM server.
     */
    Boolean isActiveDicomServerCallbackClient(@Param("clientId") String clientId);

    /**
     * Create or rotate the generated callback client for one DICOM server.
     */
    Integer upsertDicomServerCallbackClient(
            @Param("dicomServerId") Long dicomServerId,
            @Param("clientId") String clientId,
            @Param("clientName") String clientName,
            @Param("clientSecretHash") String clientSecretHash
    );
}
