package com.mattoid.scheduled.entity;

import com.mattoid.scheduled.ai.AiMessage;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
public class AiConversation extends BaseEntity {

    private String sessionId;

    private Long userId;

    private String title;

    private Long datasourceId;

    private Long docId;

    private String messages;

    private Integer status;

    @TableField(exist = false)
    private List<AiMessage> messageList;
}
