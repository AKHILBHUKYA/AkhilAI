package com.aisearch.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aisearch.dto.SearchResult;
import com.aisearch.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TavilySearchProvider {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchProvider.class);

    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final String apiKey;

    public TavilySearchProvider(
            WebClient.Builder builder,
            ObjectMapper mapper,
            @Value("${aisearch.tavily.api-key:}") String apiKey,
            @Value("${aisearch.tavily.base-url:https://api.tavily.com}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    public List<SearchResult> search(String query, int maxResults) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AppException("Tavily API key is not configured. Set TAVILY_API_KEY.");
        }

        try {
            Map<String, Object> body = Map.of(
                    "api_key", apiKey,
                    "query", query,
                    "max_results", Math.min(Math.max(maxResults, 1), 15),
                    "search_depth", "advanced",
                    "include_answer", false,
                    "include_raw_content", false
            );

            String json = webClient.post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(25))
                    .block();

            return parse(json);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new AppException("Invalid Tavily API key.");
            }
            if (e.getStatusCode().value() == 429) {
                throw new AppException("Search rate limit reached. Please wait and try again.");
            }
            log.error("Tavily error: {}", e.getMessage());
            throw new AppException("Web search failed. Please try again.");
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Search error: {}", e.getMessage());
            throw new AppException("Web search failed: " + e.getMessage());
        }
    }

    private List<SearchResult> parse(String json) throws Exception {
        List<SearchResult> results = new ArrayList<>();
        JsonNode root = mapper.readTree(json);
        JsonNode arr = root.get("results");
        if (arr == null || !arr.isArray()) return results;

        for (JsonNode n : arr) {
            String url = text(n, "url");
            if (url == null || url.isBlank()) continue;

            String domain = extractDomain(url);
            results.add(SearchResult.builder()
                    .title(text(n, "title"))
                    .url(url)
                    .content(text(n, "content"))
                    .domain(domain)
                    .score(n.has("score") ? n.get("score").asDouble() : 0.0)
                    .faviconUrl("https://www.google.com/s2/favicons?domain=" + domain + "&sz=32")
                    .build());
        }
        return results;
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v != null && !v.isNull()) ? v.asText() : null;
    }

    private String extractDomain(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : "";
        } catch (Exception e) {
            return "";
        }
    }
}
