package com.ut.emrPacs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Legacy context-load test; use explicit integration tests with controlled test profile/database.")
@SpringBootTest(classes = Application.class)
class PacsApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
