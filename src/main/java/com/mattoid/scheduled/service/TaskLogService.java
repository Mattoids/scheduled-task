package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import org.springframework.stereotype.Service;

@Service
public class TaskLogService extends ServiceImpl<TaskLogMapper, TaskLog> {
}
