package com.ut.emrPacs.mapper.pacs;

import com.ut.emrPacs.model.dto.persistence.pacs.DicomServerCallbackLogEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DicomServerCallbackLogMapper {

    int insertCallbackLog(@Param("entry") DicomServerCallbackLogEntry entry);

    int insertUnmatchedCallbackLog(@Param("entry") DicomServerCallbackLogEntry entry);
}
