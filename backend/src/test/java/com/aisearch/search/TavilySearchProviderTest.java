package com.aisearch.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aisearch.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

class TavilySearchProviderTest {

    @Test
    void throwsWhenKeyMissing() {
        TavilySearchProvider p = new TavilySearchProvider(
                WebClient.builder(), new ObjectMapper(), "", "https://api.tavily.com");
        assertThrows(AppException.class, () -> p.search("test", 5));
    }
}
