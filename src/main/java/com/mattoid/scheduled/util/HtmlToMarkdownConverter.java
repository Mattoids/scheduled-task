package com.mattoid.scheduled.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * HTML to Markdown converter utility
 */
public class HtmlToMarkdownConverter {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[a-zA-Z][^>]*>");

    /**
     * Convert HTML content to markdown format.
     * Plain text without HTML tags is returned as-is.
     */
    public static String convert(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        if (!HTML_TAG_PATTERN.matcher(html).find()) {
            return html.trim();
        }
        try {
            Element body = Jsoup.parseBodyFragment(html).body();
            String markdown = convertChildren(body).trim();
            return markdown.isEmpty() ? html.trim() : markdown;
        } catch (Exception e) {
            // Fallback: return raw text
            return html;
        }
    }

    private static String convertChildren(Node node) {
        StringBuilder sb = new StringBuilder();
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode textNode) {
                sb.append(textNode.getWholeText());
            } else if (child instanceof Element element) {
                sb.append(convertElement(element));
            }
        }
        return sb.toString();
    }

    private static String convertElement(Element element) {
        String tagName = element.tagName().toLowerCase();
        String children = convertChildren(element);

        return switch (tagName) {
            case "h1" -> "# " + children.trim() + "\n";
            case "h2" -> "## " + children.trim() + "\n";
            case "h3" -> "### " + children.trim() + "\n";
            case "h4", "h5", "h6" -> "**" + children.trim() + "**\n";
            case "p" -> children.trim() + "\n";
            case "br" -> "\n";
            case "hr" -> "\n---\n";
            case "ul", "ol" -> convertList(element, "ol".equals(tagName));
            case "li" -> "- " + children.trim() + "\n";
            case "blockquote" -> convertBlockquote(children);
            case "pre" -> convertPre(element);
            case "code" -> "`" + children + "`";
            case "a" -> "[" + children + "](" + element.attr("href") + ")";
            case "b", "strong" -> "**" + children + "**";
            case "i", "em" -> "*" + children + "*";
            case "strike", "del", "s" -> "~~" + children + "~~";
            case "u" -> children;
            case "img" -> "![" + element.attr("alt") + "](" + element.attr("src") + ")";
            case "table" -> convertTable(element) + "\n";
            case "html", "head", "body", "div", "span", "section", "article", "main" -> children;
            default -> element.isBlock() ? children.trim() + "\n" : children;
        };
    }

    private static String convertList(Element list, boolean ordered) {
        StringBuilder sb = new StringBuilder();
        List<Element> items = list.children().stream()
                .filter(child -> "li".equals(child.tagName().toLowerCase()))
                .toList();
        int index = 1;
        for (Element item : items) {
            String itemContent = convertChildren(item).trim();
            if (!StringUtils.hasText(itemContent)) {
                continue;
            }
            String prefix = ordered ? (index++) + ". " : "- ";
            String[] lines = itemContent.split("\n", -1);
            sb.append(prefix).append(lines[0]).append("\n");
            for (int i = 1; i < lines.length; i++) {
                if (!lines[i].isEmpty()) {
                    sb.append("  ").append(lines[i]).append("\n");
                } else {
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }

    private static String convertBlockquote(String children) {
        String content = children.trim();
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append("> ").append(line).append("\n");
        }
        return sb.toString();
    }

    private static String convertPre(Element element) {
        Elements codeElements = element.select("code");
        String codeText = codeElements.isEmpty() ? element.text() : codeElements.first().text();
        return "```\n" + codeText + "\n```\n";
    }

    private static String convertTable(Element table) {
        StringBuilder sb = new StringBuilder();
        Elements rows = table.select("tr");
        if (rows.isEmpty()) {
            return "";
        }

        int colCount = 0;
        for (Element row : rows) {
            Elements cells = row.select("th, td");
            if (cells.size() > colCount) {
                colCount = cells.size();
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements cells = row.select("th, td");

            for (Element cell : cells) {
                sb.append("| ").append(cell.text().trim()).append(" ");
            }
            for (int j = cells.size(); j < colCount; j++) {
                sb.append("| ");
            }
            sb.append("|\n");

            if (i == 0) {
                for (int j = 0; j < colCount; j++) {
                    sb.append("| ---");
                }
                sb.append("|\n");
            }
        }
        return sb.toString();
    }
}
