package com.mattoid.scheduled.migration;

import com.mattoid.scheduled.datasource.SshHopConfig;
import com.mattoid.scheduled.entity.DatasourceConfig;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import com.mattoid.scheduled.entity.WeComAdminAccount;
import com.mattoid.scheduled.mapper.DatasourceConfigMapper;
import com.mattoid.scheduled.mapper.NotificationConfigMapper;
import com.mattoid.scheduled.mapper.TaskWebCrawlConfigMapper;
import com.mattoid.scheduled.mapper.WeComAdminAccountMapper;
import com.mattoid.scheduled.util.CryptoMigrationUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 全量迁移数据库中旧版 ECB 密文到新版 AES-GCM。
 */
@Slf4j
@Service
public class CryptoMigrationService {

    private final DatasourceConfigMapper datasourceConfigMapper;
    private final TaskWebCrawlConfigMapper taskWebCrawlConfigMapper;
    private final NotificationConfigMapper notificationConfigMapper;
    private final WeComAdminAccountMapper weComAdminAccountMapper;

    public CryptoMigrationService(DatasourceConfigMapper datasourceConfigMapper,
                                  TaskWebCrawlConfigMapper taskWebCrawlConfigMapper,
                                  NotificationConfigMapper notificationConfigMapper,
                                  WeComAdminAccountMapper weComAdminAccountMapper) {
        this.datasourceConfigMapper = datasourceConfigMapper;
        this.taskWebCrawlConfigMapper = taskWebCrawlConfigMapper;
        this.notificationConfigMapper = notificationConfigMapper;
        this.weComAdminAccountMapper = weComAdminAccountMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void migrate() {
        migrateDatasourceConfigs();
        migrateWebCrawlConfigs();
        migrateNotificationConfigs();
        migrateWeComAdminAccounts();
    }

    private void migrateDatasourceConfigs() {
        List<DatasourceConfig> list = datasourceConfigMapper.selectList(null);
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        int changed = 0;
        for (DatasourceConfig config : list) {
            boolean modified = false;
            if (migrateValue(config::getPassword, config::setPassword)) {
                modified = true;
            }
            if (migrateValue(config::getSshPassword, config::setSshPassword)) {
                modified = true;
            }
            if (migrateValue(config::getSshPrivateKey, config::setSshPrivateKey)) {
                modified = true;
            }
            if (migrateValue(config::getSshPassphrase, config::setSshPassphrase)) {
                modified = true;
            }
            if (modified) {
                datasourceConfigMapper.updateById(config);
                changed++;
            }
        }
        log.info("[CryptoMigration] datasource_config: migrated {}/{} rows", changed, list.size());
    }

    private void migrateWebCrawlConfigs() {
        List<TaskWebCrawlConfig> list = taskWebCrawlConfigMapper.selectList(null);
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        int changed = 0;
        for (TaskWebCrawlConfig config : list) {
            boolean modified = false;
            if (migrateValue(config::getCookies, config::setCookies)) modified = true;
            if (migrateValue(config::getAuthConfig, config::setAuthConfig)) modified = true;
            if (migrateValue(config::getSshPassword, config::setSshPassword)) modified = true;
            if (migrateValue(config::getSshPrivateKey, config::setSshPrivateKey)) modified = true;
            if (migrateValue(config::getSshPassphrase, config::setSshPassphrase)) modified = true;
            if (migrateValue(config::getProxyPassword, config::setProxyPassword)) modified = true;

            List<SshHopConfig> hops = config.getSshHops();
            if (!CollectionUtils.isEmpty(hops) && CryptoMigrationUtil.migrateSshHops(hops) != null) {
                boolean hopModified = false;
                for (SshHopConfig hop : hops) {
                    if (CryptoMigrationUtil.needsMigration(hop.getPassword())
                            || CryptoMigrationUtil.needsMigration(hop.getPrivateKey())
                            || CryptoMigrationUtil.needsMigration(hop.getPassphrase())) {
                        hopModified = true;
                        break;
                    }
                }
                if (hopModified) {
                    config.setSshHops(hops);
                    modified = true;
                }
            }

            if (modified) {
                taskWebCrawlConfigMapper.updateById(config);
                changed++;
            }
        }
        log.info("[CryptoMigration] task_web_crawl_config: migrated {}/{} rows", changed, list.size());
    }

    private void migrateNotificationConfigs() {
        List<NotificationConfig> list = notificationConfigMapper.selectList(null);
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        int changed = 0;
        for (NotificationConfig config : list) {
            String migrated = CryptoMigrationUtil.migrateNotificationConfigJson(config.getConfigType(), config.getConfigJson());
            if (!java.util.Objects.equals(migrated, config.getConfigJson())) {
                config.setConfigJson(migrated);
                notificationConfigMapper.updateById(config);
                changed++;
            }
        }
        log.info("[CryptoMigration] notification_config: migrated {}/{} rows", changed, list.size());
    }

    private void migrateWeComAdminAccounts() {
        List<WeComAdminAccount> list = weComAdminAccountMapper.selectList(null);
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        int changed = 0;
        for (WeComAdminAccount account : list) {
            if (migrateValue(account::getAdminCookie, account::setAdminCookie)) {
                weComAdminAccountMapper.updateById(account);
                changed++;
            }
        }
        log.info("[CryptoMigration] wecom_admin_account: migrated {}/{} rows", changed, list.size());
    }

    private boolean migrateValue(java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter) {
        String original = getter.get();
        if (!CryptoMigrationUtil.needsMigration(original)) {
            return false;
        }
        String migrated = CryptoMigrationUtil.migrateValue(original);
        if (!java.util.Objects.equals(migrated, original)) {
            setter.accept(migrated);
            return true;
        }
        return false;
    }
}
