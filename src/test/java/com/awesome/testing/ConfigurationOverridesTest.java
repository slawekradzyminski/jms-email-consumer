package com.awesome.testing;

import jakarta.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.jms.listener.auto-startup=false",
        "spring.activemq.broker-url=tcp://localhost:61616",
        "spring.activemq.user=localhost-user",
        "spring.activemq.password=localhost-password",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "activemq.destination=email-local",
        "email.from=local@testing.com"
    }
)
class ConfigurationOverridesTest {

    @Autowired
    private Environment environment;

    @Autowired
    private JavaMailSenderImpl mailSender;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Test
    void respectsOverriddenTransportConfiguration() {
        assertThat(environment.getProperty("spring.activemq.broker-url"))
            .isEqualTo("tcp://localhost:61616");
        assertThat(environment.getProperty("spring.activemq.user"))
            .isEqualTo("localhost-user");
        assertThat(environment.getProperty("spring.activemq.password"))
            .isEqualTo("localhost-password");
        assertThat(environment.getProperty("spring.mail.host"))
            .isEqualTo("localhost");
        assertThat(environment.getProperty("spring.mail.port"))
            .isEqualTo("2525");
        assertThat(environment.getProperty("activemq.destination"))
            .isEqualTo("email-local");
        assertThat(environment.getProperty("email.from"))
            .isEqualTo("local@testing.com");

        assertThat(mailSender.getHost()).isEqualTo("localhost");
        assertThat(mailSender.getPort()).isEqualTo(2525);
        assertThat(activeMqConnectionFactory().getBrokerURL())
            .isEqualTo("tcp://localhost:61616");
        assertThat(activeMqConnectionFactory().getUserName()).isEqualTo("localhost-user");
        assertThat(activeMqConnectionFactory().getPassword()).isEqualTo("localhost-password");
    }

    private ActiveMQConnectionFactory activeMqConnectionFactory() {
        return (ActiveMQConnectionFactory) ((CachingConnectionFactory) connectionFactory)
            .getTargetConnectionFactory();
    }
}
