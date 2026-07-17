package com.mattoid.scheduled.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.datasource.SshHopConfig;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 旧版 ECB 密文迁移到新版 AES-GCM 的工具类。
 * <p>该类不直接操作数据库，仅提供单条值/JSON 的迁移逻辑。
 * 一次性全量迁移请使用 {@code crypto-migration} profile 启动应用，
 * 由 {@link com.mattoid.scheduled.migration.CryptoMigrationRunner} 执行。
 */
public final class CryptoMigrationUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CryptoMigrationUtil() {
    }

    /**
     * 判断值是否为旧版 ENC(...) 密文，需要迁移。
     */
    public static boolean needsMigration(String value) {
        return StringUtils.hasText(value) && value.startsWith(CryptoUtil.LEGACY_PREFIX) && value.endsWith(CryptoUtil.SUFFIX);
    }

    /**
     * 迁移单个值：旧版 ENC(...) 解密后再用 AES-GCM 加密。
     * 已经是 GCM(...) 或明文则原样返回。
     */
    public static String migrateValue(String value) {
        if (!needsMigration(value)) {
            return value;
        }
        String plain = CryptoUtil.decryptIfNeeded(value);
        return CryptoUtil.encrypt(plain);
    }

    /**
     * 迁移 notification_config.config_json 中的敏感字段。
     */
    public static String migrateNotificationConfigJson(String configType, String configJson) {
        if (!StringUtils.hasText(configJson) || !StringUtils.hasText(configType)) {
            return configJson;
        }
        try {
            Map<String, Object> map = MAPPER.readValue(configJson, new TypeReference<>() {
            });
            switch (configType) {
                case "EMAIL" -> migrateField(map, "password");
                case "WECOM_APP" -> {
                    migrateField(map, "secret");
                    migrateField(map, "adminCookie");
                }
                case "WECOM_INTELLIGENT_BOT" -> {
                    String mode = (String) map.get("mode");
                    if ("CALLBACK".equals(mode)) {
                        migrateField(map, "secret");
                    } else {
                        migrateField(map, "botSecret");
                    }
                }
                case "DINGTALK", "FEISHU" -> migrateField(map, "secret");
                case "SLACK" -> migrateField(map, "webhookUrl");
                case "WEBHOOK" -> {
                    migrateField(map, "url");
                    Object headersObj = map.get("headers");
                    if (headersObj instanceof Map<?, ?> headers) {
                        for (Map.Entry<?, ?> entry : headers.entrySet()) {
                            if (isSensitiveHeader(String.valueOf(entry.getKey())) && entry.getValue() instanceof String s) {
                                String migrated = migrateValue(s);
                                if (migrated != s) {
                                    ((Map<Object, Object>) headers).put(entry.getKey(), migrated);
                                }
                            }
                        }
                    }
                }
                default -> {
                    // 未知类型不处理
                }
            }
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("迁移通知配置 JSON 失败: " + configType, e);
        }
    }

    /**
     * 迁移 SSH 多跳节点配置中的敏感字段。
     */
    public static List<SshHopConfig> migrateSshHops(List<SshHopConfig> hops) {
        if (hops == null) {
            return null;
        }
        for (SshHopConfig hop : hops) {
            hop.setPassword(migrateValue(hop.getPassword()));
            hop.setPrivateKey(migrateValue(hop.getPrivateKey()));
            hop.setPassphrase(migrateValue(hop.getPassphrase()));
        }
        return hops;
    }

    private static void migrateField(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value instanceof String s) {
            String migrated = migrateValue(s);
            if (migrated != s) {
                map.put(field, migrated);
            }
        }
    }

    private static boolean isSensitiveHeader(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String lower = key.toLowerCase();
        return lower.contains("authorization")
                || lower.contains("token")
                || lower.contains("api-key")
                || lower.contains("apikey")
                || lower.contains("secret");
    }
}
