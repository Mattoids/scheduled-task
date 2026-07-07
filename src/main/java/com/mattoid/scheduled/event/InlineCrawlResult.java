package com.mattoid.scheduled.event;

import java.util.List;
import java.util.Map;

public record InlineCrawlResult(String crawlName, String crawlCode, List<Map<String, Object>> data)
        implements InlineResult {

    @Override
    public String name() {
        return crawlName;
    }

    @Override
    public String code() {
        return crawlCode;
    }
}
