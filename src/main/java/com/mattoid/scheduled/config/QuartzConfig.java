package com.mattoid.scheduled.config;

import org.quartz.Scheduler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import java.util.Properties;

@Configuration
public class QuartzConfig {

    @Bean
    public SpringBeanJobFactory springBeanJobFactory(ApplicationContext applicationContext) {
        SpringBeanJobFactory jobFactory = new SpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(SpringBeanJobFactory jobFactory) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setJobFactory(jobFactory);
        factory.setQuartzProperties(quartzProperties());
        factory.setWaitForJobsToCompleteOnShutdown(true);
        factory.setOverwriteExistingJobs(true);
        return factory;
    }

    @Bean
    public Scheduler scheduler(SchedulerFactoryBean factory) {
        return factory.getScheduler();
    }

    private Properties quartzProperties() {
        Properties props = new Properties();
        props.put("org.quartz.scheduler.instanceName", "ScheduledTaskScheduler");
        props.put("org.quartz.scheduler.instanceId", "AUTO");
        props.put("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        props.put("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        props.put("org.quartz.threadPool.threadCount", "20");
        props.put("org.quartz.threadPool.threadPriority", "5");
        return props;
    }
}
