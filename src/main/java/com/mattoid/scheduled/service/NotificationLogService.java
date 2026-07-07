package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.NotificationLog;
import com.mattoid.scheduled.mapper.NotificationLogMapper;
import org.springframework.stereotype.Service;

@Service
public class NotificationLogService extends ServiceImpl<NotificationLogMapper, NotificationLog> {
}
