package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.common.TestConnectionResult;
import com.mattoid.scheduled.datasource.DynamicDataSourceManager;
import com.mattoid.scheduled.entity.AiKnowledgeDoc;
import com.mattoid.scheduled.entity.DatasourceConfig;
import com.mattoid.scheduled.mapper.DatasourceConfigMapper;
import com.mattoid.scheduled.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.IOException;

@Slf4j
@Service
public class DatasourceConfigService extends ServiceImpl<DatasourceConfigMapper, DatasourceConfig> {

    private final DynamicDataSourceManager dataSourceManager;
    private final DatasourceSchemaService datasourceSchemaService;
    private final AiKnowledgeDocService aiKnowledgeDocService;
    private final AiKnowledgeDocStorageService aiKnowledgeDocStorageService;

    public DatasourceConfigService(DynamicDataSourceManager dataSourceManager,
                                   DatasourceSchemaService datasourceSchemaService,
                                   AiKnowledgeDocService aiKnowledgeDocService,
                                   AiKnowledgeDocStorageService aiKnowledgeDocStorageService) {
        this.dataSourceManager = dataSourceManager;
        this.datasourceSchemaService = datasourceSchemaService;
        this.aiKnowledgeDocService = aiKnowledgeDocService;
        this.aiKnowledgeDocStorageService = aiKnowledgeDocStorageService;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateDatasource(DatasourceConfig config) {
        DatasourceConfig existing = config.getId() != null ? getById(config.getId()) : null;
        if (existing != null && !StringUtils.hasText(config.getPassword()) && StringUtils.hasText(existing.getPassword())) {
            config.setPassword(existing.getPassword());
        }
        applySshAuthType(config, existing);
        if (StringUtils.hasText(config.getPassword()) && !config.getPassword().startsWith("ENC(")) {
            config.setPassword(CryptoUtil.encrypt(config.getPassword()));
        }
        if (StringUtils.hasText(config.getSshPassword()) && !config.getSshPassword().startsWith("ENC(")) {
            config.setSshPassword(CryptoUtil.encrypt(config.getSshPassword()));
        }
        if (StringUtils.hasText(config.getSshPrivateKey()) && !config.getSshPrivateKey().startsWith("ENC(")) {
            config.setSshPrivateKey(CryptoUtil.encrypt(config.getSshPrivateKey()));
        }
        if (StringUtils.hasText(config.getSshPassphrase()) && !config.getSshPassphrase().startsWith("ENC(")) {
            config.setSshPassphrase(CryptoUtil.encrypt(config.getSshPassphrase()));
        }
        if (!StringUtils.hasText(config.getDriverClass())) {
            config.setDriverClass(resolveDriverClass(config.getDbType()));
        }

        boolean result = saveOrUpdate(config);
        if (result && config.getStatus() != null && config.getStatus() == 1) {
            try {
                dataSourceManager.refreshDataSource(config);
            } catch (Exception e) {
                log.error("刷新数据源连接失败: {}", config.getId(), e);
            }
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean removeDatasource(Long id) {
        dataSourceManager.closeDataSource(id);
        return removeById(id);
    }

    public TestConnectionResult testConnection(Long id) {
        DatasourceConfig config = getById(id);
        if (config == null) {
            return TestConnectionResult.fail("数据源不存在");
        }
        return testConnection(config);
    }

    public TestConnectionResult testConnection(DatasourceConfig config) {
        if (config == null) {
            return TestConnectionResult.fail("配置不能为空");
        }
        if (!StringUtils.hasText(config.getDriverClass())) {
            config.setDriverClass(resolveDriverClass(config.getDbType()));
        }
        DatasourceConfig existing = config.getId() != null ? getById(config.getId()) : null;
        if (existing != null && !StringUtils.hasText(config.getPassword()) && StringUtils.hasText(existing.getPassword())) {
            config.setPassword(existing.getPassword());
        }
        applySshAuthType(config, existing);
        return dataSourceManager.testDataSourceConnection(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public SyncOutcome syncSchema(Long datasourceId) throws Exception {
        DatasourceConfig config = getById(datasourceId);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在");
        }
        if (!StringUtils.hasText(config.getDriverClass())) {
            config.setDriverClass(resolveDriverClass(config.getDbType()));
        }

        // 数据字典必须完整覆盖全库表结构：直接落库 extractSchema 的完整结果，
        // 不再经 AI「整理」改写——AI 受 maxTokens 限制且可能总结/丢表，会导致文档不全、无法据此检索数据。
        String rawSchema = datasourceSchemaService.extractSchema(config);
        int tableCount = parseTableCount(rawSchema);
        if (!StringUtils.hasText(rawSchema)) {
            throw new IllegalStateException("未能从数据源读取到任何表结构，请确认数据库包含表或账号权限足够");
        }
        String docContent = rawSchema;

        // 一个数据源只保留一条数据字典：已存在则覆盖原文件并更新记录，否则新建。
        AiKnowledgeDoc existing = aiKnowledgeDocService.getLatestByDatasource(datasourceId, "SCHEMA");
        String filePath;
        if (existing != null && StringUtils.hasText(existing.getFilePath())) {
            try {
                aiKnowledgeDocStorageService.writeToPath(existing.getFilePath(), docContent);
                filePath = existing.getFilePath();
            } catch (IOException e) {
                // 旧路径可能来自其他运行环境（例如 Linux 容器里的 /root/...），
                // 本机不可写时回退到默认存储目录重新生成，并用新路径更新记录完成自愈。
                log.warn("数据字典原路径不可写，回退到默认存储目录重新生成: {}", existing.getFilePath(), e);
                filePath = aiKnowledgeDocStorageService.save(datasourceId, "SCHEMA", docContent);
            }
        } else {
            filePath = aiKnowledgeDocStorageService.save(datasourceId, "SCHEMA", docContent);
        }

        AiKnowledgeDoc doc;
        if (existing != null) {
            doc = existing;
            doc.setTitle(config.getName() + " 数据字典");
            doc.setFilePath(filePath);
            doc.setStatus(1);
            aiKnowledgeDocService.updateById(doc);
        } else {
            doc = new AiKnowledgeDoc();
            doc.setDatasourceId(datasourceId);
            doc.setDocType("SCHEMA");
            doc.setTitle(config.getName() + " 数据字典");
            doc.setFilePath(filePath);
            doc.setStatus(1);
            aiKnowledgeDocService.save(doc);
        }
        return new SyncOutcome(doc, tableCount);
    }

    /** 从原始表结构文本中解析「表数量: N」，用于同步日志展示；解析失败返回 0。 */
    private int parseTableCount(String rawSchema) {
        if (!StringUtils.hasText(rawSchema)) {
            return 0;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("表数量:\\s*(\\d+)").matcher(rawSchema);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** 同步结果：生成的数据字典文档 + 同步到的表数量。 */
    public record SyncOutcome(AiKnowledgeDoc doc, int tableCount) {
    }

    private void applySshAuthType(DatasourceConfig config, DatasourceConfig existing) {
        String authType = config.getSshAuthType();
        if (!StringUtils.hasText(authType) && existing != null) {
            authType = StringUtils.hasText(existing.getSshPrivateKey()) ? "key" : "password";
        }
        if (!"key".equals(authType)) {
            authType = "password";
        }
        if ("key".equals(authType)) {
            if (!StringUtils.hasText(config.getSshPrivateKey()) && existing != null
                    && StringUtils.hasText(existing.getSshPrivateKey())) {
                config.setSshPrivateKey(existing.getSshPrivateKey());
            }
            if (!StringUtils.hasText(config.getSshPassphrase()) && existing != null
                    && StringUtils.hasText(existing.getSshPassphrase())) {
                config.setSshPassphrase(existing.getSshPassphrase());
            }
            config.setSshPassword(null);
        } else {
            if (!StringUtils.hasText(config.getSshPassword()) && existing != null
                    && StringUtils.hasText(existing.getSshPassword())) {
                config.setSshPassword(existing.getSshPassword());
            }
            config.setSshPrivateKey(null);
            config.setSshPassphrase(null);
        }
    }

    public DataSource getDataSource(Long id) throws Exception {
        DatasourceConfig config = getById(id);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: " + id);
        }
        if (!StringUtils.hasText(config.getDriverClass())) {
            config.setDriverClass(resolveDriverClass(config.getDbType()));
        }
        return dataSourceManager.getOrCreateDataSource(config);
    }

    private String resolveDriverClass(String dbType) {
        return switch (dbType.toLowerCase()) {
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "postgresql" -> "org.postgresql.Driver";
            case "oracle" -> "oracle.jdbc.driver.OracleDriver";
            case "sqlserver" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default -> throw new IllegalArgumentException("未知数据库类型: " + dbType);
        };
    }
}
