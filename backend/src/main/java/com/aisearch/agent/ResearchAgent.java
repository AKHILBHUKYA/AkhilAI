
package com.aisearch.agent;

import com.aisearch.dto.AgentEvent;
import com.aisearch.dto.SearchResult;
import com.aisearch.search.TavilySearchProvider;
import com.aisearch.web.WebPageReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ResearchAgent {

    private static final Logger log = LoggerFactory.getLogger(ResearchAgent.class);

    private final TavilySearchProvider searchProvider;
    private final WebPageReader pageReader;

    private final int qFast, sFast, pFast;
    private final int qBal, sBal, pBal;
    private final int qDeep, sDeep, pDeep;

    public ResearchAgent(
            TavilySearchProvider searchProvider,
            WebPageReader pageReader,
            @Value("${aisearch.modes.fast.queries:1}") int qFast,
            @Value("${aisearch.modes.fast.sources:5}") int sFast,
            @Value("${aisearch.modes.fast.pages:3}") int pFast,
            @Value("${aisearch.modes.balanced.queries:2}") int qBal,
            @Value("${aisearch.modes.balanced.sources:8}") int sBal,
            @Value("${aisearch.modes.balanced.pages:5}") int pBal,
            @Value("${aisearch.modes.deep.queries:4}") int qDeep,
            @Value("${aisearch.modes.deep.sources:12}") int sDeep,
            @Value("${aisearch.modes.deep.pages:8}") int pDeep) {

        this.searchProvider = searchProvider;
        this.pageReader = pageReader;

        this.qFast = qFast;
        this.sFast = sFast;
        this.pFast = pFast;

        this.qBal = qBal;
        this.sBal = sBal;
        this.pBal = pBal;

        this.qDeep = qDeep;
        this.sDeep = sDeep;
        this.pDeep = pDeep;
    }

    public Flux<AgentEvent> run(
            String query,
            String mode,
            AtomicBoolean cancelled) {

        Sinks.Many<AgentEvent> sink =
                Sinks.many().unicast().onBackpressureBuffer();

        Thread.ofVirtual().start(() -> {
            try {
                execute(query, mode, cancelled, sink);
            } catch (Exception e) {
                log.error("Research failed", e);

                emit(
                        sink,
                        AgentEvent.of(
                                "ERROR",
                                e.getMessage() != null
                                        ? e.getMessage()
                                        : "Research failed"
                        )
                );
            } finally {
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux();
    }

    private void execute(
            String query,
            String mode,
            AtomicBoolean cancelled,
            Sinks.Many<AgentEvent> sink) {

        if (query == null || query.isBlank()) {
            emit(
                    sink,
                    AgentEvent.of(
                            "ERROR",
                            "Search query cannot be empty."
                    )
            );

            emit(
                    sink,
                    AgentEvent.of(
                            "DONE",
                            "Finished with error"
                    )
            );

            return;
        }

        /*
         * Select search configuration based on mode.
         */
        int maxQ;
        int maxS;
        int maxP;

        switch (mode == null ? "balanced" : mode.toLowerCase()) {

            case "fast" -> {
                maxQ = qFast;
                maxS = sFast;
                maxP = pFast;
            }

            case "deep" -> {
                maxQ = qDeep;
                maxS = sDeep;
                maxP = pDeep;
            }

            default -> {
                maxQ = qBal;
                maxS = sBal;
                maxP = pBal;
            }
        }

        /*
         * Step 1: Understand the question.
         */
        emit(
                sink,
                AgentEvent.of(
                        "STATUS",
                        "Understanding your question..."
                )
        );

        if (cancelled.get()) {
            return;
        }

        /*
         * Step 2: No OpenAI query planning.
         *
         * Tavily receives the original user query directly.
         * This keeps the application completely free from OpenAI.
         */
        emit(
                sink,
                AgentEvent.of(
                        "STATUS",
                        "Preparing search..."
                )
        );

        List<String> queries = List.of(query.trim());

        for (String q : queries) {

            emit(
                    sink,
                    AgentEvent.builder()
                            .type("STATUS")
                            .message("Query: " + q)
                            .timestamp(Instant.now())
                            .build()
            );
        }

        if (cancelled.get()) {
            return;
        }

        /*
         * Step 3: Search Tavily.
         */
        emit(
                sink,
                AgentEvent.of(
                        "STATUS",
                        "Searching the public web..."
                )
        );

        Map<String, SearchResult> unique = new LinkedHashMap<>();

        for (String q : queries) {

            if (cancelled.get()) {
                break;
            }

            try {

                List<SearchResult> results =
                        searchProvider.search(q, maxS);

                if (results == null) {
                    continue;
                }

                for (SearchResult r : results) {

                    if (r == null || r.getUrl() == null) {
                        continue;
                    }

                    String key = normalize(r.getUrl());

                    if (key != null && !unique.containsKey(key)) {

                        unique.put(key, r);

                        emit(
                                sink,
                                AgentEvent.builder()
                                        .type("SOURCE")
                                        .title(r.getTitle())
                                        .url(r.getUrl())
                                        .domain(r.getDomain())
                                        .description(
                                                shorten(
                                                        r.getContent(),
                                                        180
                                                )
                                        )
                                        .faviconUrl(r.getFaviconUrl())
                                        .sourceCount(unique.size())
                                        .timestamp(Instant.now())
                                        .build()
                        );
                    }
                }

            } catch (Exception e) {

                log.warn(
                        "Search query failed: {}",
                        e.getMessage()
                );

                emit(
                        sink,
                        AgentEvent.of(
                                "STATUS",
                                "Search issue: " +
                                        (e.getMessage() != null
                                                ? e.getMessage()
                                                : "Unable to search")
                        )
                );
            }
        }

        if (cancelled.get()) {

            emit(
                    sink,
                    AgentEvent.of(
                            "DONE",
                            "Stopped"
                    )
            );

            return;
        }

        /*
         * Step 4: Rank Tavily results.
         */
        List<SearchResult> ranked =
                unique.values()
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        SearchResult::getScore,
                                        Comparator.nullsLast(
                                                Comparator.reverseOrder()
                                        )
                                )
                        )
                        .limit(maxS)
                        .toList();

        emit(
                sink,
                AgentEvent.of(
                        "STATUS",
                        ranked.size() +
                                " sources found. Reading pages..."
                )
        );

        /*
         * Step 5: Read webpages using Jina.
         */
        List<SearchResult> used = new ArrayList<>();

        StringBuilder context =
                new StringBuilder();

        int idx = 1;
        int pages = 0;

        for (SearchResult r : ranked) {

            if (cancelled.get() || pages >= maxP) {
                break;
            }

            emit(
                    sink,
                    AgentEvent.builder()
                            .type("READING")
                            .title(r.getTitle())
                            .url(r.getUrl())
                            .domain(r.getDomain())
                            .timestamp(Instant.now())
                            .build()
            );

            String content = null;

            try {
                content = pageReader.read(r.getUrl());
            } catch (Exception e) {
                log.debug(
                        "Could not read page {}: {}",
                        r.getUrl(),
                        e.getMessage()
                );
            }

            /*
             * If Jina cannot read the page,
             * use Tavily's returned content.
             */
            if (content == null || content.length() < 80) {

                content =
                        r.getContent() != null
                                ? r.getContent()
                                : "";
            }

            if (content.length() > 50) {

                used.add(r);

                context.append("[")
                        .append(idx)
                        .append("] ")
                        .append(
                                r.getTitle() != null
                                        ? r.getTitle()
                                        : "Source"
                        )
                        .append("\nURL: ")
                        .append(r.getUrl())
                        .append("\n")
                        .append(
                                shorten(
                                        content,
                                        3500
                                )
                        )
                        .append("\n\n");

                idx++;
                pages++;
            }
        }

        if (cancelled.get()) {

            emit(
                    sink,
                    AgentEvent.of(
                            "DONE",
                            "Stopped"
                    )
            );

            return;
        }

        /*
         * Step 6: Make sure we have usable sources.
         */
        if (used.isEmpty()) {

            emit(
                    sink,
                    AgentEvent.of(
                            "ERROR",
                            "No usable sources found. Try a different query."
                    )
            );

            emit(
                    sink,
                    AgentEvent.of(
                            "DONE",
                            "Finished with no sources"
                    )
            );

            return;
        }

        /*
         * Step 7: Build a Tavily-only answer.
         *
         * There is NO OpenAI call here.
         * The answer is created from the web-search results
         * and page content.
         */
        emit(
                sink,
                AgentEvent.of(
                        "STATUS",
                        "Preparing search results..."
                )
        );

        StringBuilder answer =
                new StringBuilder();

        answer.append("Search results for: ")
                .append(query)
                .append("\n\n");

        for (int i = 0; i < used.size(); i++) {

            SearchResult r = used.get(i);

            answer.append("[")
                    .append(i + 1)
                    .append("] ");

            if (r.getTitle() != null &&
                    !r.getTitle().isBlank()) {

                answer.append(r.getTitle());
            } else {
                answer.append("Untitled source");
            }

            answer.append("\n");

            if (r.getContent() != null &&
                    !r.getContent().isBlank()) {

                answer.append(
                        shorten(
                                r.getContent(),
                                700
                        )
                );

                answer.append("\n");
            }

            answer.append("Source: ")
                    .append(r.getUrl())
                    .append("\n\n");
        }

        /*
         * Stream the answer to the existing frontend.
         */
        String finalAnswer = answer.toString();

        emit(
                sink,
                AgentEvent.builder()
                        .type("TOKEN")
                        .token(finalAnswer)
                        .timestamp(Instant.now())
                        .build()
        );

        /*
         * Step 8: Build source list.
         */
        List<Map<String, Object>> sourceList =
                new ArrayList<>();

        for (int i = 0; i < used.size(); i++) {

            SearchResult r = used.get(i);

            Map<String, Object> source =
                    new LinkedHashMap<>();

            source.put("number", i + 1);

            source.put(
                    "title",
                    r.getTitle() != null
                            ? r.getTitle()
                            : "Untitled"
            );

            source.put(
                    "url",
                    r.getUrl()
            );

            source.put(
                    "domain",
                    r.getDomain() != null
                            ? r.getDomain()
                            : ""
            );

            sourceList.add(source);
        }

        /*
         * Step 9: Send final DONE event.
         */
        emit(
                sink,
                AgentEvent.builder()
                        .type("DONE")
                        .message("Search complete")
                        .answer(finalAnswer)
                        .sources(sourceList)
                        .timestamp(Instant.now())
                        .build()
        );
    }

    private void emit(
            Sinks.Many<AgentEvent> sink,
            AgentEvent event) {

        sink.tryEmitNext(event);
    }

    private String normalize(String url) {

        if (url == null) {
            return null;
        }

        try {

            URI u = URI.create(url);

            String host =
                    u.getHost() != null
                            ? u.getHost()
                                    .toLowerCase()
                                    .replaceFirst(
                                            "^www\\.",
                                            ""
                                    )
                            : "";

            String path =
                    u.getPath() != null
                            ? u.getPath()
                                    .replaceAll(
                                            "/$",
                                            ""
                                    )
                            : "";

            return host + path;

        } catch (Exception e) {

            return url;
        }
    }

    private String shorten(
            String text,
            int max) {

        if (text == null) {
            return "";
        }

        return text.length() <= max
                ? text
                : text.substring(0, max) + "...";
    }
}

