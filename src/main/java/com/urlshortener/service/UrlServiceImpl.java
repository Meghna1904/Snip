package com.urlshortener.service;

import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.dto.UrlShortenRequest;
import com.urlshortener.dto.UrlShortenResponse;
import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.model.Url;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlServiceImpl implements UrlService {

    private final UrlRepository urlRepository;
    private final Base62Encoder base62Encoder;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.shortener.domain}")
    private String domain;

    private static final String CACHE_PREFIX = "url:";

    @Override
    @Transactional
    public UrlShortenResponse shortenUrl(UrlShortenRequest request) {
        if (request.getCustomAlias() != null && !request.getCustomAlias().isEmpty()) {
            urlRepository.findByShortCode(request.getCustomAlias()).ifPresent(url -> {
                throw new AliasAlreadyExistsException("Alias '" + request.getCustomAlias() + "' already exists");
            });
        }

        Url url = Url.builder()
                .originalUrl(request.getOriginalUrl())
                .shortCode("PENDING") // Placeholder, replaced below
                .createdAt(LocalDateTime.now())
                .expiresAt(request.getExpiry())
                .clickCount(0)
                .build();

        // First save to generate the ID
        url = urlRepository.save(url);

        if (request.getCustomAlias() != null && !request.getCustomAlias().isEmpty()) {
            url.setShortCode(request.getCustomAlias());
        } else {
            url.setShortCode(base62Encoder.encode(url.getId()));
        }

        // Save again with the shortCode
        urlRepository.save(url);
        cacheUrl(url);

        return UrlShortenResponse.builder()
                .shortUrl(domain + url.getShortCode())
                .createdAt(url.getCreatedAt())
                .expiry(url.getExpiresAt())
                .build();
    }

    @Override
    @Transactional
    public String getOriginalUrl(String shortCode) {
        String cachedUrl = redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);
        if (cachedUrl != null && !cachedUrl.isEmpty()) {
            incrementClick(shortCode);
            return cachedUrl;
        }

        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found"));

        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UrlNotFoundException("Short URL has expired");
        }

        cacheUrl(url);
        incrementClick(shortCode);

        return url.getOriginalUrl();
    }

    @Override
    public AnalyticsResponse getAnalytics(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found"));

        return AnalyticsResponse.builder()
                .originalUrl(url.getOriginalUrl())
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .clickCount(url.getClickCount())
                .build();
    }

    private void incrementClick(String shortCode) {
        urlRepository.findByShortCode(shortCode).ifPresent(url -> {
            url.setClickCount(url.getClickCount() + 1);
            urlRepository.save(url);
        });
    }

    private void cacheUrl(Url url) {
        if (url.getExpiresAt() != null) {
            long seconds = Duration.between(LocalDateTime.now(), url.getExpiresAt()).getSeconds();
            if (seconds > 0) {
                redisTemplate.opsForValue().set(CACHE_PREFIX + url.getShortCode(), url.getOriginalUrl(), seconds, TimeUnit.SECONDS);
            }
        } else {
            // Keep in cache for 7 days
            redisTemplate.opsForValue().set(CACHE_PREFIX + url.getShortCode(), url.getOriginalUrl(), 7, TimeUnit.DAYS);
        }
    }
}
