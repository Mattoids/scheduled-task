package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("email_recipient_group_relation")
public class EmailRecipientGroupRelation extends BaseEntity {

    private Long recipientId;
    private Long groupId;
}
