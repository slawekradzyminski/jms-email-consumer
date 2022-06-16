package com.awesome.testing.listener;

import com.awesome.testing.dto.EmailDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@SuppressWarnings("unused")
@RequiredArgsConstructor
public class EmailConsumer {

    private final JavaMailSender javaMailSender;

    @Value("${email.from}")
    private String from;

    @JmsListener(destination = "${activemq.destination}", containerFactory = "jmsFactory")
    public void processToDo(EmailDTO email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.getTo());
        message.setSubject(email.getSubject());
        message.setText(email.getMessage());
        javaMailSender.send(message);
        log.info("Send message {}", email);
    }

}
