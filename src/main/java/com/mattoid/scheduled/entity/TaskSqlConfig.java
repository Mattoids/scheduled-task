package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_sql_config")
public class TaskSqlConfig extends BaseEntity {

    private String sqlName;

    private String sqlCode;

    private Long datasourceId;

    private String sqlContent;

    private Long templateId;

    /**
     * CSV / EXCEL / WORD / PPT / TXT；无模板时默认 CSV
     */
    private String outputFormat;

    private String fileSuffix;

    private String fileNamePattern;

    private String groupName;

    private String description;

    private Integer status;

    /**
     * 非数据库字段：在前端任务配置中用于标识该 SQL 在当前任务中的排序
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer sortOrder;
}
