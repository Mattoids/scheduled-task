package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.EmailRecipient;
import com.mattoid.scheduled.entity.EmailRecipientGroupRelation;
import com.mattoid.scheduled.mapper.EmailRecipientGroupRelationMapper;
import com.mattoid.scheduled.mapper.EmailRecipientMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EmailRecipientService extends ServiceImpl<EmailRecipientMapper, EmailRecipient> {

    private final EmailRecipientGroupRelationMapper relationMapper;

    public EmailRecipientService(EmailRecipientGroupRelationMapper relationMapper) {
        this.relationMapper = relationMapper;
    }

    @Override
    public boolean save(EmailRecipient entity) {
        boolean saved = super.save(entity);
        if (saved) {
            saveRelations(entity);
        }
        return saved;
    }

    @Override
    public boolean updateById(EmailRecipient entity) {
        boolean updated = super.updateById(entity);
        if (updated) {
            updateRelations(entity);
        }
        return updated;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean removed = super.removeById(id);
        if (removed) {
            relationMapper.delete(new LambdaQueryWrapper<EmailRecipientGroupRelation>()
                    .eq(EmailRecipientGroupRelation::getRecipientId, id));
        }
        return removed;
    }

    public Page<EmailRecipient> pageRecipients(PageQuery query, Long groupId, String recipientName) {
        Page<EmailRecipient> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<EmailRecipient> wrapper = new LambdaQueryWrapper<EmailRecipient>()
                .like(StringUtils.hasText(recipientName), EmailRecipient::getRecipientName, recipientName)
                .orderByDesc(EmailRecipient::getCreateTime);

        if (groupId != null) {
            List<Long> recipientIds = listRecipientIdsByGroupIds(Collections.singletonList(groupId));
            if (recipientIds.isEmpty()) {
                return page;
            }
            wrapper.in(EmailRecipient::getId, recipientIds);
        }

        page = page(page, wrapper);
        fillGroupIds(page.getRecords());
        return page;
    }

    @Override
    public List<EmailRecipient> list() {
        List<EmailRecipient> list = super.list();
        fillGroupIds(list);
        return list;
    }

    public List<EmailRecipient> listByIds(String ids) {
        if (!StringUtils.hasText(ids)) {
            return Collections.emptyList();
        }
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toList());
        if (idList.isEmpty()) {
            return Collections.emptyList();
        }
        List<EmailRecipient> list = listByIds(idList);
        fillGroupIds(list);
        return list;
    }

    public List<EmailRecipient> listByGroupIds(String groupIds) {
        if (!StringUtils.hasText(groupIds)) {
            return Collections.emptyList();
        }
        List<Long> idList = Arrays.stream(groupIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toList());
        if (idList.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> recipientIds = listRecipientIdsByGroupIds(idList);
        if (recipientIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<EmailRecipient> list = listByIds(recipientIds);
        fillGroupIds(list);
        return list;
    }

    private List<Long> listRecipientIdsByGroupIds(List<Long> groupIds) {
        List<EmailRecipientGroupRelation> relations = relationMapper.selectList(
                new LambdaQueryWrapper<EmailRecipientGroupRelation>()
                        .in(EmailRecipientGroupRelation::getGroupId, groupIds));
        return relations.stream()
                .map(EmailRecipientGroupRelation::getRecipientId)
                .distinct()
                .collect(Collectors.toList());
    }

    private void saveRelations(EmailRecipient entity) {
        if (entity.getId() == null || CollectionUtils.isEmpty(entity.getGroupIds())) {
            return;
        }
        List<EmailRecipientGroupRelation> relations = entity.getGroupIds().stream()
                .filter(gid -> gid != null)
                .map(gid -> {
                    EmailRecipientGroupRelation relation = new EmailRecipientGroupRelation();
                    relation.setRecipientId(entity.getId());
                    relation.setGroupId(gid);
                    return relation;
                })
                .collect(Collectors.toList());
        if (!relations.isEmpty()) {
            relationMapper.insert(relations);
        }
    }

    private void updateRelations(EmailRecipient entity) {
        if (entity.getId() == null) {
            return;
        }
        relationMapper.delete(new LambdaQueryWrapper<EmailRecipientGroupRelation>()
                .eq(EmailRecipientGroupRelation::getRecipientId, entity.getId()));
        saveRelations(entity);
    }

    private void fillGroupIds(List<EmailRecipient> recipients) {
        if (CollectionUtils.isEmpty(recipients)) {
            return;
        }
        Set<Long> recipientIds = recipients.stream()
                .map(EmailRecipient::getId)
                .collect(Collectors.toSet());
        List<EmailRecipientGroupRelation> relations = relationMapper.selectList(
                new LambdaQueryWrapper<EmailRecipientGroupRelation>()
                        .in(EmailRecipientGroupRelation::getRecipientId, recipientIds));
        Map<Long, List<Long>> groupMap = relations.stream()
                .collect(Collectors.groupingBy(
                        EmailRecipientGroupRelation::getRecipientId,
                        Collectors.mapping(EmailRecipientGroupRelation::getGroupId, Collectors.toList())));
        for (EmailRecipient recipient : recipients) {
            recipient.setGroupIds(groupMap.getOrDefault(recipient.getId(), Collections.emptyList()));
        }
    }
}
