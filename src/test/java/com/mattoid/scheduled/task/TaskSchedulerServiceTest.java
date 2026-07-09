package com.mattoid.scheduled.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskSchedulerServiceTest {

    @Test
    void normalizeCronExpression_shouldReplaceDayOfMonthWithQuestionMark() {
        assertEquals("0 0 9 ? * 1", TaskSchedulerService.normalizeCronExpression("0 0 9 * * 1"));
    }

    @Test
    void normalizeCronExpression_shouldReplaceDayOfWeekWithQuestionMark() {
        assertEquals("0 0 9 15 * ?", TaskSchedulerService.normalizeCronExpression("0 0 9 15 * *"));
    }

    @Test
    void normalizeCronExpression_shouldKeepExistingQuestionMark() {
        assertEquals("0 0 9 ? * *", TaskSchedulerService.normalizeCronExpression("0 0 9 ? * *"));
        assertEquals("0 0 9 * * ?", TaskSchedulerService.normalizeCronExpression("0 0 9 * * ?"));
    }

    @Test
    void normalizeCronExpression_shouldPrependSecondsForFiveFieldCron() {
        assertEquals("0 0 9 * * ?", TaskSchedulerService.normalizeCronExpression("0 9 * * ?"));
    }

    @Test
    void normalizeCronExpression_shouldSupportOptionalYearField() {
        assertEquals("0 0 9 ? * 1 2099", TaskSchedulerService.normalizeCronExpression("0 0 9 * * 1 2099"));
    }

    @Test
    void normalizeCronExpression_shouldReturnNullForInvalidInput() {
        assertNull(TaskSchedulerService.normalizeCronExpression(null));
        assertNull(TaskSchedulerService.normalizeCronExpression(""));
        assertNull(TaskSchedulerService.normalizeCronExpression("0 0 9"));
    }
}
