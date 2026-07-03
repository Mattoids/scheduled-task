package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    private String permissionCode;
    private String permissionName;
    private String resourceType;
    private Long parentId;
    private Integer sortOrder;
    private String path;
    private Integer status;
}
