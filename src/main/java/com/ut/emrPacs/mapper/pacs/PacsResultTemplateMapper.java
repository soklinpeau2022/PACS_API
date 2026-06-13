package com.ut.emrPacs.mapper.pacs;

import com.ut.emrPacs.model.base.filter.PacsResultTemplateFilter;
import com.ut.emrPacs.model.dto.request.pacs.result.PacsResultTemplateSaveRequest;
import com.ut.emrPacs.model.dto.response.pacs.result.PacsResultTemplateResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PacsResultTemplateMapper {
    Long countTemplates(PacsResultTemplateFilter filter);

    List<PacsResultTemplateResponse> listTemplates(PacsResultTemplateFilter filter);

    PacsResultTemplateResponse findTemplateById(@Param("id") Long id);

    Long countDuplicateTemplateName(
            @Param("hospitalId") Long hospitalId,
            @Param("modalityId") Long modalityId,
            @Param("templateName") String templateName,
            @Param("excludeId") Long excludeId
    );

    Boolean insertTemplate(
            @Param("request") PacsResultTemplateSaveRequest request,
            @Param("createdBy") Long createdBy
    );

    Boolean updateTemplate(
            @Param("request") PacsResultTemplateSaveRequest request,
            @Param("modifiedBy") Long modifiedBy
    );

    Boolean deactivateTemplate(
            @Param("id") Long id,
            @Param("modifiedBy") Long modifiedBy
    );
}
