package com.knowledgeforge.system.utils;

public final class FileTypeUtil {

    private FileTypeUtil() {
    }

    public static String detectByExtension(String fileName) {
        if (fileName == null) return "OTHER";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".docx")) return "WORD";
        if (lower.endsWith(".doc")) return "WORD";
        if (lower.endsWith(".md")) return "MARKDOWN";
        if (lower.endsWith(".markdown")) return "MARKDOWN";
        if (lower.endsWith(".txt")) return "TEXT";
        return "OTHER";
    }

    public static boolean isSupportedFormat(String fileName) {
        return !"OTHER".equals(detectByExtension(fileName));
    }

    public static String toDisplayName(String fileType) {
        return switch (fileType) {
            case "PDF" -> "PDF 文档";
            case "WORD" -> "Word 文档";
            case "MARKDOWN" -> "Markdown 文档";
            case "TEXT" -> "纯文本文档";
            default -> "未知格式";
        };
    }
}