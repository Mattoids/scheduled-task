package com.mattoid.scheduled.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebCrawlPreviewResult {

    private boolean success;
    private Integer statusCode;
    private String message;
    private String title;

    public static WebCrawlPreviewResult success(Integer statusCode, String message, String title) {
        return new WebCrawlPreviewResult(true, statusCode, message, title);
    }

    public static WebCrawlPreviewResult failure(String message) {
        return new WebCrawlPreviewResult(false, null, message, null);
    }
}
