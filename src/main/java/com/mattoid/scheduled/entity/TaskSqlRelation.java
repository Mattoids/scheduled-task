package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_sql_relation")
public class TaskSqlRelation extends BaseEntity {

    private Long taskId;

    private String taskCode;

    private Long sqlId;

    private String sqlCode;

    private Integer sortOrder;
}
