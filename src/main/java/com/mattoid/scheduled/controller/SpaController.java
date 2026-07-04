package com.mattoid.scheduled.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.URI;

@Controller
public class SpaController {

    @RequestMapping(value = {
            "/",
            "/login",
            "/dashboard",
            "/task-sql",
            "/task-sql/**",
            "/task",
            "/task/**",
            "/task-log",
            "/task-log/**",
            "/notification-rule",
            "/notification-rule/**",
            "/notification",
            "/notification/**",
            "/ai-config",
            "/ai-config/**",
            "/datasource",
            "/datasource/**",
            "/email-config",
            "/email-config/**",
            "/email-recipient",
            "/email-recipient/**",
            "/template",
            "/template/**",
            "/wecom",
            "/wecom/**",
            "/system",
            "/system/**"
    })
    public String forward() {
        return "forward:/index.html";
    }

    @RequestMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/favicon.svg"))
                .build();
    }
}
