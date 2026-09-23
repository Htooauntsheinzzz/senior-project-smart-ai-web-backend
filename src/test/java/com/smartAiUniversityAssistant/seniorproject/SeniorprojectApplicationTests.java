package com.smartAiUniversityAssistant.seniorproject;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SeniorprojectApplicationTests extends IntegrationSupport {

	@Autowired
	private RedisConnectionFactory redisConnectionFactory;

	@Test
	void contextLoads() {
	}

	@Test
	void redisConnectionAuthenticates() {
		try (var connection = redisConnectionFactory.getConnection()) {
			assertThat(connection.ping()).isEqualTo("PONG");
		}
	}

}
