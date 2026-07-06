package com.mattoid.scheduled.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class SchemaMigrationRunner implements BeanPostProcessor, Ordered {

    private static final String MIGRATION_LOCATION = "classpath:db/migration/V*__*.sql";
    private static final String VERSION_TABLE = "schema_version";
    private static final AtomicBoolean MIGRATED = new AtomicBoolean(false);

    // MySQL error codes that are safe to ignore during idempotent DDL
    private static final int ER_DUP_FIELDNAME = 1060;
    private static final int ER_DUP_KEYNAME = 1061;
    private static final int ER_CANT_DROP_FIELD_OR_KEY = 1091;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource && !MIGRATED.get()) {
            try {
                runMigrations(dataSource);
                MIGRATED.set(true);
            } catch (Exception e) {
                throw new RuntimeException("Schema migration failed", e);
            }
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private void runMigrations(DataSource dataSource) throws Exception {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(MIGRATION_LOCATION);
        } catch (Exception e) {
            log.info("No schema migration files found");
            return;
        }
        if (resources.length == 0) {
            log.info("No schema migration files found");
            return;
        }
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename));

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            ensureVersionTable(stmt);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || isApplied(conn, filename)) {
                    continue;
                }
                log.info("Applying schema migration: {}", filename);
                String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                List<String> statements = splitStatements(sql);
                for (String statement : statements) {
                    if (statement.isBlank()) {
                        continue;
                    }
                    executeStatement(stmt, statement);
                }
                markApplied(conn, filename);
                log.info("Schema migration applied: {}", filename);
            }
        }
    }

    private void executeStatement(Statement stmt, String statement) throws SQLException {
        try {
            stmt.execute(statement);
        } catch (SQLException e) {
            int errorCode = e.getErrorCode();
            if (errorCode == ER_DUP_FIELDNAME || errorCode == ER_DUP_KEYNAME || errorCode == ER_CANT_DROP_FIELD_OR_KEY) {
                log.warn("Ignoring idempotent DDL error: {}", e.getMessage());
                return;
            }
            throw e;
        }
    }

    private void ensureVersionTable(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS " + VERSION_TABLE + " (" +
                "filename VARCHAR(256) PRIMARY KEY, " +
                "applied_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")");
    }

    private boolean isApplied(Connection conn, String filename) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM " + VERSION_TABLE + " WHERE filename = ?")) {
            ps.setString(1, filename);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void markApplied(Connection conn, String filename) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO " + VERSION_TABLE + " (filename) VALUES (?)")) {
            ps.setString(1, filename);
            ps.executeUpdate();
        }
    }

    /**
     * 简单 SQL 拆分，支持 DELIMITER 指令。
     */
    private List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        String delimiter = ";";
        StringBuilder current = new StringBuilder();
        String[] lines = sql.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            if (trimmed.toUpperCase().startsWith("DELIMITER ")) {
                String candidate = current.toString().trim();
                if (!candidate.isEmpty()) {
                    statements.add(candidate);
                    current = new StringBuilder();
                }
                delimiter = trimmed.substring("DELIMITER ".length()).trim();
                continue;
            }
            current.append(line).append("\n");
            if (trimmed.endsWith(delimiter)) {
                String candidate = current.toString().trim();
                if (candidate.endsWith(delimiter)) {
                    candidate = candidate.substring(0, candidate.length() - delimiter.length()).trim();
                }
                if (!candidate.isEmpty()) {
                    statements.add(candidate);
                }
                current = new StringBuilder();
            }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) {
            statements.add(last);
        }
        return statements;
    }
}
