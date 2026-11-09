package com.awesome.testing.dto.email;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EmailTemplate {
    GENERIC,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_CONFIRMED,
    BACK_IN_STOCK,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    RETURN_UPDATED;

    /**
     * Maps template names this consumer does not know to {@link #GENERIC}, so a
     * newer producer cannot poison the queue: the email is still delivered with
     * the subject and message the producer rendered.
     */
    @JsonCreator
    public static EmailTemplate fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (EmailTemplate template : values()) {
            if (template.name().equals(value)) {
                return template;
            }
        }
        return GENERIC;
    }
}
