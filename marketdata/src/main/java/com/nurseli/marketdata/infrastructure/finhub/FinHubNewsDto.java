package com.nurseli.marketdata.infrastructure.finhub;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class FinHubNewsDto {

    @Data
    public static class NewsItem {
        private Long id;
        private String category;
        private String datetime;
        private String headline;
        private String summary;
        private String source;
        private String url;
        @JsonProperty("related")
        private String related;
    }
}