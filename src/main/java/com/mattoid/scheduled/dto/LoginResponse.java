package com.mattoid.scheduled.dto;

import lombok.Data;

import java.util.List;

@Data
public class LoginResponse {

    private String token;
    private String username;
    private String nickname;
    private List<String> permissions;
}
