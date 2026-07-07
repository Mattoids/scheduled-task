package com.mattoid.scheduled.event;

import java.util.List;
import java.util.Map;

public interface InlineResult {

    String name();

    String code();

    List<Map<String, Object>> data();
}
