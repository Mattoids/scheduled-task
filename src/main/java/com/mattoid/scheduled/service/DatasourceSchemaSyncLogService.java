package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.AiKnowledgeDoc;
import com.mattoid.scheduled.entity.DatasourceConfig;
import com.mattoid.scheduled.entity.DatasourceSchemaSyncLog;
import com.mattoid.scheduled.mapper.DatasourceSchemaSyncLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
public class DatasourceSchemaSyncLogService extends ServiceImpl<DatasourceSchemaSyncLogMapper, DatasourceSchemaSyncLog> {

    /** 错误信息最大保留长度，避免异常堆栈把日志表撑爆。 */
    private static final int MAX_ERROR_LENGTH = 2000;

    private final DatasourceConfigService datasourceConfigService;
    private final TransactionTemplate transactionTemplate;

    public DatasourceSchemaSyncLogService(DatasourceConfigService datasourceConfigService,
                                          TransactionTemplate transactionTemplate) {
        this.datasourceConfigService = datasourceConfigService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 带日志的同步编排：先写一条 RUNNING 记录（独立事务），再执行真正的同步，
     * 结束后把结果（成功/失败、耗时、表数、文档）回写到该记录。
     * 本方法本身不加事务，确保日志记录与同步事务相互独立——即使同步失败回滚，
     * 失败日志依然能落库；即使前端因超时断开，服务端跑完后也能在记录里看到最终结果。
     */
    public AiKnowledgeDoc syncSchemaTracked(Long datasourceId) throws Exception {
        DatasourceConfig config = datasourceConfigService.getById(datasourceId);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在");
        }

        Long logId = start(config);
        long t0 = System.currentTimeMillis();
        try {
            DatasourceConfigService.SyncOutcome outcome = datasourceConfigService.syncSchema(datasourceId);
            long duration = System.currentTimeMillis() - t0;
            succeed(logId, outcome.tableCount(), outcome.doc(), duration);
            return outcome.doc();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - t0;
            fail(logId, duration, e.getMessage());
            throw e;
        }
    }

    private Long start(DatasourceConfig config) {
        return transactionTemplate.execute(status -> {
            DatasourceSchemaSyncLog logRow = new DatasourceSchemaSyncLog();
            logRow.setDatasourceId(config.getId());
            logRow.setDatasourceName(config.getName());
            logRow.setStatus(DatasourceSchemaSyncLog.STATUS_RUNNING);
            logRow.setStartTime(LocalDateTime.now());
            save(logRow);
            return logRow.getId();
        });
    }

    private void succeed(Long logId, int tableCount, AiKnowledgeDoc doc, long durationMs) {
        transactionTemplate.executeWithoutResult(status -> {
            DatasourceSchemaSyncLog update = new DatasourceSchemaSyncLog();
            update.setId(logId);
            update.setStatus(DatasourceSchemaSyncLog.STATUS_SUCCESS);
            update.setTableCount(tableCount);
            update.setDocId(doc != null ? doc.getId() : null);
            update.setDocTitle(doc != null ? doc.getTitle() : null);
            update.setDurationMs(durationMs);
            update.setEndTime(LocalDateTime.now());
            updateById(update);
        });
    }

    private void fail(Long logId, long durationMs, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            DatasourceSchemaSyncLog update = new DatasourceSchemaSyncLog();
            update.setId(logId);
            update.setStatus(DatasourceSchemaSyncLog.STATUS_FAIL);
            update.setErrorMessage(truncate(errorMessage));
            update.setDurationMs(durationMs);
            update.setEndTime(LocalDateTime.now());
            updateById(update);
        });
    }

    public Page<DatasourceSchemaSyncLog> pageByDatasource(Long datasourceId, long current, long size) {
        LambdaQueryWrapper<DatasourceSchemaSyncLog> wrapper = new LambdaQueryWrapper<DatasourceSchemaSyncLog>()
                .eq(DatasourceSchemaSyncLog::getDatasourceId, datasourceId)
                .orderByDesc(DatasourceSchemaSyncLog::getCreateTime);
        return page(new Page<>(current, size), wrapper);
    }

    /** 全量分页查询同步日志，可选按数据源过滤，按创建时间倒序。 */
    public Page<DatasourceSchemaSyncLog> pageAll(long current, long size, Long datasourceId) {
        LambdaQueryWrapper<DatasourceSchemaSyncLog> wrapper = new LambdaQueryWrapper<DatasourceSchemaSyncLog>()
                .eq(datasourceId != null, DatasourceSchemaSyncLog::getDatasourceId, datasourceId)
                .orderByDesc(DatasourceSchemaSyncLog::getCreateTime);
        return page(new Page<>(current, size), wrapper);
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH) + "...(truncated)";
    }
}
