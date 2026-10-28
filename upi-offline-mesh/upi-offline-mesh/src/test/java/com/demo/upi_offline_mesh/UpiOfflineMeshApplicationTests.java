package com.demo.upi_offline_mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UpiOfflineMeshApplicationTests {

	@Value("${server.port}")
	private String serverPort;

	@Test
	void contextLoads() {
	}

	@Test
	void defaultServerPortUsesAFreeLocalPort() {
		assertEquals("8081", serverPort);
	}

}
