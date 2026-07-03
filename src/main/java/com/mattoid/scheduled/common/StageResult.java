package com.mattoid.scheduled.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StageResult {

    private String stage;
    private boolean success;
    private String message;
}
