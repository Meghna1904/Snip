package com.urlshortener.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UrlShortenResponse {
    private String shortUrl;
    private LocalDateTime expiry;
    private LocalDateTime createdAt;
}
