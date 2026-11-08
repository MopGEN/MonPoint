package com.monpoint.reportes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"eureka.client.enabled=false",
		"spring.rabbitmq.listener.simple.auto-startup=false"
})
class MsReportesApplicationTests {

	@Test
	void contextLoads() {
	}

}
