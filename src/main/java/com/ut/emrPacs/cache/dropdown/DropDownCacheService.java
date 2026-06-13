package com.ut.emrPacs.cache.dropdown;

import com.ut.emrPacs.cache.config.CacheConfig;
import com.ut.emrPacs.mapper.dropDown.DropDownMapper;
import com.ut.emrPacs.model.base.filter.DropDownFilter;
import com.ut.emrPacs.model.base.filter.Filter;
import com.ut.emrPacs.model.dto.response.dropDown.DicomServerDropDownResponse;
import com.ut.emrPacs.model.dto.response.dropDown.DropDownModelResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DropDownCacheService {

    private final DropDownMapper dropDownMapper;

    public DropDownCacheService(DropDownMapper dropDownMapper) {
        this.dropDownMapper = dropDownMapper;
    }

    @Cacheable(cacheNames = CacheConfig.DROPDOWN_COUNTRIES, key = "T(com.ut.emrPacs.cache.dropdown.DropDownCacheService).filterKey(#filter)")
    public List<DropDownModelResponse> getListCountries(DropDownFilter filter) {
        return dropDownMapper.getListCountries(filter);
    }

    @Cacheable(cacheNames = CacheConfig.DROPDOWN_HOSPITALS_BY_USER, key = "#userId + '|' + T(com.ut.emrPacs.cache.dropdown.DropDownCacheService).filterKey(#filter)")
    public List<DropDownModelResponse> getListHospitalsByUserId(Long userId, DropDownFilter filter) {
        return dropDownMapper.getListHospitalsByUserId(userId, filter);
    }
    @Cacheable(cacheNames = CacheConfig.DROPDOWN_MODALITYS_BY_HOSPITAL, key = "#hospitalId + '|' + T(com.ut.emrPacs.cache.dropdown.DropDownCacheService).filterKey(#filter)")
    public List<DropDownModelResponse> getListModalitiesByHospitalId(Long hospitalId, DropDownFilter filter) {
        return dropDownMapper.getListModalitiesByHospitalId(hospitalId, filter);
    }

    @Cacheable(cacheNames = CacheConfig.DROPDOWN_MODALITY_CATALOG, key = "T(com.ut.emrPacs.cache.dropdown.DropDownCacheService).filterKey(#filter)")
    public List<DropDownModelResponse> getListModalityCatalog(DropDownFilter filter) {
        return dropDownMapper.getListModalityCatalog(filter);
    }

    @Cacheable(cacheNames = CacheConfig.DROPDOWN_DICOM_SERVERS_BY_HOSPITAL, key = "#hospitalId + '|' + T(com.ut.emrPacs.cache.dropdown.DropDownCacheService).filterKey(#filter)")
    public List<DicomServerDropDownResponse> getListDicomServersByHospitalId(Long hospitalId, DropDownFilter filter) {
        return dropDownMapper.getListDicomServersByHospitalId(hospitalId, filter);
    }

    @Cacheable(cacheNames = CacheConfig.DROPDOWN_USERS, key = "(#p0 == null ? 'ALL' : #p0) + '|' + T(com.ut.emrPacs.cache.dropdown.DropDownCacheService).filterKey(#p1)")
    public List<DropDownModelResponse> getListUsers(Long hospitalId, DropDownFilter filter) {
        return dropDownMapper.getListUsers(hospitalId, filter);
    }

    @Cacheable(cacheNames = CacheConfig.DROPDOWN_PATIENTS_BY_HOSPITAL, key = "#p0 + '|' + T(com.ut.emrPacs.cache.dropdown.DropDownCacheService).filterKey(#p1)")
    public List<DropDownModelResponse> getListPatientsByHospitalId(Long hospitalId, DropDownFilter filter) {
        return dropDownMapper.getListPatientsByHospitalId(hospitalId, filter);
    }

    @Cacheable(cacheNames = CacheConfig.DROPDOWN_USER_GROUPS, key = "(#p0 == null ? 'ALL' : #p0) + '|' + #p1 + '|' + #p2 + '|' + #p3 + '|' + T(com.ut.emrPacs.cache.dropdown.DropDownCacheService).filterKey(#p4)")
    public List<DropDownModelResponse> getListUserGroups(
            Long hospitalId,
            Long actorHospitalId,
            Boolean includeAllHospitals,
            Boolean includeCrossScope,
            DropDownFilter filter
    ) {
        return dropDownMapper.getListUserGroups(hospitalId, actorHospitalId, includeAllHospitals, includeCrossScope, filter);
    }
    public static String filterKey(Filter filter) {
        if (filter == null) {
            return "null";
        }
        String key = String.valueOf(filter.getPage()) + "|"
                + String.valueOf(filter.getRowsPerPage()) + "|"
                + String.valueOf(filter.getSearchText()) + "|"
                + String.valueOf(filter.getOrderBy());
        if (filter instanceof DropDownFilter dropDownFilter) {
            key += "|unusedRoutingOnly=" + String.valueOf(dropDownFilter.getUnusedRoutingOnly())
                    + "|includeDicomServerId=" + String.valueOf(dropDownFilter.getIncludeDicomServerId());
        }
        return key;
    }
}
