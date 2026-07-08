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

    private String templateCode;

    private Long groupId;

    private String groupCode;

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

    /**
     * 图表分类过多时是否自动合并相邻数据：1 开启，0 关闭
     */
    private Integer chartAutoMerge;

    /**
     * X 轴标签旋转角度：AUTO / 0 / 45 / 90
     */
    private String chartLabelRotation;

    /**
     * 图表背景色，留空/透明表示使用透明背景
     */
    private String chartBackgroundColor;

    /**
     * Excel 合并组名，相同组名的 SQL 结果会合并到同一个 Excel 文件
     */
    private String excelMergeGroup;

    /**
     * Excel 中 sheet 页名称；同一合并组内相同 sheet 名的 SQL 会追加到同一页
     */
    private String excelSheetName;

    /**
     * 是否启用 Excel 循环生成 sheet：1 启用，0 禁用
     */
    private Integer excelLoopEnabled;

    /**
     * Excel 循环生成配置 JSON
     */
    private String excelLoopConfig;

    /**
     * 是否启用 Excel 追加模式：1 启用，0 禁用
     */
    private Integer excelAppendMode;

    /**
     * Excel 基础文件路径（追加模式），支持占位符
     */
    private String excelBaseFilePath;

    /**
     * Excel 追加模式是否更新同名 sheet：1 更新，0 跳过
     */
    private Integer excelAppendUpdateSameSheet;

    private String fileSuffix;

    private String fileNamePattern;

    private String description;

    /**
     * 自定义参数 JSON，key 对应 SQL 中的 ${xxx} 占位符
     */
    private String customParams;

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
