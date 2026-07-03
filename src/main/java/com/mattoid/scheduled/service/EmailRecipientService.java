package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.EmailRecipient;
import com.mattoid.scheduled.mapper.EmailRecipientMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmailRecipientService extends ServiceImpl<EmailRecipientMapper, EmailRecipient> {

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
        return listByIds(idList);
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
        return lambdaQuery()
                .in(EmailRecipient::getGroupId, idList)
                .eq(EmailRecipient::getStatus, 1)
                .list();
    }
}
