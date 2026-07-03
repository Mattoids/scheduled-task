package com.mattoid.scheduled.vo;

import lombok.Data;

import java.util.List;

@Data
public class CurrentUserVo {

    private Long userId;
    private String username;
    private String nickname;
    private List<String> permissions;
}
