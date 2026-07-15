package com.mattoid.scheduled.model;

import java.util.List;

/**
 * 依赖安装进度快照。
 * <p>用于持久化、查询安装过程中的阶段、进度、状态与日志，支持 SSE 重连与页面刷新后恢复。</p>
 */
public record InstallProgressSnapshot(
        String key,
        String phase,
        double percentage,
        String status,
        String message,
        boolean running,
        List<String> logs
) {

    public InstallProgressSnapshot {
        if (logs == null) {
            logs = List.of();
        }
    }
}
