package com.mattoid.scheduled;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.mattoid.scheduled.mapper")
public class ScheduledTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduledTaskApplication.class, args);
    }
}
