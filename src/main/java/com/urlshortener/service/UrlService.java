package com.urlshortener.service;

import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.dto.UrlShortenRequest;
import com.urlshortener.dto.UrlShortenResponse;

public interface UrlService {
    UrlShortenResponse shortenUrl(UrlShortenRequest request);
    String getOriginalUrl(String shortCode);
    AnalyticsResponse getAnalytics(String shortCode);
}
