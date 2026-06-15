package com.ut.emrPacs.mapper;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperXmlWellFormedTest {

    @Test
    void allMybatisMapperXmlFilesShouldBeWellFormed() throws Exception {
        Path mapperRoot = Path.of("src/main/resources/mybatis");
        List<String> errors = new ArrayList<>();

        try (var paths = Files.walk(mapperRoot)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".xml")).toList()) {
                try {
                    var factory = DocumentBuilderFactory.newInstance();
                    factory.setValidating(false);
                    var builder = factory.newDocumentBuilder();
                    builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
                    builder.parse(path.toFile());
                } catch (Exception error) {
                    errors.add(path + ": " + error.getMessage());
                }
            }
        }

        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }
}
