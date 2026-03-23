package com.urlshortener.service;

import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.dto.UrlShortenRequest;
import com.urlshortener.dto.UrlShortenResponse;
import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.model.Url;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.util.Base62Encoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceImplTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private Base62Encoder base62Encoder;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UrlServiceImpl urlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlService, "domain", "http://sho.rt/");
    }

    @Test
    void testShortenUrl_Success() {
        UrlShortenRequest request = new UrlShortenRequest();
        request.setOriginalUrl("https://example.com");

        Url mockUrl = Url.builder().id(1L).originalUrl("https://example.com").build();
        
        when(urlRepository.save(any(Url.class))).thenReturn(mockUrl);
        when(base62Encoder.encode(1L)).thenReturn("b");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        UrlShortenResponse response = urlService.shortenUrl(request);

        assertNotNull(response);
        assertEquals("http://sho.rt/b", response.getShortUrl());
        verify(urlRepository, times(2)).save(any(Url.class)); // 1 for ID, 1 for updating code
        verify(valueOperations).set(eq("url:b"), eq("https://example.com"), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    void testShortenUrl_CustomAlias() {
        UrlShortenRequest request = new UrlShortenRequest();
        request.setOriginalUrl("https://example.com");
        request.setCustomAlias("myalias");

        when(urlRepository.findByShortCode("myalias")).thenReturn(Optional.empty());
        when(urlRepository.save(any(Url.class))).thenAnswer(i -> i.getArguments()[0]);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        UrlShortenResponse response = urlService.shortenUrl(request);

        assertEquals("http://sho.rt/myalias", response.getShortUrl());
    }

    @Test
    void testShortenUrl_AliasExists() {
        UrlShortenRequest request = new UrlShortenRequest();
        request.setOriginalUrl("https://example.com");
        request.setCustomAlias("existing");

        when(urlRepository.findByShortCode("existing")).thenReturn(Optional.of(new Url()));

        assertThrows(AliasAlreadyExistsException.class, () -> urlService.shortenUrl(request));
    }

    @Test
    void testGetOriginalUrl_CacheHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:cachedcode")).thenReturn("https://cached.com");

        // mock increment DB search
        Url mockUrl = Url.builder().clickCount(0).build();
        when(urlRepository.findByShortCode("cachedcode")).thenReturn(Optional.of(mockUrl));

        String original = urlService.getOriginalUrl("cachedcode");

        assertEquals("https://cached.com", original);
        assertEquals(1, mockUrl.getClickCount());
        verify(urlRepository).save(mockUrl);
    }

    @Test
    void testGetOriginalUrl_CacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:dbcode")).thenReturn(null);

        Url mockUrl = Url.builder()
                .originalUrl("https://fromdb.com")
                .shortCode("dbcode")
                .clickCount(0)
                .build();

        when(urlRepository.findByShortCode("dbcode")).thenReturn(Optional.of(mockUrl));

        String original = urlService.getOriginalUrl("dbcode");

        assertEquals("https://fromdb.com", original);
        assertEquals(1, mockUrl.getClickCount());
        verify(urlRepository).save(mockUrl);
        verify(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void testGetAnalytics_Success() {
        Url mockUrl = Url.builder()
                .originalUrl("https://example.com")
                .createdAt(LocalDateTime.now())
                .clickCount(5)
                .build();

        when(urlRepository.findByShortCode("mycode")).thenReturn(Optional.of(mockUrl));

        AnalyticsResponse response = urlService.getAnalytics("mycode");

        assertNotNull(response);
        assertEquals("https://example.com", response.getOriginalUrl());
        assertEquals(5, response.getClickCount());
    }

    @Test
    void testGetAnalytics_NotFound() {
        when(urlRepository.findByShortCode("notfound")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class, () -> urlService.getAnalytics("notfound"));
    }

    @Test
    void testGetOriginalUrl_Expired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:expiredcode")).thenReturn(null);

        Url mockUrl = Url.builder()
                .originalUrl("https://expired.com")
                .expiresAt(LocalDateTime.now().minusDays(1)) // Expired yesterday
                .build();

        when(urlRepository.findByShortCode("expiredcode")).thenReturn(Optional.of(mockUrl));

        assertThrows(UrlNotFoundException.class, () -> urlService.getOriginalUrl("expiredcode"));
    }
}
