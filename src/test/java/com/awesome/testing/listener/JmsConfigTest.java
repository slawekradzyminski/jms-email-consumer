package com.awesome.testing.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.awesome.testing.dto.email.EmailDto;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jms.autoconfigure.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.JacksonJsonMessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

class JmsConfigTest {

    private final JmsConfig config = new JmsConfig();

    @Test
    void shouldConfigureListenerFactoryWithApplicationMessageConverter() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        DefaultJmsListenerContainerFactoryConfigurer configurer =
                mock(DefaultJmsListenerContainerFactoryConfigurer.class);

        var result = config.jmsFactory(connectionFactory, configurer);

        assertThat(result).isInstanceOf(DefaultJmsListenerContainerFactory.class);
        DefaultJmsListenerContainerFactory factory = (DefaultJmsListenerContainerFactory) result;
        verify(configurer).configure(factory, connectionFactory);
        assertThat(ReflectionTestUtils.getField(factory, "messageConverter"))
                .isInstanceOf(JacksonJsonMessageConverter.class);
    }

    @Test
    void shouldSerializeEmailAsTextWithTheProducerTypeContract() throws Exception {
        MessageConverter converter = config.jacksonJmsMessageConverter();
        Session session = mock(Session.class);
        TextMessage textMessage = mock(TextMessage.class);
        when(session.createTextMessage(anyString())).thenReturn(textMessage);
        EmailDto email = EmailDto.builder()
                .to("recipient@example.com")
                .subject("Subject")
                .message("Body")
                .build();

        var result = converter.toMessage(email, session);

        assertThat(result).isSameAs(textMessage);
        verify(session).createTextMessage(anyString());
        verify(textMessage).setStringProperty(
                eq("_awesome_"),
                argThat(typeId -> typeId.equals("EmailDTO")
                        || typeId.equals("com.awesome.testing.dto.email.EmailDTO")));
    }
}
