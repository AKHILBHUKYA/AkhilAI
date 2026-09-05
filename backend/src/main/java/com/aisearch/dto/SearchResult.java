package com.aisearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    private String title;
    private String url;
    private String content;
    private String domain;
    private Double score;
    private String faviconUrl;
}
