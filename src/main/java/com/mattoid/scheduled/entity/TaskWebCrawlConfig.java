package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_web_crawl_config")
public class TaskWebCrawlConfig extends BaseEntity {

    private String crawlName;

    private String crawlCode;

    private String requestUrl;

    private String requestMethod;

    private String requestHeaders;

    private String requestParams;

    private String requestBody;

    private String requestContentType;

    private String cookies;

    private String authType;

    private String authConfig;

    private Integer sshEnabled;

    private String sshHost;

    private Integer sshPort;

    private String sshUsername;

    private String sshPassword;

    private String sshPrivateKey;

    private String sshPassphrase;

    /**
     * PASSWORD / KEY
     */
    private String sshAuthType;

    private String sshRemoteHost;

    private Integer sshRemotePort;

    private Integer sshLocalPort;

    private Integer proxyEnabled;

    private String proxyHost;

    private Integer proxyPort;

    private String proxyUsername;

    private String proxyPassword;

    /**
     * STATIC / DYNAMIC
     */
    private String renderType;

    private String driverConfig;

    /**
     * CSV / EXCEL / WORD / PPT / TXT / INLINE
     */
    private String outputFormat;

    private Long templateId;

    private String templateCode;

    private String fileSuffix;

    private String fileNamePattern;

    private String description;

    private String customParams;

    private Integer status;

    private Integer paginationEnabled;

    private String paginationType;

    private String paginationSelector;

    private String paginationUrlTemplate;

    private Integer paginationMaxPages;

    private Integer mediaEnabled;

    private String mediaSelector;

    private String mediaFileTypes;

    private Long mediaStorageConfigId;

    private String mediaOutputMode;

    private String mediaZipNamePattern;

    private String mediaFilterConfig;

    private Integer chartEnabled;

    private String chartType;

    private String chartTitle;

    private Integer chartAutoMerge;

    private String chartLabelRotation;

    private String chartBackgroundColor;

    /**
     * 非数据库字段：字段提取规则
     */
    @TableField(exist = false)
    private List<TaskWebCrawlSelector> selectors;

    /**
     * 非数据库字段：在前端任务配置中用于标识该爬取配置在当前任务中的排序
     */
    @TableField(exist = false)
    private Integer sortOrder;
}
