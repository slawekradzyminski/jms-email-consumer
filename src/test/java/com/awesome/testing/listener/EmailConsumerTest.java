package com.awesome.testing.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.awesome.testing.dto.email.EmailDto;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailConsumerTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private EmailTemplateRenderer templateRenderer;

    private EmailConsumer emailConsumer;

    @BeforeEach
    void setUp() {
        emailConsumer = new EmailConsumer(javaMailSender, templateRenderer);
        ReflectionTestUtils.setField(emailConsumer, "from", "noreply@test.com");
    }

    @Test
    void shouldRenderTemplateAndSendEmail() {
        EmailDto dto = EmailDto.builder()
                .to("user@example.com")
                .template(com.awesome.testing.dto.email.EmailTemplate.PASSWORD_RESET_REQUESTED)
                .properties(Map.of())
                .build();
        when(templateRenderer.render(dto))
                .thenReturn(new EmailTemplateRenderer.EmailContent("Subject", "Body"));

        emailConsumer.processToDo(dto);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getSubject()).isEqualTo("Subject");
        assertThat(message.getText()).isEqualTo("Body");
        assertThat(message.getFrom()).isEqualTo("noreply@test.com");
        assertThat(message.getTo()).containsExactly("user@example.com");
    }
}
