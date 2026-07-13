package com.mattoid.scheduled.service.wecom;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.WeComIpSyncLog;
import com.mattoid.scheduled.mapper.WeComIpSyncLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 企业微信可信 IP 同步日志服务。
 */
@Slf4j
@Service
public class WeComIpSyncLogService extends ServiceImpl<WeComIpSyncLogMapper, WeComIpSyncLog> {

    /** 结果描述最大保留长度，避免异常堆栈把日志表撑爆。 */
    private static final int MAX_MESSAGE_LENGTH = 1000;

    /** 失败日志去重窗口：同一配置、同一失败原因一小时内只落一条失败记录。 */
    private static final int FAIL_DEDUP_HOURS = 1;

    /**
     * 记录一条同步日志。日志写库失败不影响同步主流程。
     */
    public void record(WeComIpSyncLog logRow) {
        try {
            logRow.setMessage(truncate(logRow.getMessage()));
            save(logRow);
        } catch (Exception e) {
            log.warn("写入企业微信 IP 同步日志失败: configId={}", logRow.getConfigId(), e);
        }
    }

    /** 指定配置最近一小时内是否已有相同原因的失败记录（用于失败日志去重）。 */
    public boolean hasRecentFailure(Long configId, String failReason) {
        if (configId == null) {
            return false;
        }
        return lambdaQuery()
                .eq(WeComIpSyncLog::getConfigId, configId)
                .eq(WeComIpSyncLog::getStatus, WeComIpSyncLog.STATUS_FAIL)
                .eq(WeComIpSyncLog::getFailReason, failReason)
                .ge(WeComIpSyncLog::getCreateTime, LocalDateTime.now().minusHours(FAIL_DEDUP_HOURS))
                .count() > 0;
    }

    /** 分页查询同步日志，可选按通知配置、状态过滤，按创建时间倒序。 */
    public Page<WeComIpSyncLog> pageAll(long current, long size, Long configId, String status) {
        LambdaQueryWrapper<WeComIpSyncLog> wrapper = new LambdaQueryWrapper<WeComIpSyncLog>()
                .eq(configId != null, WeComIpSyncLog::getConfigId, configId)
                .eq(StringUtils.hasText(status), WeComIpSyncLog::getStatus, status)
                .orderByDesc(WeComIpSyncLog::getCreateTime);
        return page(new Page<>(current, size), wrapper);
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() <= MAX_MESSAGE_LENGTH ? value : value.substring(0, MAX_MESSAGE_LENGTH) + "...(truncated)";
    }
}
