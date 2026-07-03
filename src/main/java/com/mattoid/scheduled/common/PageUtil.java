package com.mattoid.scheduled.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public class PageUtil {

    public static <T> PageResult<T> convert(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(page.getRecords());
        return result;
    }
}
