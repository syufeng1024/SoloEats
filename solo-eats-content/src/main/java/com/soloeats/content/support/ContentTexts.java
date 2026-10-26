package com.soloeats.content.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内容域文本与 JSON 清洗。
 *
 * <p>策略：
 * <ul>
 *   <li>title / update_desc 等纯文本：去标签、去控制字符、&lt; &gt; 转义；</li>
 *   <li>config_json：递归清洗所有字符串节点，富文本只保留白名单标签
 *       p/br/strong/em/ul/ol/li/span，标签属性全部丢弃；</li>
 *   <li>图片地址只接受 t_file 代理前缀或 http(s)，拒绝 javascript: 与尖括号。</li>
 * </ul>
 */
public final class ContentTexts {

    public static final String FILE_PROXY_PREFIX = "/api/v1/files/";

    private static final int MAX_CONFIG_LENGTH = 20_000;

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "p", "br", "strong", "em", "ul", "ol", "li", "span");

    private static final Pattern SCRIPT_BLOCK =
            Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1\\s*>");
    private static final Pattern TAG =
            Pattern.compile("(?is)</?([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");
    private static final Pattern CONTROL =
            Pattern.compile("[\\p{Cntrl}&&[^\\n]]");

    private ContentTexts() {
    }

    /** 单行纯文本：去标签、折叠空白、转义尖括号。 */
    public static String requirePlainText(String value, String field, int maxLength) {
        String text = normalize(value, maxLength, field, false);
        if (!StringUtils.hasText(text)) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        return text;
    }

    /** 可空纯文本，多行保留换行（更新说明）。 */
    public static String optionalMultilineText(String value, String field, int maxLength) {
        String text = normalize(value, maxLength, field, true);
        return StringUtils.hasText(text) ? text : null;
    }

    private static String normalize(String value, int maxLength, String field, boolean multiline) {
        if (value == null) {
            return null;
        }
        String text = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        text = SCRIPT_BLOCK.matcher(text).replaceAll("");
        text = TAG.matcher(text).replaceAll("");
        text = CONTROL.matcher(text).replaceAll(multiline ? "\n" : " ");
        if (!multiline) {
            text = text.replaceAll("\\s+", " ").strip();
        } else {
            text = text.replaceAll("\n{3,}", "\n\n").strip();
        }
        // 转义会放大长度（1 个 < 变成 4 个字符），必须按最终入库值校验列宽。
        String escaped = text.replace("<", "&lt;").replace(">", "&gt;");
        if (escaped.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 最长 " + maxLength + " 个字符");
        }
        return escaped;
    }

    /** 图片地址：只允许 t_file 代理或 http(s) 绝对地址。 */
    public static String requireImageUrl(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        String url = value.strip();
        if (url.length() > 512) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 最长 512 个字符");
        }
        String lower = url.toLowerCase(Locale.ROOT);
        boolean fileProxy = url.startsWith(FILE_PROXY_PREFIX);
        boolean http = lower.startsWith("http://") || lower.startsWith("https://");
        if (!fileProxy && !http) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 必须是 t_file 代理地址或 http(s) 图片地址");
        }
        if (lower.contains("javascript:") || url.contains("<") || url.contains(">")
                || url.contains("\"") || url.contains("'")) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 含有非法字符");
        }
        return url;
    }

    /** 通用下载/跳转地址：http(s) 绝对地址或以 / 开头的站内路径。 */
    public static String requireUrl(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 不能为空");
        }
        String url = value.strip();
        if (url.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 最长 " + maxLength + " 个字符");
        }
        String lower = url.toLowerCase(Locale.ROOT);
        boolean http = lower.startsWith("http://") || lower.startsWith("https://");
        boolean sitePath = url.startsWith("/") && !url.startsWith("//");
        if (!http && !sitePath) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 必须是 http(s) 地址或以 / 开头的站内路径");
        }
        if (lower.contains("javascript:") || url.contains("<") || url.contains(">")
                || url.contains("\"") || url.contains("'")) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 含有非法字符");
        }
        return url;
    }

    /** 搜索词归一：全角转半角、去控制字符、去首尾空白。 */
    public static String normalizeKeyword(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "keyword 不能为空");
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}]", "")
                .strip();
        if (normalized.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "keyword 不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "keyword 最长 " + maxLength + " 个字符");
        }
        return normalized;
    }

    /**
     * 校验并清洗 config_json：必须是 JSON 对象，字符串节点过富文本白名单，返回序列化结果。
     */
    public static String sanitizeConfigJson(ObjectMapper objectMapper, JsonNode config, String field) {
        JsonNode source = config == null ? objectMapper.createObjectNode() : config;
        if (!source.isObject()) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 必须是 JSON 对象");
        }
        JsonNode sanitized = sanitizeNode(source);
        String json;
        try {
            json = objectMapper.writeValueAsString(sanitized);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 序列化失败");
        }
        if (json.length() > MAX_CONFIG_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    field + " 长度不能超过 " + MAX_CONFIG_LENGTH + " 字符");
        }
        return json;
    }

    /** 读取已落库的 config_json；脏数据按空对象处理，不把异常抛给前端。 */
    public static JsonNode readConfig(ObjectMapper objectMapper, String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private static JsonNode sanitizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isTextual()) {
            return TextNode.valueOf(sanitizeRichText(node.asText()));
        }
        if (node.isObject()) {
            ObjectNode copy = ((ObjectNode) node).objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                copy.set(entry.getKey(), sanitizeNode(entry.getValue()));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = ((ArrayNode) node).arrayNode();
            for (JsonNode item : node) {
                copy.add(sanitizeNode(item));
            }
            return copy;
        }
        return node;
    }

    /**
     * 富文本白名单：script/style 整段删除；白名单标签只保留标签名、丢弃全部属性；
     * 其余标签整段删除。文本片段中的裸尖括号转义。
     */
    private static String sanitizeRichText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String cleaned = SCRIPT_BLOCK.matcher(raw).replaceAll("");
        Matcher matcher = TAG.matcher(cleaned);
        StringBuilder out = new StringBuilder(cleaned.length());
        int last = 0;
        while (matcher.find()) {
            appendEscapedText(out, cleaned.substring(last, matcher.start()));
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            boolean closing = matcher.group().startsWith("</");
            if (ALLOWED_TAGS.contains(name)) {
                out.append(closing ? "</" + name + ">" : "<" + name + ">");
            }
            last = matcher.end();
        }
        appendEscapedText(out, cleaned.substring(last));
        return out.toString();
    }

    private static void appendEscapedText(StringBuilder out, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        out.append(text.replace("<", "&lt;").replace(">", "&gt;"));
    }
}
