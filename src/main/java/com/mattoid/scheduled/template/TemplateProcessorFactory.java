package com.mattoid.scheduled.template;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TemplateProcessorFactory {

    private final List<TemplateProcessor> processors;

    public TemplateProcessorFactory(List<TemplateProcessor> processors) {
        this.processors = processors;
    }

    public TemplateProcessor getProcessor(String templateType) {
        for (TemplateProcessor processor : processors) {
            if (processor.supports(templateType)) {
                return processor;
            }
        }
        throw new IllegalArgumentException("不支持的模板类型: " + templateType);
    }
}
