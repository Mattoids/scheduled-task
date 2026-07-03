package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("email_recipient")
public class EmailRecipient extends BaseEntity {

    private String recipientName;
    private String email;
    private Long groupId;
    private Integer status;
}
