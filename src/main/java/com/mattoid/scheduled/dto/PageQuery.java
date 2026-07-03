package com.mattoid.scheduled.dto;

import lombok.Data;

@Data
public class PageQuery {

    private Long current = 1L;
    private Long size = 20L;
}
