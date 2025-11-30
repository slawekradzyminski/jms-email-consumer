package com.awesome.testing.listener;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EmailTemplateRenderer {

    private static final String PROP_USERNAME = "username";
    private static final String PROP_RESET_LINK = "resetLink";
    private static final String PROP_EXPIRES_MINUTES = "expiresInMinutes";

    public EmailContent render(EmailDto emailDto) {
        EmailTemplate template = emailDto.getTemplate();
        if (template == null || template == EmailTemplate.GENERIC) {
            return new EmailContent(emailDto.getSubject(), emailDto.getMessage());
        }

        Map<String, String> properties = emailDto.getProperties();
        return switch (template) {
            case PASSWORD_RESET_REQUESTED -> buildResetRequestEmail(properties, emailDto);
            case PASSWORD_RESET_CONFIRMED -> buildResetConfirmationEmail(properties, emailDto);
            default -> new EmailContent(emailDto.getSubject(), emailDto.getMessage());
        };
    }

    private EmailContent buildResetRequestEmail(Map<String, String> props, EmailDto fallback) {
        String username = props.getOrDefault(PROP_USERNAME, "there");
        String resetLink = props.getOrDefault(PROP_RESET_LINK, "your reset link");
        String expires = props.getOrDefault(PROP_EXPIRES_MINUTES, "30");
        String subject = "Password reset requested";
        String body = """
                Hi %s,

                We received a request to reset your password. Use the link below within %s minute%s:

                %s

                If you did not make this request you can ignore this email.
                """.formatted(username, expires, expires.equals("1") ? "" : "s", resetLink);
        return new EmailContent(subject, body);
    }

    private EmailContent buildResetConfirmationEmail(Map<String, String> props, EmailDto fallback) {
        String username = props.getOrDefault(PROP_USERNAME, "there");
        String subject = "Your password was changed";
        String body = """
                Hi %s,

                This is a confirmation that your password was just updated. If this wasn't you, request a new reset immediately.
                """.formatted(username);
        return new EmailContent(subject, body);
    }

    public record EmailContent(String subject, String body) {}
}
