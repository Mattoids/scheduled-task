package com.mattoid.scheduled.datasource;

import com.mattoid.scheduled.entity.DatasourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DynamicDataSourceManagerTest {

    @Test
    void buildJdbcUrl_shouldEnableSslForMysqlWhenSshTunnelEnabled() {
        DynamicDataSourceManager manager = new DynamicDataSourceManager(null);
        DatasourceConfig config = new DatasourceConfig();
        config.setDbType("mysql");
        config.setDatabaseName("test_db");
        config.setSshEnabled(1);
        config.setJdbcUrlParams("useSSL=false&serverTimezone=Asia/Shanghai");

        String url = manager.buildJdbcUrl(config, "127.0.0.1", 3307);
        assertEquals("jdbc:mysql://127.0.0.1:3307/test_db?serverTimezone=Asia/Shanghai&sslMode=REQUIRED", url);
    }

    @Test
    void buildJdbcUrl_shouldKeepExistingSslModeForMysqlWhenSshTunnelEnabled() {
        DynamicDataSourceManager manager = new DynamicDataSourceManager(null);
        DatasourceConfig config = new DatasourceConfig();
        config.setDbType("mysql");
        config.setDatabaseName("test_db");
        config.setSshEnabled(1);
        config.setJdbcUrlParams("sslMode=VERIFY_IDENTITY");

        String url = manager.buildJdbcUrl(config, "127.0.0.1", 3307);
        assertEquals("jdbc:mysql://127.0.0.1:3307/test_db?sslMode=VERIFY_IDENTITY", url);
    }

    @Test
    void buildJdbcUrl_shouldNotForceSslForMysqlWithoutSshTunnel() {
        DynamicDataSourceManager manager = new DynamicDataSourceManager(null);
        DatasourceConfig config = new DatasourceConfig();
        config.setDbType("mysql");
        config.setDatabaseName("test_db");
        config.setSshEnabled(0);
        config.setJdbcUrlParams("useSSL=false");

        String url = manager.buildJdbcUrl(config, "localhost", 3306);
        assertEquals("jdbc:mysql://localhost:3306/test_db?useSSL=false", url);
    }

    @Test
    void buildJdbcUrl_shouldAppendParamsForPostgresql() {
        DynamicDataSourceManager manager = new DynamicDataSourceManager(null);
        DatasourceConfig config = new DatasourceConfig();
        config.setDbType("postgresql");
        config.setDatabaseName("test_db");
        config.setJdbcUrlParams("sslmode=require");

        String url = manager.buildJdbcUrl(config, "localhost", 5432);
        assertEquals("jdbc:postgresql://localhost:5432/test_db?sslmode=require", url);
    }
}
