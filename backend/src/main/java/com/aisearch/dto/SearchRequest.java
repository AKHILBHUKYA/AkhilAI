package com.aisearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SearchRequest {
    @NotBlank(message = "Query is required")
    @Size(min = 2, max = 1500, message = "Query must be between 2 and 1500 characters")
    private String query;

    private String mode = "balanced"; // fast | balanced | deep
}
