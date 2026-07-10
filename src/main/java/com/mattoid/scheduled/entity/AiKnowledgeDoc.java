package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_doc")
public class AiKnowledgeDoc extends BaseEntity {

    private Long datasourceId;

    private String docType;

    private String title;

    private String content;

    private Integer status;

    @TableField(exist = false)
    private String datasourceName;
}
