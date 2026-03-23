package com.urlshortener.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AnalyticsResponse {
    private String originalUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Integer clickCount;
}
