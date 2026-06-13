package com.ut.emrPacs.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserMapperXmlHardeningTest {

    @Test
    void shouldNotUseLegacyMysqlFunctionsInPostgresMapper() throws IOException {
        Path mapperPath = Path.of("src/main/resources/mybatis/postgresql/UserMapper.xml");
        String xml = Files.readString(mapperPath, StandardCharsets.UTF_8).toLowerCase();

        assertFalse(xml.contains("ifnull("), "UserMapper.xml must not use MySQL IFNULL in PostgreSQL mapper.");
        assertTrue(xml.contains("coalesce("), "UserMapper.xml should use PostgreSQL COALESCE for null-safe projection.");
    }
}
