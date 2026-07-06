package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("email_recipient")
public class EmailRecipient extends BaseEntity {

    private String recipientName;
    private String email;
    private Integer status;

    @TableField(exist = false)
    private List<Long> groupIds;
}
