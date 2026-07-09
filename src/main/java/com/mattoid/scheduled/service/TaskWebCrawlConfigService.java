package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.datasource.SshHopConfig;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        boolean result = super.save(config);
        if (result && config.getId() != null) {
            persistSshHops(config);
        }
        return result;
    }

    @Override
    public boolean updateById(TaskWebCrawlConfig config) {
        encryptSensitiveFields(config);
        lambdaUpdate()
                .set(TaskWebCrawlConfig::getTemplateId, config.getTemplateId())
                .set(TaskWebCrawlConfig::getTemplateCode, config.getTemplateCode())
                .eq(TaskWebCrawlConfig::getId, config.getId())
                .update();
        boolean result = super.updateById(config);
        if (result && config.getId() != null) {
            persistSshHops(config);
        }
        return result;
    }

    public TaskWebCrawlConfig getDecryptedById(Long id) {
        TaskWebCrawlConfig config = getById(id);
        if (config != null) {
            decryptSensitiveFields(config);
            if (config.getSshHops() == null) {
                config.setSshHops(parseSshHopsFromRawJson(id));
            }
        }
        return config;
    }

    private List<SshHopConfig> parseSshHopsFromRawJson(Long id) {
        try {
            List<Object> rows = baseMapper.selectObjs(new QueryWrapper<TaskWebCrawlConfig>()
                    .select("ssh_hops")
                    .eq("id", id));
            if (CollectionUtils.isEmpty(rows)) {
                return Collections.emptyList();
            }
            Object raw = rows.get(0);
            if (raw == null) {
                return Collections.emptyList();
            }
            String json = raw.toString();
            if (!StringUtils.hasText(json) || "null".equals(json)) {
                return Collections.emptyList();
            }
            List<SshHopConfig> hops = OBJECT_MAPPER.readValue(json, new TypeReference<List<SshHopConfig>>() {
            });
            return hops == null ? Collections.emptyList() : hops;
        } catch (Exception e) {
            log.warn("从数据库原始 JSON 解析 ssh_hops 失败, id={}", id, e);
            return Collections.emptyList();
        }
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
            if (config.getSshHops() == null) {
                config.setSshHops(Collections.emptyList());
            }
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
        if (StringUtils.hasText(config.getProxyPassword()) && !config.getProxyPassword().startsWith(ENC_PREFIX)) {
            config.setProxyPassword(CryptoUtil.encrypt(config.getProxyPassword()));
        }
        encryptSshHops(config.getSshHops());
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
        if (StringUtils.hasText(config.getProxyPassword())) {
            config.setProxyPassword(CryptoUtil.decryptIfNeeded(config.getProxyPassword()));
        }
        decryptSshHops(config.getSshHops());
    }

    private void encryptSshHops(List<SshHopConfig> hops) {
        if (CollectionUtils.isEmpty(hops)) {
            return;
        }
        for (SshHopConfig hop : hops) {
            if (StringUtils.hasText(hop.getPassword()) && !hop.getPassword().startsWith(ENC_PREFIX)) {
                hop.setPassword(CryptoUtil.encrypt(hop.getPassword()));
            }
            if (StringUtils.hasText(hop.getPrivateKey()) && !hop.getPrivateKey().startsWith(ENC_PREFIX)) {
                hop.setPrivateKey(CryptoUtil.encrypt(hop.getPrivateKey()));
            }
            if (StringUtils.hasText(hop.getPassphrase()) && !hop.getPassphrase().startsWith(ENC_PREFIX)) {
                hop.setPassphrase(CryptoUtil.encrypt(hop.getPassphrase()));
            }
        }
    }

    private void persistSshHops(TaskWebCrawlConfig config) {
        List<SshHopConfig> hops = config.getSshHops();
        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(hops == null ? Collections.emptyList() : hops);
        } catch (JsonProcessingException e) {
            log.error("序列化 SSH 多跳配置失败: {}", hops, e);
            throw new RuntimeException("序列化 SSH 多跳配置失败", e);
        }
        UpdateWrapper<TaskWebCrawlConfig> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", config.getId());
        wrapper.set("ssh_hops", json);
        update(wrapper);
    }

    private void decryptSshHops(List<SshHopConfig> hops) {
        if (CollectionUtils.isEmpty(hops)) {
            return;
        }
        for (SshHopConfig hop : hops) {
            if (StringUtils.hasText(hop.getPassword())) {
                hop.setPassword(CryptoUtil.decryptIfNeeded(hop.getPassword()));
            }
            if (StringUtils.hasText(hop.getPrivateKey())) {
                hop.setPrivateKey(CryptoUtil.decryptIfNeeded(hop.getPrivateKey()));
            }
            if (StringUtils.hasText(hop.getPassphrase())) {
                hop.setPassphrase(CryptoUtil.decryptIfNeeded(hop.getPassphrase()));
            }
        }
    }
}
