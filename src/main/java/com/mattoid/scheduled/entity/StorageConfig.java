package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("storage_config")
public class StorageConfig extends BaseEntity {

    /**
     * 配置名称
     */
    private String configName;

    /**
     * 存储类型：LOCAL / OSS / S3 / WEBDAV
     */
    private String storageType;

    /**
     * 配置 JSON
     */
    private String configJson;

    /**
     * 状态：1 启用，0 禁用
     */
    private Integer status;

    /**
     * 是否默认：1 默认，0 非默认
     */
    private Integer isDefault;
}
