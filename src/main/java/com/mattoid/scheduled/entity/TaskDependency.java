package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_dependency")
public class TaskDependency extends BaseEntity {

    private Long taskId;

    private Long dependsOnTaskId;
}
