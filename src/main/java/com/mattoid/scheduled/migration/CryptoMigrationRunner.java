package com.mattoid.scheduled.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 一次性加密迁移脚本入口。
 * <p>仅在激活 {@code crypto-migration} profile 时执行，例如：
 * <pre>java -jar scheduled-task.jar --spring.profiles.active=crypto-migration</pre>
 * 运行结束后进程自动退出。
 */
@Slf4j
@Component
@Profile("crypto-migration")
public class CryptoMigrationRunner implements CommandLineRunner {

    private final CryptoMigrationService migrationService;

    public CryptoMigrationRunner(CryptoMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(String... args) {
        log.info("[CryptoMigration] 开始将旧版 ECB 密文迁移到 AES-GCM...");
        migrationService.migrate();
        log.info("[CryptoMigration] 迁移完成");
    }
}
