package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_web_crawl_selector")
public class TaskWebCrawlSelector extends BaseEntity {

    private Long crawlConfigId;

    private String crawlCode;

    private String fieldName;

    /**
     * 是否为行级选择器
     */
    private Integer isRowSelector;

    /**
     * CSS / XPATH / REGEX
     */
    private String selectorType;

    private String selectorValue;

    /**
     * text / html / src / href / attr:xxx
     */
    private String attribute;

    /**
     * STRING / NUMBER / DATE / LINK / HTML
     */
    private String dataType;

    private String defaultValue;

    private Integer sortOrder;
}
