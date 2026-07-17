package com.mattoid.scheduled.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 统一 HTTP JSON 响应解析工具。
 * <p>
 * AI 客户端与通知客户端复用同一解析逻辑，避免各客户端重复创建 ObjectMapper 或
 * 手写异常处理，并统一对空响应、字段缺失、JSON 非法等场景的处理方式。
 */
@Slf4j
public final class JsonResponseParser {

    private static final int DEFAULT_TRUNCATE_LENGTH = 2000;

    private JsonResponseParser() {
    }

    /**
     * 解析 JSON 字符串为 {@link JSONObject}，解析失败时返回 null 并记录日志。
     */
    public static JSONObject parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            log.warn("Parse JSON response failed: {}", truncate(json), e);
            return null;
        }
    }

    /**
     * 按路径提取字符串，路径使用 {@code .} 分隔（如 {@code error.message}）。
     * 路径不存在时返回 null。
     */
    public static String pathString(JSONObject root, String path) {
        JSONObject node = navigate(root, path);
        if (node == null) {
            return null;
        }
        String lastKey = lastKey(path);
        return node.containsKey(lastKey) ? node.getString(lastKey) : null;
    }

    /**
     * 按路径提取整型，路径不存在时返回默认值。
     */
    public static int pathInt(JSONObject root, String path, int defaultValue) {
        JSONObject node = navigate(root, path);
        if (node == null) {
            return defaultValue;
        }
        String lastKey = lastKey(path);
        return node.containsKey(lastKey) ? node.getIntValue(lastKey, defaultValue) : defaultValue;
    }

    /**
     * 获取 JSON 数组，不存在或类型不符时返回 null。
     */
    public static JSONArray pathArray(JSONObject root, String path) {
        JSONObject node = navigate(root, path);
        if (node == null) {
            return null;
        }
        String lastKey = lastKey(path);
        if (!node.containsKey(lastKey)) {
            return null;
        }
        Object value = node.get(lastKey);
        return value instanceof JSONArray array ? array : null;
    }

    /**
     * 截断超长字符串，便于日志输出。
     */
    public static String truncate(String value) {
        return truncate(value, DEFAULT_TRUNCATE_LENGTH);
    }

    /**
     * 截断超长字符串，便于日志输出。
     */
    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...(truncated)";
    }

    private static JSONObject navigate(JSONObject root, String path) {
        if (root == null || !StringUtils.hasText(path)) {
            return root;
        }
        String[] segments = path.split("\\.");
        JSONObject current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            if (current == null || !current.containsKey(segments[i])) {
                return null;
            }
            Object next = current.get(segments[i]);
            if (!(next instanceof JSONObject)) {
                return null;
            }
            current = (JSONObject) next;
        }
        return current;
    }

    private static String lastKey(String path) {
        if (path == null) {
            return null;
        }
        int index = path.lastIndexOf('.');
        return index >= 0 ? path.substring(index + 1) : path;
    }
}
