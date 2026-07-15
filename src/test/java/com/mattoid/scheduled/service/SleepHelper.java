package com.mattoid.scheduled.service;

/**
 * 测试辅助类：让子进程睡眠指定秒数，用于验证执行超时逻辑。
 */
public class SleepHelper {
    public static void main(String[] args) throws Exception {
        long seconds = args.length > 0 ? Long.parseLong(args[0]) : 60;
        Thread.sleep(seconds * 1000);
    }
}
