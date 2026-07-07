package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableField;
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

    private Long groupId;

    /**
     * CSV / EXCEL / WORD / PPT / TXT / INLINE；无模板时默认 CSV
     * INLINE：不生成文件，直接将 SQL 结果嵌入通知内容
     */
    private String outputFormat;

    /**
     * 是否根据该 SQL 结果生成图表：1 启用，0 禁用
     */
    private Integer chartEnabled;

    /**
     * 图表类型：BAR / LINE / PIE
     */
    private String chartType;

    /**
     * 图表标题，留空使用 sqlName
     */
    private String chartTitle;

    private String fileSuffix;

    private String fileNamePattern;

    private String description;

    private Integer status;

    /**
     * 非数据库字段：关联的 SQL 分组
     */
    @TableField(exist = false)
    private TaskSqlGroup taskSqlGroup;

    /**
     * 非数据库字段：用于列表展示的分组名称
     */
    @TableField(exist = false)
    private String groupName;

    /**
     * 非数据库字段：在前端任务配置中用于标识该 SQL 在当前任务中的排序
     */
    @TableField(exist = false)
    private Integer sortOrder;
}
