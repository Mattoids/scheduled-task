package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.EmailRecipientGroup;
import com.mattoid.scheduled.mapper.EmailRecipientGroupMapper;
import org.springframework.stereotype.Service;

@Service
public class EmailRecipientGroupService extends ServiceImpl<EmailRecipientGroupMapper, EmailRecipientGroup> {
}
