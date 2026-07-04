package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_sql_group")
public class TaskSqlGroup extends BaseEntity {

    private String groupName;

    private String groupCode;

    private String fileNamePattern;

    private String description;

    private Integer status;
}
