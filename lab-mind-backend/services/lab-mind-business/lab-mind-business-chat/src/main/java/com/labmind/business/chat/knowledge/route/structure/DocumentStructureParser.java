package com.labmind.business.chat.knowledge.route.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DocumentStructureParser {

    public static final int NODE_TYPE_DOCUMENT = 1;

    public static final int NODE_TYPE_SECTION = 2;

    public static final int NODE_TYPE_ITEM = 4;

    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    private static final Pattern NUMBERED_HEADING_PATTERN = Pattern.compile(
            "^((?:\\d+\\.)*\\d+)[、.．\\s]+(.+)$");

    private static final Pattern CHINESE_HEADING_PATTERN = Pattern.compile(
            "^(第[一二三四五六七八九十百千万0-9]+[章节篇部分])[、.．\\s]*(.+)$");

    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile(
            "^((?:[-*+]\\s+)|(?:\\d+[.)、]\\s+)|(?:[（(]?[一二三四五六七八九十]+[）)、.]\\s+))(.+)$");

    /**
     * 将解析后的纯文本转换为文档结构节点草稿。
     *
     * <p>这里直接从文本中的标题编号、Markdown 标题和列表项识别结构，不调用模型生成结构，
     * 保证同一份文本重复解析时节点顺序和路径稳定。</p>
     */
    public List<DocumentStructureNodeDraft> parse(String documentName, String parsedText) {
        String normalizedText = normalizeRequiredText(parsedText, "parsedText");
        String rootTitle = normalizeRequiredText(documentName, "documentName");
        List<DocumentStructureNodeDraft> drafts = new ArrayList<>();
        drafts.add(new DocumentStructureNodeDraft(
                1,
                NODE_TYPE_DOCUMENT,
                null,
                0,
                null,
                rootTitle,
                rootTitle,
                rootTitle,
                rootTitle,
                normalizedText,
                null));

        List<SectionCursor> sectionStack = new ArrayList<>();
        StringBuilder pendingContent = new StringBuilder();
        int currentSectionNodeNo = 1;
        int nodeNo = 1;
        int itemIndex = 0;
        for (String rawLine : normalizedText.split("\\R")) {
            String line = rawLine.strip();
            if (!StringUtils.hasText(line)) {
                appendPendingLine(pendingContent, "");
                continue;
            }
            Heading heading = readHeading(line);
            if (heading != null) {
                flushContent(drafts, currentSectionNodeNo, pendingContent);
                while (!sectionStack.isEmpty() && sectionStack.get(sectionStack.size() - 1).depth() >= heading.depth()) {
                    sectionStack.remove(sectionStack.size() - 1);
                }
                Integer parentNodeNo = sectionStack.isEmpty()
                        ? 1
                        : sectionStack.get(sectionStack.size() - 1).nodeNo();
                String parentPath = sectionStack.isEmpty()
                        ? rootTitle
                        : sectionStack.get(sectionStack.size() - 1).canonicalPath();
                nodeNo++;
                String canonicalPath = parentPath + "/" + heading.title();
                drafts.add(new DocumentStructureNodeDraft(
                        nodeNo,
                        NODE_TYPE_SECTION,
                        parentNodeNo,
                        heading.depth(),
                        heading.nodeCode(),
                        heading.title(),
                        heading.title(),
                        canonicalPath,
                        canonicalPath,
                        "",
                        null));
                sectionStack.add(new SectionCursor(nodeNo, heading.depth(), canonicalPath));
                currentSectionNodeNo = nodeNo;
                itemIndex = 0;
                continue;
            }
            Matcher listItemMatcher = LIST_ITEM_PATTERN.matcher(line);
            if (listItemMatcher.matches()) {
                flushContent(drafts, currentSectionNodeNo, pendingContent);
                itemIndex++;
                String content = normalizeRequiredText(listItemMatcher.group(2), "item content");
                String parentPath = sectionStack.isEmpty()
                        ? rootTitle
                        : sectionStack.get(sectionStack.size() - 1).canonicalPath();
                String anchorText = limitText(content, 80);
                nodeNo++;
                drafts.add(new DocumentStructureNodeDraft(
                        nodeNo,
                        NODE_TYPE_ITEM,
                        currentSectionNodeNo,
                        sectionStack.isEmpty() ? 1 : sectionStack.get(sectionStack.size() - 1).depth() + 1,
                        String.valueOf(itemIndex),
                        anchorText,
                        anchorText,
                        parentPath + "/" + itemIndex,
                        parentPath,
                        content,
                        itemIndex));
                continue;
            }
            appendPendingLine(pendingContent, line);
        }
        flushContent(drafts, currentSectionNodeNo, pendingContent);
        return drafts;
    }

    /**
     * 识别一行文本是否是章节标题。
     *
     * <p>支持 Markdown 标题、数字层级标题和中文章节标题。普通业务正文不会被提升为章节节点。</p>
     */
    private Heading readHeading(String line) {
        Matcher markdownMatcher = MARKDOWN_HEADING_PATTERN.matcher(line);
        if (markdownMatcher.matches()) {
            return new Heading(markdownMatcher.group(1).length(), null,
                    normalizeRequiredText(markdownMatcher.group(2), "heading"));
        }
        Matcher numberedMatcher = NUMBERED_HEADING_PATTERN.matcher(line);
        if (numberedMatcher.matches()) {
            String nodeCode = numberedMatcher.group(1);
            int depth = (int) nodeCode.chars().filter(ch -> ch == '.').count() + 1;
            return new Heading(depth, nodeCode, normalizeRequiredText(numberedMatcher.group(2), "heading"));
        }
        Matcher chineseMatcher = CHINESE_HEADING_PATTERN.matcher(line);
        if (chineseMatcher.matches()) {
            return new Heading(1, chineseMatcher.group(1),
                    normalizeRequiredText(chineseMatcher.group(1) + " " + chineseMatcher.group(2), "heading"));
        }
        return null;
    }

    private void appendPendingLine(StringBuilder builder, String line) {
        if (!builder.isEmpty()) {
            builder.append("\n");
        }
        builder.append(line);
    }

    /**
     * 将连续正文写回当前章节节点。
     *
     * <p>正文属于最近的章节；如果文档没有显式章节，则挂在文档根节点上。</p>
     */
    private void flushContent(
            List<DocumentStructureNodeDraft> drafts,
            int currentSectionNodeNo,
            StringBuilder pendingContent) {
        if (pendingContent.isEmpty()) {
            return;
        }
        String contentText = pendingContent.toString().strip();
        pendingContent.setLength(0);
        if (!StringUtils.hasText(contentText)) {
            return;
        }
        for (int index = drafts.size() - 1; index >= 0; index--) {
            DocumentStructureNodeDraft draft = drafts.get(index);
            if (draft.nodeNo() == currentSectionNodeNo) {
                drafts.set(index, new DocumentStructureNodeDraft(
                        draft.nodeNo(),
                        draft.nodeType(),
                        draft.parentNodeNo(),
                        draft.depth(),
                        draft.nodeCode(),
                        draft.title(),
                        draft.anchorText(),
                        draft.canonicalPath(),
                        draft.sectionPath(),
                        mergeContent(draft.contentText(), contentText),
                        draft.itemIndex()));
                return;
            }
        }
    }

    private String mergeContent(String left, String right) {
        if (!StringUtils.hasText(left)) {
            return right;
        }
        return left.strip() + "\n" + right;
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalized = value == null ? null : value.strip();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private String limitText(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars).strip();
    }

    private record Heading(int depth, String nodeCode, String title) {
    }

    private record SectionCursor(int nodeNo, int depth, String canonicalPath) {
    }
}
