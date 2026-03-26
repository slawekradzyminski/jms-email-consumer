package com.awesome.testing;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationYamlContractTest {

    @Test
    void declaresEnvironmentVariableOverridesWithDockerDefaults() throws IOException {
        String applicationYaml = new ClassPathResource("application.yml")
            .getContentAsString(StandardCharsets.UTF_8);

        assertThat(applicationYaml).contains("broker-url: ${SPRING_ACTIVEMQ_BROKER_URL:tcp://activemq:61616}");
        assertThat(applicationYaml).contains("user: ${SPRING_ACTIVEMQ_USER:admin}");
        assertThat(applicationYaml).contains("password: ${SPRING_ACTIVEMQ_PASSWORD:admin}");
        assertThat(applicationYaml).contains("host: ${SPRING_MAIL_HOST:mailhog}");
        assertThat(applicationYaml).contains("port: ${SPRING_MAIL_PORT:1025}");
        assertThat(applicationYaml).contains("destination: ${ACTIVEMQ_DESTINATION:email}");
        assertThat(applicationYaml).contains("from: ${EMAIL_FROM:awesome@testing.com}");
    }
}
