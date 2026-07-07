package com.mattoid.scheduled.dto;

import lombok.Data;

import java.io.File;

/**
 * 企业微信指令处理结果，支持文本和附加图片。
 */
@Data
public class CommandResult {

    private String text;
    private File imageFile;

    public CommandResult() {
    }

    public CommandResult(String text) {
        this.text = text;
    }

    public CommandResult(String text, File imageFile) {
        this.text = text;
        this.imageFile = imageFile;
    }

    public boolean hasText() {
        return text != null && !text.isEmpty();
    }

    public boolean hasImage() {
        return imageFile != null && imageFile.exists();
    }
}
