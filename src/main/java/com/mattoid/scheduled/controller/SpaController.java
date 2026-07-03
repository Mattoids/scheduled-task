package com.mattoid.scheduled.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {

    @RequestMapping(value = {
            "/",
            "/login",
            "/dashboard",
            "/task",
            "/task/**",
            "/task-log",
            "/task-log/**",
            "/datasource",
            "/datasource/**",
            "/email-config",
            "/email-config/**",
            "/email-recipient",
            "/email-recipient/**",
            "/template",
            "/template/**",
            "/system",
            "/system/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
