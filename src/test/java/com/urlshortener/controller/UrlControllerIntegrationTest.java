package com.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.dto.UrlShortenRequest;
import com.urlshortener.dto.UrlShortenResponse;
import com.urlshortener.service.RateLimiterService;
import com.urlshortener.service.UrlService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({UrlController.class, RedirectController.class})
class UrlControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UrlService urlService;

    @MockBean
    private RateLimiterService rateLimiterService;

    private Bucket unlimitedBucket;

    @BeforeEach
    void setUp() {
        // Create an unlimited bucket for testing
        Bandwidth limit = Bandwidth.builder().capacity(100).refillGreedy(100, Duration.ofMinutes(1)).build();
        unlimitedBucket = Bucket.builder().addLimit(limit).build();
        when(rateLimiterService.resolveBucket(anyString())).thenReturn(unlimitedBucket);
    }

    @Test
    void testShortenUrl_Success() throws Exception {
        UrlShortenRequest request = new UrlShortenRequest();
        request.setOriginalUrl("https://google.com");

        UrlShortenResponse response = UrlShortenResponse.builder()
                .shortUrl("http://localhost:8080/abc")
                .build();

        when(urlService.shortenUrl(any(UrlShortenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc"));
    }

    @Test
    void testShortenUrl_InvalidUrl() throws Exception {
        UrlShortenRequest request = new UrlShortenRequest();
        request.setOriginalUrl("invalid-url");

        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.originalUrl").exists());
    }

    @Test
    void testRedirect_Success() throws Exception {
        when(urlService.getOriginalUrl("abc")).thenReturn("https://google.com");

        mockMvc.perform(get("/abc"))
                .andExpect(status().isFound()) // 302
                .andExpect(header().string("Location", "https://google.com"));
    }

    @Test
    void testGetAnalytics_Success() throws Exception {
        AnalyticsResponse response = AnalyticsResponse.builder()
                .originalUrl("https://google.com")
                .createdAt(LocalDateTime.now())
                .clickCount(10)
                .build();

        when(urlService.getAnalytics("testcode")).thenReturn(response);

        mockMvc.perform(get("/api/analytics/testcode")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://google.com"))
                .andExpect(jsonPath("$.clickCount").value(10));
    }

    @Test
    void testGetAnalytics_NotFound() throws Exception {
        when(urlService.getAnalytics("notfound"))
            .thenThrow(new com.urlshortener.exception.UrlNotFoundException("Short URL not found"));

        mockMvc.perform(get("/api/analytics/notfound")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
