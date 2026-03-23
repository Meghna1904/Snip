package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UrlShortenRequest {
    @NotBlank(message = "Original URL is required")
    @Pattern(regexp = "^(https?://).*$", message = "URL must start with http or https")
    private String originalUrl;

    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Alias must be alphanumeric")
    private String customAlias;

    private LocalDateTime expiry;
}
