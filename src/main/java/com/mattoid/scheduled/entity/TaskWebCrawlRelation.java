package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_web_crawl_relation")
public class TaskWebCrawlRelation extends BaseEntity {

    private Long taskId;

    private String taskCode;

    private Long crawlId;

    private String crawlCode;

    private Integer sortOrder;
}
