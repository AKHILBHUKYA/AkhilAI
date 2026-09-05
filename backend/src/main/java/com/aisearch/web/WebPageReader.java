package com.aisearch.web;

import com.aisearch.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class WebPageReader {

    private static final Logger log = LoggerFactory.getLogger(WebPageReader.class);
    private static final Pattern PRIVATE = Pattern.compile(
            "^(localhost|127\\.|10\\.|172\\.(1[6-9]|2\\d|3[01])\\.|192\\.168\\.|0\\.0\\.0\\.0|::1).*",
            Pattern.CASE_INSENSITIVE);

    private final WebClient client;
    private final String jinaBase;
    private final int timeoutSec;
    private final int maxSize;

    public WebPageReader(
            WebClient.Builder builder,
            @Value("${aisearch.jina.base-url:https://r.jina.ai}") String jinaBase,
            @Value("${aisearch.limits.page-timeout-seconds:15}") int timeoutSec,
            @Value("${aisearch.limits.max-page-size:1500000}") int maxSize) {
        this.client = builder.build();
        this.jinaBase = jinaBase;
        this.timeoutSec = timeoutSec;
        this.maxSize = maxSize;
    }

    public String read(String url) {
        validate(url);
        try {
            String readerUrl = jinaBase + "/" + url;
            String content = client.get()
                    .uri(readerUrl)
                    .header(HttpHeaders.ACCEPT, "text/plain")
                    .header("X-Return-Format", "markdown")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .onErrorResume(WebClientResponseException.class, e -> {
                        log.warn("Reader failed for {}: {}", url, e.getStatusCode());
                        return Mono.empty();
                    })
                    .block();

            if (content == null || content.isBlank()) {
                return null;
            }
            if (content.length() > maxSize) {
                content = content.substring(0, maxSize);
            }
            return content.replaceAll("\\n{3,}", "\n\n").trim();
        } catch (Exception e) {
            log.warn("Failed to read {}: {}", url, e.getMessage());
            return null;
        }
    }

    private void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new AppException("Empty URL");
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new AppException("Only http/https allowed");
            }
            String host = uri.getHost();
            if (host == null || PRIVATE.matcher(host).matches()) {
                throw new AppException("Private/internal hosts blocked");
            }
        } catch (IllegalArgumentException e) {
            throw new AppException("Invalid URL");
        }
    }
}
