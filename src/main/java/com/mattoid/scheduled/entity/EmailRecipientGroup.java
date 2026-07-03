package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("email_recipient_group")
public class EmailRecipientGroup extends BaseEntity {

    private String groupName;
    private String groupCode;
    private String description;
    private Integer status;
}
