package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("report_template")
public class ReportTemplate extends BaseEntity {

    private String templateName;
    private String templateCode;

    /**
     * EXCEL / WORD / PPT / CSV / TXT
     */
    private String templateType;

    private String filePath;
    private String fileName;
    private String description;
    private Integer status;
}
