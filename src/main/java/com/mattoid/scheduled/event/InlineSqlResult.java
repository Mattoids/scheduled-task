package com.mattoid.scheduled.event;

import java.util.List;
import java.util.Map;

/**
 * SQL 内联结果，用于直接将查询结果嵌入通知/邮件内容，不生成文件。
 */
public record InlineSqlResult(String sqlName, String sqlCode, List<Map<String, Object>> data) {
}
