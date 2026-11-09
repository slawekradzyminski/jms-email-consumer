package com.awesome.testing.listener;

import com.awesome.testing.dto.email.EmailDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Delivers the emails the backend publishes. The backend renders every subject and body
 * (the same text its local outbox shows); the template only travels as metadata for logging.
 */
@Component
@Slf4j
@SuppressWarnings("unused")
@RequiredArgsConstructor
public class EmailConsumer {

    private final JavaMailSender javaMailSender;

    @Value("${email.from}")
    private String from;

    @JmsListener(destination = "${activemq.destination}", containerFactory = "jmsFactory")
    public void processToDo(EmailDto email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.getTo());
        message.setSubject(email.getSubject());
        message.setText(email.getMessage());
        javaMailSender.send(message);
        log.info("Sent {} email to {}", email.getTemplate(), email.getTo());
    }

}
