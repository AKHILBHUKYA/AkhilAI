package com.aisearch.controller;

import com.aisearch.agent.ResearchAgent;
import com.aisearch.dto.AgentEvent;
import com.aisearch.dto.SearchRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final ResearchAgent agent;
    private final Map<String, AtomicBoolean> cancels = new ConcurrentHashMap<>();

    public SearchController(ResearchAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, String>> start(@Valid @RequestBody SearchRequest req) {
        String id = UUID.randomUUID().toString();
        cancels.put(id, new AtomicBoolean(false));
        log.info("Search started id={} mode={} q={}", id, req.getMode(), req.getQuery());
        return ResponseEntity.ok(Map.of(
                "id", id,
                "status", "STARTED",
                "mode", req.getMode() != null ? req.getMode() : "balanced"
        ));
    }

    @GetMapping(value = "/search/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AgentEvent> events(
            @PathVariable String id,
            @RequestParam String query,
            @RequestParam(defaultValue = "balanced") String mode) {

        AtomicBoolean flag = cancels.computeIfAbsent(id, k -> new AtomicBoolean(false));

        return agent.run(query, mode, flag)
                .doFinally(sig -> cancels.remove(id));
    }

    @PostMapping("/search/{id}/cancel")
    public ResponseEntity<Map<String, String>> cancel(@PathVariable String id) {
        AtomicBoolean flag = cancels.get(id);
        if (flag != null) {
            flag.set(true);
            return ResponseEntity.ok(Map.of("status", "CANCELLED", "id", id));
        }
        return ResponseEntity.ok(Map.of("status", "NOT_FOUND", "id", id));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health(
            @org.springframework.beans.factory.annotation.Value("${aisearch.tavily.api-key:}") String tavily,
            @org.springframework.beans.factory.annotation.Value("${aisearch.openai.api-key:}") String openai) {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "AI Search",
                "tavilyConfigured", tavily != null && !tavily.isBlank(),
                "openaiConfigured", openai != null && !openai.isBlank()
        ));
    }
}
