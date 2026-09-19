package com.example.humancode;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs as {@code dev} on purpose. The default configuration generates a problem
 * per session, and a @SpringBootTest publishes ApplicationReadyEvent — so
 * without this the build would warm ProblemPool, spend two model calls and add
 * a minute of latency every time anyone ran the tests.
 */
@SpringBootTest
@ActiveProfiles("dev")
class HumancodeApplicationTests {

	@Test
	void contextLoads() {
	}

}
