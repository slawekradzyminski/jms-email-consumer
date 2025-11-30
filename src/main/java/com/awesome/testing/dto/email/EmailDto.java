package com.awesome.testing.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Collections;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailDto {

    private String to;
    private String subject;
    private String message;
    private EmailTemplate template;
    private Map<String, String> properties;

    public Map<String, String> getProperties() {
        return properties == null ? Collections.emptyMap() : properties;
    }
}
