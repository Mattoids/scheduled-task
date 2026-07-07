package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import com.mattoid.scheduled.entity.TaskWebCrawlRelation;
import com.mattoid.scheduled.entity.TaskWebCrawlSelector;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.mapper.TaskWebCrawlConfigMapper;
import com.mattoid.scheduled.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TaskWebCrawlConfigService extends ServiceImpl<TaskWebCrawlConfigMapper, TaskWebCrawlConfig> {

    private static final String ENC_PREFIX = "ENC(";

    private final TaskWebCrawlRelationService taskWebCrawlRelationService;
    private final TaskWebCrawlSelectorService taskWebCrawlSelectorService;
    private final TaskConfigMapper taskConfigMapper;

    public TaskWebCrawlConfigService(TaskWebCrawlRelationService taskWebCrawlRelationService,
                                     TaskWebCrawlSelectorService taskWebCrawlSelectorService,
                                     TaskConfigMapper taskConfigMapper) {
        this.taskWebCrawlRelationService = taskWebCrawlRelationService;
        this.taskWebCrawlSelectorService = taskWebCrawlSelectorService;
        this.taskConfigMapper = taskConfigMapper;
    }

    @Override
    public boolean save(TaskWebCrawlConfig config) {
        encryptSensitiveFields(config);
        return super.save(config);
    }

    @Override
    public boolean updateById(TaskWebCrawlConfig config) {
        encryptSensitiveFields(config);
        return super.updateById(config);
    }

    public TaskWebCrawlConfig getDecryptedById(Long id) {
        TaskWebCrawlConfig config = getById(id);
        if (config != null) {
            decryptSensitiveFields(config);
        }
        return config;
    }

    public TaskWebCrawlConfig getByCode(String crawlCode) {
        if (!StringUtils.hasText(crawlCode)) {
            return null;
        }
        return lambdaQuery().eq(TaskWebCrawlConfig::getCrawlCode, crawlCode).one();
    }

    public TaskWebCrawlConfig getDecryptedByCode(String crawlCode) {
        TaskWebCrawlConfig config = getByCode(crawlCode);
        if (config != null) {
            decryptSensitiveFields(config);
        }
        return config;
    }

    public List<TaskWebCrawlConfig> listByTaskId(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        TaskConfig task = taskConfigMapper.selectById(taskId);
        return task != null ? listByTaskCode(task.getTaskCode()) : Collections.emptyList();
    }

    public List<TaskWebCrawlConfig> listByTaskCode(String taskCode) {
        if (!StringUtils.hasText(taskCode)) {
            return Collections.emptyList();
        }
        List<String> crawlCodes = taskWebCrawlRelationService.lambdaQuery()
                .eq(TaskWebCrawlRelation::getTaskCode, taskCode)
                .orderByAsc(TaskWebCrawlRelation::getSortOrder)
                .list()
                .stream()
                .map(TaskWebCrawlRelation::getCrawlCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        return listByCrawlCodes(crawlCodes);
    }

    public List<TaskWebCrawlConfig> listByCrawlCodes(List<String> crawlCodes) {
        if (CollectionUtils.isEmpty(crawlCodes)) {
            return Collections.emptyList();
        }
        List<TaskWebCrawlConfig> configs = lambdaQuery()
                .in(TaskWebCrawlConfig::getCrawlCode, crawlCodes)
                .list();
        populateSelectors(configs);
        Map<String, TaskWebCrawlConfig> configMap = configs.stream()
                .collect(Collectors.toMap(TaskWebCrawlConfig::getCrawlCode, c -> c, (a, b) -> a));
        return crawlCodes.stream()
                .map(configMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void populateSelectors(List<TaskWebCrawlConfig> configs) {
        if (CollectionUtils.isEmpty(configs)) {
            return;
        }
        List<Long> configIds = configs.stream()
                .map(TaskWebCrawlConfig::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (configIds.isEmpty()) {
            return;
        }
        List<TaskWebCrawlSelector> selectors = taskWebCrawlSelectorService.lambdaQuery()
                .in(TaskWebCrawlSelector::getCrawlConfigId, configIds)
                .orderByAsc(TaskWebCrawlSelector::getSortOrder)
                .list();
        Map<Long, List<TaskWebCrawlSelector>> selectorMap = selectors.stream()
                .collect(Collectors.groupingBy(TaskWebCrawlSelector::getCrawlConfigId));
        for (TaskWebCrawlConfig config : configs) {
            config.setSelectors(selectorMap.getOrDefault(config.getId(), Collections.emptyList()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean removeCrawlConfig(Long crawlId) {
        TaskWebCrawlConfig config = getById(crawlId);
        if (config != null) {
            taskWebCrawlSelectorService.lambdaUpdate()
                    .eq(TaskWebCrawlSelector::getCrawlConfigId, crawlId)
                    .remove();
            if (StringUtils.hasText(config.getCrawlCode())) {
                taskWebCrawlRelationService.lambdaUpdate()
                        .eq(TaskWebCrawlRelation::getCrawlCode, config.getCrawlCode())
                        .remove();
            }
        }
        return removeById(crawlId);
    }

    private void encryptSensitiveFields(TaskWebCrawlConfig config) {
        if (StringUtils.hasText(config.getCookies()) && !config.getCookies().startsWith(ENC_PREFIX)) {
            config.setCookies(CryptoUtil.encrypt(config.getCookies()));
        }
        if (StringUtils.hasText(config.getAuthConfig()) && !config.getAuthConfig().startsWith(ENC_PREFIX)) {
            config.setAuthConfig(CryptoUtil.encrypt(config.getAuthConfig()));
        }
        if (StringUtils.hasText(config.getSshPassword()) && !config.getSshPassword().startsWith(ENC_PREFIX)) {
            config.setSshPassword(CryptoUtil.encrypt(config.getSshPassword()));
        }
        if (StringUtils.hasText(config.getSshPrivateKey()) && !config.getSshPrivateKey().startsWith(ENC_PREFIX)) {
            config.setSshPrivateKey(CryptoUtil.encrypt(config.getSshPrivateKey()));
        }
        if (StringUtils.hasText(config.getSshPassphrase()) && !config.getSshPassphrase().startsWith(ENC_PREFIX)) {
            config.setSshPassphrase(CryptoUtil.encrypt(config.getSshPassphrase()));
        }
    }

    private void decryptSensitiveFields(TaskWebCrawlConfig config) {
        if (StringUtils.hasText(config.getCookies())) {
            config.setCookies(CryptoUtil.decryptIfNeeded(config.getCookies()));
        }
        if (StringUtils.hasText(config.getAuthConfig())) {
            config.setAuthConfig(CryptoUtil.decryptIfNeeded(config.getAuthConfig()));
        }
        if (StringUtils.hasText(config.getSshPassword())) {
            config.setSshPassword(CryptoUtil.decryptIfNeeded(config.getSshPassword()));
        }
        if (StringUtils.hasText(config.getSshPrivateKey())) {
            config.setSshPrivateKey(CryptoUtil.decryptIfNeeded(config.getSshPrivateKey()));
        }
        if (StringUtils.hasText(config.getSshPassphrase())) {
            config.setSshPassphrase(CryptoUtil.decryptIfNeeded(config.getSshPassphrase()));
        }
    }
}
