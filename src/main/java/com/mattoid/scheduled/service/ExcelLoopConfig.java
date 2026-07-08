package com.mattoid.scheduled.service;

import lombok.Data;

@Data
public class ExcelLoopConfig {

    /**
     * 循环开始表达式，如 2025-05、{currentMonth}、{firstDayOfLastMonth}
     */
    private String startExpr;

    /**
     * 循环结束表达式，如 {currentMonth}、2026-12
     */
    private String endExpr;

    /**
     * 步长，默认 1
     */
    private Integer step;

    /**
     * 循环单位：MONTH / DAY / WEEK / YEAR / HOUR / MINUTE
     */
    private String unit;

    /**
     * Sheet 名称表达式，如 ${loopValue:yyyy年MM月}
     */
    private String sheetNameExpr;

    /**
     * 可选的 SpEL 循环条件，变量：loopIndex、loopValue、loopStart、loopEnd
     */
    private String condition;

    /**
     * 数据为空时是否跳过生成该 sheet，默认 false
     */
    private Boolean skipEmptySheet;

    public Integer getStep() {
        return step == null || step == 0 ? 1 : step;
    }

    public String getUnit() {
        return unit == null ? "MONTH" : unit;
    }

    public boolean isSkipEmptySheet() {
        return skipEmptySheet != null && skipEmptySheet;
    }
}
