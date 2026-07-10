package com.mattoid.scheduled.service;

import com.mattoid.scheduled.datasource.DynamicDataSourceManager;
import com.mattoid.scheduled.entity.DatasourceConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DatasourceSchemaService {

    private final DynamicDataSourceManager dynamicDataSourceManager;

    public DatasourceSchemaService(DynamicDataSourceManager dynamicDataSourceManager) {
        this.dynamicDataSourceManager = dynamicDataSourceManager;
    }

    public String extractSchema(DatasourceConfig config) throws Exception {
        DataSource dataSource = dynamicDataSourceManager.getOrCreateDataSource(config);
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = resolveCatalog(config);
            String schema = resolveSchema(config);

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String remarks = rs.getString("REMARKS");
                    tables.add(tableName);
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("数据库: ").append(config.getDatabaseName()).append("\n");
            sb.append("类型: ").append(config.getDbType()).append("\n");
            sb.append("表数量: ").append(tables.size()).append("\n\n");

            for (String tableName : tables) {
                sb.append("表名: ").append(tableName).append("\n");
                try (ResultSet colRs = metaData.getColumns(catalog, schema, tableName, "%")) {
                    while (colRs.next()) {
                        String columnName = colRs.getString("COLUMN_NAME");
                        String typeName = colRs.getString("TYPE_NAME");
                        int columnSize = colRs.getInt("COLUMN_SIZE");
                        String nullable = colRs.getString("IS_NULLABLE");
                        String columnRemarks = colRs.getString("REMARKS");
                        sb.append("  - ").append(columnName)
                                .append(" ").append(typeName)
                                .append("(").append(columnSize).append(")")
                                .append(" nullable=").append(nullable);
                        if (columnRemarks != null && !columnRemarks.isBlank()) {
                            sb.append(" 备注:").append(columnRemarks);
                        }
                        sb.append("\n");
                    }
                }
                try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                    List<String> pks = new ArrayList<>();
                    while (pkRs.next()) {
                        pks.add(pkRs.getString("COLUMN_NAME"));
                    }
                    if (!pks.isEmpty()) {
                        sb.append("  主键: ").append(String.join(", ", pks)).append("\n");
                    }
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    private String resolveCatalog(DatasourceConfig config) {
        String dbType = config.getDbType().toLowerCase();
        if ("mysql".equals(dbType)) {
            return config.getDatabaseName();
        }
        return null;
    }

    private String resolveSchema(DatasourceConfig config) {
        String dbType = config.getDbType().toLowerCase();
        if ("postgresql".equals(dbType)) {
            return "public";
        }
        return null;
    }
}
