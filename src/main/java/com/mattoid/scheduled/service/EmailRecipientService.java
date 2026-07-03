package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.EmailRecipient;
import com.mattoid.scheduled.mapper.EmailRecipientMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmailRecipientService extends ServiceImpl<EmailRecipientMapper, EmailRecipient> {

    public List<EmailRecipient> listByIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toList());
        return listByIds(idList);
    }
}
