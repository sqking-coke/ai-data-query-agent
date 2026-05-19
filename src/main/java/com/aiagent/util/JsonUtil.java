package com.aiagent.util;

/**
 * JSON工具类
 */
public class JsonUtil {

    /**
     * 从一段文本中提取JSON子串
     * <p>
     * 大模型有时会在JSON外包裹说明文字（如 "好的，这是结果：\n{...}"），
     * 需要从中提取纯净的JSON部分。
     *
     * @param text 可能包含额外文字的响应文本
     * @return 提取出的JSON字符串（从第一个 { 到最后一个 }）
     * @throws IllegalArgumentException 未找到有效的JSON对象时抛出
     */
    public static String extractJson(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("输入文本为空");
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');

        if (start == -1) {
            throw new IllegalArgumentException("未找到JSON起始标记 '{': " + text);
        }
        if (end == -1 || end <= start) {
            throw new IllegalArgumentException("未找到JSON结束标记 '}': " + text);
        }

        return text.substring(start, end + 1);
    }

    /**
     * 判断字符串是否为合法JSON
     */
    public static boolean isJson(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
}