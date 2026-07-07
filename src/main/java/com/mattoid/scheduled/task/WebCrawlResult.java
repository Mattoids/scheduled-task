package com.mattoid.scheduled.task;

import java.io.File;
import java.util.List;
import java.util.Map;

public record WebCrawlResult(
        String crawlName,
        String crawlCode,
        List<Map<String, Object>> data,
        List<File> mediaFiles,
        int totalRows,
        int downloadedMediaCount
) {
}
