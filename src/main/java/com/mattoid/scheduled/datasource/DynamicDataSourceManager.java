package com.mattoid.scheduled.datasource;

import com.mattoid.scheduled.common.StageResult;
import com.mattoid.scheduled.common.TestConnectionResult;
import com.mattoid.scheduled.entity.DatasourceConfig;
import com.mattoid.scheduled.util.CryptoUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DynamicDataSourceManager {

    private final Map<Long, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final SshTunnelManager sshTunnelManager;

    public DynamicDataSourceManager(SshTunnelManager sshTunnelManager) {
        this.sshTunnelManager = sshTunnelManager;
    }

    public DataSource getOrCreateDataSource(DatasourceConfig config) throws Exception {
        Long id = config.getId() != null ? config.getId() : Long.valueOf(-1);
        HikariDataSource ds = dataSources.get(id);
        if (ds != null && !ds.isClosed() && testConnection(ds)) {
            return ds;
        }

        synchronized (this) {
            ds = dataSources.get(id);
            if (ds != null && !ds.isClosed() && testConnection(ds)) {
                return ds;
            }
            closeDataSource(id);
            ds = buildDataSource(config, id);
            dataSources.put(id, ds);
            log.info("DataSource created for datasource {}", id);
            return ds;
        }
    }

    private HikariDataSource buildDataSource(DatasourceConfig config, Long id) throws Exception {
        String host = config.getHost();
        int port = config.getPort();

        if (Integer.valueOf(1).equals(config.getSshEnabled())) {
            SshTunnel tunnel = sshTunnelManager.createTunnel(config);
            host = tunnel.getLocalHost();
            port = tunnel.getLocalPort();
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(config.getDriverClass());
        hikariConfig.setJdbcUrl(buildJdbcUrl(config, host, port));
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(CryptoUtil.decryptIfNeeded(config.getPassword()));
        hikariConfig.setMaximumPoolSize(3);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setPoolName("ds-pool-" + id);

        return new HikariDataSource(hikariConfig);
    }

    public TestConnectionResult testDataSourceConnection(DatasourceConfig config) {
        List<StageResult> stages = new ArrayList<>();

        if (Integer.valueOf(1).equals(config.getSshEnabled())) {
            try {
                sshTunnelManager.testConnection(config);
                stages.add(new StageResult("SSH", true, null));
            } catch (Exception e) {
                log.error("测试 SSH 连接失败: {}", config.getName(), e);
                stages.add(new StageResult("SSH", false, e.getMessage()));
                return TestConnectionResult.fail("SSH", e.getMessage(), stages);
            }
        }

        try (HikariDataSource ds = buildDataSource(config, config.getId() != null ? config.getId() : Long.valueOf(-1))) {
            log.info("[测试连接] 数据库连接信息: host={}, port={}, username={}, password={}, jdbcUrl={}",
                    config.getHost(), config.getPort(), config.getUsername(),
                    CryptoUtil.decryptIfNeeded(config.getPassword()), ds.getJdbcUrl());
            try (Connection conn = ds.getConnection()) {
                if (conn.isValid(5)) {
                    stages.add(new StageResult("DATABASE", true, null));
                    return TestConnectionResult.ok(stages);
                }
                stages.add(new StageResult("DATABASE", false, "数据库连接无效"));
                return TestConnectionResult.fail("DATABASE", "数据库连接无效", stages);
            }
        } catch (Exception e) {
            log.error("测试数据库连接失败: {}", config.getName(), e);
            stages.add(new StageResult("DATABASE", false, e.getMessage()));
            return TestConnectionResult.fail("DATABASE", e.getMessage(), stages);
        }
    }

    public boolean testConnection(DatasourceConfig config) {
        return testDataSourceConnection(config).isSuccess();
    }

    public void closeDataSource(Long datasourceId) {
        HikariDataSource ds = dataSources.remove(datasourceId);
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
        sshTunnelManager.closeTunnel(datasourceId);
        log.info("DataSource closed for datasource {}", datasourceId);
    }

    public void refreshDataSource(DatasourceConfig config) throws Exception {
        closeDataSource(config.getId());
        getOrCreateDataSource(config);
    }

    private String buildJdbcUrl(DatasourceConfig config, String host, int port) {
        StringBuilder url = new StringBuilder();
        String dbType = config.getDbType().toLowerCase();
        switch (dbType) {
            case "mysql":
                url.append("jdbc:mysql://").append(host).append(":").append(port)
                   .append("/").append(config.getDatabaseName());
                break;
            case "postgresql":
                url.append("jdbc:postgresql://").append(host).append(":").append(port)
                   .append("/").append(config.getDatabaseName());
                break;
            case "oracle":
                url.append("jdbc:oracle:thin:@").append(host).append(":").append(port)
                   .append(":").append(config.getDatabaseName());
                break;
            case "sqlserver":
                url.append("jdbc:sqlserver://").append(host).append(":").append(port)
                   .append(";databaseName=").append(config.getDatabaseName());
                break;
            default:
                throw new IllegalArgumentException("Unsupported db type: " + config.getDbType());
        }
        if (config.getJdbcUrlParams() != null && !config.getJdbcUrlParams().isBlank()) {
            String sep;
            if ("sqlserver".equals(dbType)) {
                sep = ";";
            } else {
                sep = url.indexOf("?") < 0 ? "?" : "&";
            }
            url.append(sep).append(config.getJdbcUrlParams());
        }
        return url.toString();
    }

    private boolean testConnection(HikariDataSource ds) {
        try (Connection conn = ds.getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }
}
