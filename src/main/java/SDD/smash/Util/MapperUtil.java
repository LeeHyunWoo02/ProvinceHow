package SDD.smash.Util;

import com.fasterxml.jackson.databind.JsonNode;

public class MapperUtil {
    /**
     * json 에서 문자열 값을 꺼내는 메소드
     * String... -> 가변인자 문법 / String[] 와 동일하지만, 호출할 때 배열을 직접 만들지 않아도 여러 개의 인자를 넘길 수 있음.
     * */
    public static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) return value.asText();
        }
        return null;
    }

    /**
     * json 에서 정수 값을 꺼내는 메소드
     * */
    public static Integer num(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                String s = value.asText().replaceAll("[^0-9-]", "");
                try { return Integer.parseInt(s); } catch (Exception ignore) {}
            }
        }
        return null;
    }

    public static String extractJson(String raw) {
        int s = raw.indexOf('{');
        int e = raw.lastIndexOf('}');
        if (s == -1 || e == -1 || s > e) {
            return null;
        }
        return raw.substring(s, e + 1).trim();
    }
}
