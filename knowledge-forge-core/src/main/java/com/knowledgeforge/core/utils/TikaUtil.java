package com.knowledgeforge.core.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;

import java.io.InputStream;

@Slf4j
public final class TikaUtil {

    private static final Tika TIKA = new Tika();

    private TikaUtil() {
    }

    public static String detectMimeType(String fileName) {
        return TIKA.detect(fileName);
    }

    public static String detectMimeType(InputStream inputStream) {
        try {
            return TIKA.detect(inputStream);
        } catch (Exception e) {
            log.warn("文件类型检测失败", e);
            return MediaType.OCTET_STREAM.toString();
        }
    }

    public static boolean isTextDocument(String fileName) {
        String mime = detectMimeType(fileName);
        return mime.contains("pdf") || mime.contains("msword")
                || mime.contains("officedocument") || mime.contains("text")
                || mime.contains("markdown");
    }

    public static boolean isPdf(String mimeType) {
        return mimeType != null && mimeType.equals("application/pdf");
    }

    public static boolean isWord(String mimeType) {
        return mimeType != null && (mimeType.contains("officedocument.word")
                || mimeType.contains("msword"));
    }
}