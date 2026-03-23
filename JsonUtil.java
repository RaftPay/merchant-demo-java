import java.util.*;

/**
 * 极简 JSON 工具（零第三方依赖）
 *
 * 仅支持本 Demo 所需场景:
 *   - toJson:    Map → JSON 字符串（支持 String/Number/Boolean/null/嵌套 Map）
 *   - parseJson: JSON 字符串 → Map（支持嵌套对象、数组值作为原始字符串保留）
 *
 * 非通用 JSON 库，不建议用于生产环境
 */
public class JsonUtil {

    // ================================================================
    // 序列化: Map → JSON
    // ================================================================

    /**
     * 将 Map 转为 JSON 字符串
     */
    public static String toJson(Map<String, Object> map) {
        if (map == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append('"');
            sb.append(':');
            sb.append(valueToJson(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String valueToJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escapeJson((String) value) + "\"";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Map) return toJson((Map<String, Object>) value);
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ================================================================
    // 反序列化: JSON → Map
    // ================================================================

    /**
     * 将 JSON 字符串解析为 Map
     */
    public static Map<String, Object> parseJson(String json) {
        if (json == null) return null;
        json = json.trim();
        if (!json.startsWith("{")) {
            throw new RuntimeException("JSON 解析失败: 期望 '{' 开头，实际: " + json.substring(0, Math.min(20, json.length())));
        }
        int[] pos = {0};
        return parseObject(json, pos);
    }

    private static Map<String, Object> parseObject(String json, int[] pos) {
        Map<String, Object> map = new LinkedHashMap<>();
        pos[0]++; // 跳过 '{'
        skipWhitespace(json, pos);

        if (pos[0] < json.length() && json.charAt(pos[0]) == '}') {
            pos[0]++;
            return map;
        }

        while (pos[0] < json.length()) {
            skipWhitespace(json, pos);
            String key = parseString(json, pos);
            skipWhitespace(json, pos);
            expect(json, pos, ':');
            skipWhitespace(json, pos);
            Object value = parseValue(json, pos);
            map.put(key, value);
            skipWhitespace(json, pos);
            if (pos[0] < json.length() && json.charAt(pos[0]) == ',') {
                pos[0]++;
            } else {
                break;
            }
        }
        skipWhitespace(json, pos);
        if (pos[0] < json.length() && json.charAt(pos[0]) == '}') {
            pos[0]++;
        }
        return map;
    }

    private static Object parseValue(String json, int[] pos) {
        skipWhitespace(json, pos);
        char c = json.charAt(pos[0]);

        if (c == '"') return parseString(json, pos);
        if (c == '{') return parseObject(json, pos);
        if (c == '[') return parseArray(json, pos);
        if (c == 't' || c == 'f') return parseBoolean(json, pos);
        if (c == 'n') return parseNull(json, pos);
        return parseNumber(json, pos);
    }

    private static String parseString(String json, int[] pos) {
        expect(json, pos, '"');
        StringBuilder sb = new StringBuilder();
        while (pos[0] < json.length()) {
            char c = json.charAt(pos[0]);
            if (c == '\\') {
                pos[0]++;
                char esc = json.charAt(pos[0]);
                switch (esc) {
                    case '"':  sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':
                        String hex = json.substring(pos[0] + 1, pos[0] + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos[0] += 4;
                        break;
                    default:   sb.append(esc);
                }
            } else if (c == '"') {
                pos[0]++;
                return sb.toString();
            } else {
                sb.append(c);
            }
            pos[0]++;
        }
        return sb.toString();
    }

    private static List<Object> parseArray(String json, int[] pos) {
        List<Object> list = new ArrayList<>();
        pos[0]++; // 跳过 '['
        skipWhitespace(json, pos);
        if (pos[0] < json.length() && json.charAt(pos[0]) == ']') {
            pos[0]++;
            return list;
        }
        while (pos[0] < json.length()) {
            skipWhitespace(json, pos);
            list.add(parseValue(json, pos));
            skipWhitespace(json, pos);
            if (pos[0] < json.length() && json.charAt(pos[0]) == ',') {
                pos[0]++;
            } else {
                break;
            }
        }
        skipWhitespace(json, pos);
        if (pos[0] < json.length() && json.charAt(pos[0]) == ']') {
            pos[0]++;
        }
        return list;
    }

    private static Number parseNumber(String json, int[] pos) {
        int start = pos[0];
        boolean isFloat = false;
        if (json.charAt(pos[0]) == '-') pos[0]++;
        while (pos[0] < json.length()) {
            char c = json.charAt(pos[0]);
            if (c == '.' || c == 'e' || c == 'E') isFloat = true;
            if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                pos[0]++;
            } else {
                break;
            }
        }
        // 修正: 首字符是负号时不应重复计入
        String numStr = json.substring(start, pos[0]);
        if (isFloat) return Double.parseDouble(numStr);
        long val = Long.parseLong(numStr);
        if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) return (int) val;
        return val;
    }

    private static Boolean parseBoolean(String json, int[] pos) {
        if (json.startsWith("true", pos[0])) {
            pos[0] += 4;
            return Boolean.TRUE;
        }
        pos[0] += 5;
        return Boolean.FALSE;
    }

    private static Object parseNull(String json, int[] pos) {
        pos[0] += 4;
        return null;
    }

    private static void skipWhitespace(String json, int[] pos) {
        while (pos[0] < json.length() && Character.isWhitespace(json.charAt(pos[0]))) {
            pos[0]++;
        }
    }

    private static void expect(String json, int[] pos, char expected) {
        if (pos[0] >= json.length() || json.charAt(pos[0]) != expected) {
            throw new RuntimeException("JSON 解析失败: 期望 '" + expected + "' 在位置 " + pos[0]);
        }
        pos[0]++;
    }
}
