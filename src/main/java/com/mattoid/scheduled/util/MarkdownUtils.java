package com.mattoid.scheduled.util;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight Markdown to plain text converter.
 */
public class MarkdownUtils {

    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```(?:\\w+)?\\n?(.*?)```");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]*\\)");
    private static final Pattern TRIPLE_EMPHASIS = Pattern.compile("\\*\\*\\*(.*?)\\*\\*\\*|___(.*?)___");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.*?)\\*\\*|__(.*?)__");
    private static final Pattern ITALIC = Pattern.compile("\\*(.*?)\\*|_(.*?)_");
    private static final Pattern STRIKE = Pattern.compile("~~(.*?)~~");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+", Pattern.MULTILINE);
    private static final Pattern BLOCKQUOTE = Pattern.compile("^\\s*>\\s?", Pattern.MULTILINE);
    private static final Pattern UNORDERED_LIST = Pattern.compile("^\\s*[-*+]\\s+", Pattern.MULTILINE);
    private static final Pattern ORDERED_LIST = Pattern.compile("^\\s*\\d+\\.\\s+", Pattern.MULTILINE);
    private static final Pattern HORIZONTAL_RULE = Pattern.compile("^[-=*_]{3,}\\s*$", Pattern.MULTILINE);
    private static final Pattern MULTIPLE_BLANKS = Pattern.compile("\\n{3,}");

    /**
     * Remove common Markdown syntax and return readable plain text.
     */
    public static String toPlainText(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }

        String text = markdown;

        // Code fences: keep code content, remove fences
        text = CODE_FENCE.matcher(text).replaceAll("$1\n");
        // Inline code
        text = INLINE_CODE.matcher(text).replaceAll("$1");
        // Images: keep alt text
        text = IMAGE.matcher(text).replaceAll("$1");
        // Links: keep link text
        text = LINK.matcher(text).replaceAll("$1");
        // Triple emphasis
        text = TRIPLE_EMPHASIS.matcher(text).replaceAll(r -> Matcher.quoteReplacement(r.group(1) != null ? r.group(1) : r.group(2)));
        // Bold
        text = BOLD.matcher(text).replaceAll(r -> Matcher.quoteReplacement(r.group(1) != null ? r.group(1) : r.group(2)));
        // Italic
        text = ITALIC.matcher(text).replaceAll(r -> Matcher.quoteReplacement(r.group(1) != null ? r.group(1) : r.group(2)));
        // Strikethrough
        text = STRIKE.matcher(text).replaceAll("$1");
        // Headings, blockquotes, list markers, horizontal rules
        text = HEADING.matcher(text).replaceAll("");
        text = BLOCKQUOTE.matcher(text).replaceAll("");
        text = UNORDERED_LIST.matcher(text).replaceAll("");
        text = ORDERED_LIST.matcher(text).replaceAll("");
        text = HORIZONTAL_RULE.matcher(text).replaceAll("");
        // Collapse excessive blank lines
        text = MULTIPLE_BLANKS.matcher(text).replaceAll("\n\n");

        return text.trim();
    }
}
