package com.mattoid.scheduled.dto;

import com.mattoid.scheduled.entity.TaskConfig;
import lombok.Data;

import java.util.List;

@Data
public class TaskConfigRequest {

    private TaskConfig task;

    private List<String> sqlCodes;

    private List<Long> dependencyIds;
}
