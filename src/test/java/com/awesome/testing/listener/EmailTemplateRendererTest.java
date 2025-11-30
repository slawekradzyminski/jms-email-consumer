package com.awesome.testing.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTest {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    @Test
    void shouldRenderPasswordResetRequestTemplate() {
        EmailDto dto = EmailDto.builder()
                .to("user@example.com")
                .template(EmailTemplate.PASSWORD_RESET_REQUESTED)
                .properties(Map.of(
                        "username", "client",
                        "resetLink", "http://localhost/reset?token=abc",
                        "expiresInMinutes", "45"
                ))
                .build();

        EmailTemplateRenderer.EmailContent content = renderer.render(dto);

        assertThat(content.subject()).contains("Password reset");
        assertThat(content.body()).contains("client")
                .contains("45 minutes")
                .contains("http://localhost/reset?token=abc");
    }

    @Test
    void shouldRenderPasswordResetConfirmationTemplate() {
        EmailDto dto = EmailDto.builder()
                .template(EmailTemplate.PASSWORD_RESET_CONFIRMED)
                .properties(Map.of("username", "client"))
                .build();

        EmailTemplateRenderer.EmailContent content = renderer.render(dto);

        assertThat(content.subject()).contains("password");
        assertThat(content.body()).contains("client");
    }

    @Test
    void shouldFallbackToPayloadWhenTemplateMissing() {
        EmailDto dto = EmailDto.builder()
                .subject("Subject")
                .message("Body")
                .build();

        EmailTemplateRenderer.EmailContent content = renderer.render(dto);

        assertThat(content.subject()).isEqualTo("Subject");
        assertThat(content.body()).isEqualTo("Body");
    }
}
