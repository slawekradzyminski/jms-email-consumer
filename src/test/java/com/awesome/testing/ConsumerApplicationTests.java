package com.awesome.testing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.jms.listener.auto-startup=false")
class ConsumerApplicationTests {

	@Test
	void contextLoads() {
	}

}
