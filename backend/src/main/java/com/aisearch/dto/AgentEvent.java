package com.aisearch.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentEvent {
    private String type;
    private String message;
    private String title;
    private String url;
    private String domain;
    private String description;
    private String faviconUrl;
    private Integer sourceCount;
    private String token;
    private String answer;
    private List<Map<String, Object>> sources;
    private Instant timestamp;

    public static AgentEvent of(String type, String message) {
        return AgentEvent.builder()
                .type(type)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
