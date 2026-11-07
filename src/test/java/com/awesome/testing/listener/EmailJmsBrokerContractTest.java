package com.awesome.testing.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import jakarta.jms.Connection;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumer half of the JMS email contract with test-secure-backend.
 *
 * <p>Publishes the producer's contract fixture ({@code src/test/resources/contracts/email-jms-contract.json}, kept
 * byte-identical with the copy in test-secure-backend, where {@code EmailJmsProducerContractTest} proves the backend
 * emits exactly this message) through a real Artemis broker using the backend's Artemis core JMS client, while the
 * application listens with its own OpenWire client, converter and {@code activemq.destination} default. A change to
 * the destination, the type header or the payload shape on this side therefore fails the test: the email is not sent,
 * or the {@link EmailDto} bound from the message differs from the producer's payload (fields the selected template does
 * not render, such as {@code subject} and {@code message}, are checked on the bound object).
 *
 * <p>{@code @DirtiesContext} closes the listener before Testcontainers stops the broker, so no cached context keeps
 * reconnecting to a dead container.
 */
@Testcontainers
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "management.health.mail.enabled=false")
class EmailJmsBrokerContractTest {

    /** Same broker image as awesome-localstack's docker-compose.yml. */
    private static final DockerImageName ARTEMIS_IMAGE = DockerImageName.parse("apache/artemis:2.55.0")
            .asCompatibleSubstituteFor("apache/activemq-artemis");

    @Container
    private static final ArtemisContainer ARTEMIS = new ArtemisContainer(ARTEMIS_IMAGE)
            .withUser("admin")
            .withPassword("admin");

    @MockitoBean
    private JavaMailSender javaMailSender;

    @MockitoSpyBean
    private EmailTemplateRenderer templateRenderer;

    @DynamicPropertySource
    static void brokerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.activemq.broker-url", ARTEMIS::getBrokerUrl);
        registry.add("spring.activemq.user", ARTEMIS::getUser);
        registry.add("spring.activemq.password", ARTEMIS::getPassword);
    }

    @Test
    void shouldSendEmailForTheEventPublishedByTheBackend() throws Exception {
        JsonNode contract = JsonMapper.builder().build().readTree(new ClassPathResource("contracts/email-jms-contract.json")
                .getContentAsString(StandardCharsets.UTF_8));
        JsonNode payload = contract.get("payload");

        publishAsBackend(contract);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender, timeout(15_000)).send(captor.capture());
        SimpleMailMessage mail = captor.getValue();
        JsonNode properties = payload.get("properties");

        ArgumentCaptor<EmailDto> boundEmail = ArgumentCaptor.forClass(EmailDto.class);
        verify(templateRenderer).render(boundEmail.capture());
        assertThat(boundEmail.getValue())
                .as("EmailDto bound by the listener from the producer payload")
                .usingRecursiveComparison()
                .isEqualTo(expectedEmail(payload));

        assertThat(mail.getTo()).containsExactly(payload.get("to").asString());
        assertThat(mail.getFrom()).isEqualTo("awesome@testing.com");
        assertThat(mail.getSubject()).isEqualTo("Password reset requested");
        assertThat(mail.getText())
                .contains("Hi " + properties.get("username").asString() + ",")
                .contains("within " + properties.get("expiresInMinutes").asString() + " minutes")
                .contains(properties.get("resetLink").asString());
    }

    private static EmailDto expectedEmail(JsonNode payload) {
        Map<String, String> properties = new LinkedHashMap<>();
        payload.get("properties").properties()
                .forEach(entry -> properties.put(entry.getKey(), entry.getValue().asString()));
        return EmailDto.builder()
                .to(payload.get("to").asString())
                .subject(payload.get("subject").asString())
                .message(payload.get("message").asString())
                .template(EmailTemplate.valueOf(payload.get("template").asString()))
                .properties(properties)
                .build();
    }

    private static void publishAsBackend(JsonNode contract) throws Exception {
        try (ActiveMQConnectionFactory factory =
                     new ActiveMQConnectionFactory(ARTEMIS.getBrokerUrl(), ARTEMIS.getUser(), ARTEMIS.getPassword());
             Connection connection = factory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageProducer producer = session.createProducer(session.createQueue(contract.get("destination").asString()));
            TextMessage message = session.createTextMessage(contract.get("payload").toString());
            message.setStringProperty(contract.get("typeIdProperty").asString(), contract.get("typeId").asString());
            producer.send(message);
        }
    }
}
