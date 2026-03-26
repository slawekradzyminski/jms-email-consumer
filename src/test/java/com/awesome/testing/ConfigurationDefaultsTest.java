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
    properties = "spring.jms.listener.auto-startup=false"
)
class ConfigurationDefaultsTest {

    @Autowired
    private Environment environment;

    @Autowired
    private JavaMailSenderImpl mailSender;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Test
    void usesDockerFriendlyDefaults() {
        assertThat(environment.getProperty("spring.activemq.broker-url"))
            .isEqualTo("tcp://activemq:61616");
        assertThat(environment.getProperty("spring.activemq.user"))
            .isEqualTo("admin");
        assertThat(environment.getProperty("spring.activemq.password"))
            .isEqualTo("admin");
        assertThat(environment.getProperty("spring.mail.host"))
            .isEqualTo("mailhog");
        assertThat(environment.getProperty("spring.mail.port"))
            .isEqualTo("1025");
        assertThat(environment.getProperty("activemq.destination"))
            .isEqualTo("email");
        assertThat(environment.getProperty("email.from"))
            .isEqualTo("awesome@testing.com");

        assertThat(mailSender.getHost()).isEqualTo("mailhog");
        assertThat(mailSender.getPort()).isEqualTo(1025);
        assertThat(activeMqConnectionFactory().getBrokerURL())
            .isEqualTo("tcp://activemq:61616");
        assertThat(activeMqConnectionFactory().getUserName()).isEqualTo("admin");
        assertThat(activeMqConnectionFactory().getPassword()).isEqualTo("admin");
    }

    private ActiveMQConnectionFactory activeMqConnectionFactory() {
        return (ActiveMQConnectionFactory) ((CachingConnectionFactory) connectionFactory)
            .getTargetConnectionFactory();
    }
}
