package com.awesome.testing.listener;

import com.awesome.testing.dto.email.EmailDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@SuppressWarnings("unused")
@RequiredArgsConstructor
public class EmailConsumer {

    private final JavaMailSender javaMailSender;
    private final EmailTemplateRenderer templateRenderer;

    @Value("${email.from}")
    private String from;

    @JmsListener(destination = "${activemq.destination}", containerFactory = "jmsFactory")
    public void processToDo(EmailDto email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.getTo());
        EmailTemplateRenderer.EmailContent content = templateRenderer.render(email);
        message.setSubject(content.subject());
        message.setText(content.body());
        javaMailSender.send(message);
        log.info("Sent {} email to {}", email.getTemplate(), email.getTo());
    }

}
