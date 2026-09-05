
package com.aisearch.llm;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class LLMService {

    /**
     * OpenAI has been removed.
     *
     * This service now works without any paid AI API.
     * Tavily is responsible for web search, while the
     * search results are returned directly to the frontend.
     */

    public LLMService() {
    }

    /**
     * Without OpenAI, query planning is not performed by an LLM.
     * We simply use the original user question as the search query.
     */
    public List<String> planQueries(String question, int max) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        return List.of(question.trim());
    }

    /**
     * Without OpenAI, the researched web content is returned directly.
     *
     * The existing SSE/streaming architecture is preserved by returning
     * the research context as a Flux.
     */
    public Flux<String> streamAnswer(String question, String context) {

        if (context == null || context.isBlank()) {
            return Flux.just(
                    "No search results were found for: " + question
            );
        }

        String answer = """
                Search results for: %s

                %s
                """.formatted(question, context);

        return Flux.just(answer);
    }
}

