package com.mattoid.scheduled.task;

public record WebCrawlPreviewJsonResult(
        boolean success,
        Integer statusCode,
        String message,
        String title,
        Object data
) {

    public static WebCrawlPreviewJsonResult failure(String message) {
        return new WebCrawlPreviewJsonResult(false, null, message, null, null);
    }

    public static WebCrawlPreviewJsonResult success(Integer statusCode, String message, String title, Object data) {
        return new WebCrawlPreviewJsonResult(true, statusCode, message, title, data);
    }
}
