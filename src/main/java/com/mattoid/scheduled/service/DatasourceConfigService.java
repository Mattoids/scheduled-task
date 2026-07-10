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

@Slf4j
@Service
public class DatasourceConfigService extends ServiceImpl<DatasourceConfigMapper, DatasourceConfig> {

    private final DynamicDataSourceManager dataSourceManager;
    private final DatasourceSchemaService datasourceSchemaService;
    private final AiAssistantService aiAssistantService;
    private final AiKnowledgeDocService aiKnowledgeDocService;
    private final AiKnowledgeDocStorageService aiKnowledgeDocStorageService;

    public DatasourceConfigService(DynamicDataSourceManager dataSourceManager,
                                   DatasourceSchemaService datasourceSchemaService,
                                   AiAssistantService aiAssistantService,
                                   AiKnowledgeDocService aiKnowledgeDocService,
                                   AiKnowledgeDocStorageService aiKnowledgeDocStorageService) {
        this.dataSourceManager = dataSourceManager;
        this.datasourceSchemaService = datasourceSchemaService;
        this.aiAssistantService = aiAssistantService;
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
    public AiKnowledgeDoc syncSchema(Long datasourceId) throws Exception {
        DatasourceConfig config = getById(datasourceId);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在");
        }
        if (!StringUtils.hasText(config.getDriverClass())) {
            config.setDriverClass(resolveDriverClass(config.getDbType()));
        }

        String rawSchema = datasourceSchemaService.extractSchema(config);
        String docContent = aiAssistantService.generateSchemaDoc(rawSchema);
        if (!StringUtils.hasText(docContent) && StringUtils.hasText(rawSchema)) {
            docContent = rawSchema;
        }
        if (!StringUtils.hasText(docContent)) {
            throw new IllegalStateException("无法生成数据字典内容，请检查 AI 配置或数据源是否包含表结构");
        }

        String filePath = aiKnowledgeDocStorageService.save(datasourceId, "SCHEMA", docContent);

        AiKnowledgeDoc doc = new AiKnowledgeDoc();
        doc.setDatasourceId(datasourceId);
        doc.setDocType("SCHEMA");
        doc.setTitle(config.getName() + " 数据字典");
        doc.setFilePath(filePath);
        doc.setStatus(1);
        aiKnowledgeDocService.save(doc);
        return doc;
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
