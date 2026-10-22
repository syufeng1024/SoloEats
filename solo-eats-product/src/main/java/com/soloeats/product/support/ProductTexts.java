package com.soloeats.product.support;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商品域文本与图片地址校验。
 *
 * <p>XSS 采用「白名单标签 + 仅保留 img.src」策略：富文本只允许
 * p/br/img/strong/em/ul/ol/li，其余标签整段删除；纯文本字段统一转义。
 * 图片只接受 T03 文件代理地址，或显式配置的外部域名前缀。
 */
public final class ProductTexts {

    public static final String FILE_PROXY_PREFIX = "/api/v1/files/";

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "p", "br", "img", "strong", "em", "ul", "ol", "li");

    private static final Pattern SCRIPT_BLOCK =
            Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1\\s*>");
    private static final Pattern TAG =
            Pattern.compile("(?is)</?([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");
    private static final Pattern ATTR =
            Pattern.compile("(?is)\\b([a-zA-Z-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

    private ProductTexts() {
    }

    /** 富文本白名单过滤；返回 null 表示空内容。 */
    public static String sanitizeHtml(String html) {
        if (!StringUtils.hasText(html)) {
            return null;
        }
        String cleaned = SCRIPT_BLOCK.matcher(html).replaceAll("");
        Matcher matcher = TAG.matcher(cleaned);
        StringBuilder out = new StringBuilder(cleaned.length());
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            boolean closing = matcher.group().startsWith("</");
            if (!ALLOWED_TAGS.contains(name)) {
                matcher.appendReplacement(out, "");
                continue;
            }
            if ("img".equals(name)) {
                if (closing) {
                    matcher.appendReplacement(out, "");
                    continue;
                }
                String src = attribute(matcher.group(2), "src");
                if (!StringUtils.hasText(src)) {
                    matcher.appendReplacement(out, "");
                    continue;
                }
                matcher.appendReplacement(out, Matcher.quoteReplacement(
                        "<img src=\"" + escapeAttribute(src.trim()) + "\" alt=\"\">"));
                continue;
            }
            matcher.appendReplacement(out, closing ? "</" + name + ">" : "<" + name + ">");
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** 纯文本入库前转义，避免前端直接渲染时执行标签。 */
    public static String escapeText(String text, int maxLength, String fieldLabel) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    fieldLabel + " 长度不能超过 " + maxLength);
        }
        return trimmed.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 校验商品图/评价图：只允许 T03 文件代理地址或配置的外部域名前缀。
     */
    public static String requireImage(String url, ProductProperties properties, String fieldLabel) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String value = url.trim();
        if (value.length() > 512) {
            throw new BizException(ErrorCode.PARAM_INVALID, fieldLabel + " 长度不能超过 512");
        }
        if (isFileProxy(value) || matchesAllowlist(value, properties)) {
            return value;
        }
        throw new BizException(ErrorCode.PARAM_INVALID,
                fieldLabel + " 必须是 t_file 代理地址或配置允许的图片域名");
    }

    public static List<String> requireImages(List<String> urls, ProductProperties properties,
                                             String fieldLabel, int maxCount) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        if (urls.size() > maxCount) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    fieldLabel + " 最多 " + maxCount + " 张");
        }
        List<String> values = new ArrayList<>(urls.size());
        for (String url : urls) {
            String checked = requireImage(url, properties, fieldLabel);
            if (checked != null) {
                values.add(checked);
            }
        }
        return values;
    }

    private static boolean isFileProxy(String url) {
        return url.startsWith(FILE_PROXY_PREFIX);
    }

    private static boolean matchesAllowlist(String url, ProductProperties properties) {
        String allowlist = properties == null ? null : properties.getImageHostAllowlist();
        if (!StringUtils.hasText(allowlist)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        if (lower.contains("javascript:") || lower.contains("<") || lower.contains(">")) {
            return false;
        }
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (host == null) {
            return false;
        }
        for (String prefix : Arrays.stream(allowlist.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList()) {
            String normalized = prefix.toLowerCase(Locale.ROOT);
            if (host.equals(normalized) || host.endsWith("." + normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String attribute(String attrText, String name) {
        if (!StringUtils.hasText(attrText)) {
            return null;
        }
        Matcher matcher = ATTR.matcher(attrText);
        while (matcher.find()) {
            if (name.equalsIgnoreCase(matcher.group(1))) {
                String doubleQuoted = matcher.group(3);
                String singleQuoted = matcher.group(4);
                String bare = matcher.group(5);
                return doubleQuoted != null ? doubleQuoted : singleQuoted != null ? singleQuoted : bare;
            }
        }
        return null;
    }

    private static String escapeAttribute(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
