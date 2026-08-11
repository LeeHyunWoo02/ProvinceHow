package SDD.smash.common.util;


public class BatchTextUtil {
    public static String addLeadingZero(String code) {
        if (code == null) return null;
        code = code.replace("\uFEFF", "").trim();

        if (code.matches("\\d+")) {
            int num = Integer.parseInt(code);
            if (num < 10) {
                return String.format("%02d", num);
            }
            return String.valueOf(num);
        }
        return code;
    }

    public static String addLeadingZeroThird(String code) {
        if (code == null) return null;
        code = code.replace("\uFEFF", "").trim();

        if (code.matches("\\d+")) {
            int num = Integer.parseInt(code);
            if (num < 100) {
                return String.format("%03d", num);
            }
            return String.valueOf(num);
        }
        return code;
    }
    /**
     * 보이지 않는 공백들 제거
     * */
    public static String normalize(String s) {
        if (s == null) return null;
        s = s.replace("\uFEFF", ""); // BOM 제거
        s = s.replaceAll("[\\u200B-\\u200D\\u2060]", ""); // 제로폭 문자제거
        s = s.replace('\u00A0', ' '); // NBSP→공백
        s = s.replace('\r', ' ').replace('\t', ' ');
        s = s.replaceAll("^\"|\"$", "").trim(); // 따옴표 삭제
        return s.isEmpty() ? "" : s;
    }

    public static boolean isBlank(String s) {
        return s == null || normalize(s).isEmpty();
    }

    /**
     * 숫자만 10,000 -> 10000
     * */
    public static String digitsOnly(String s) {
        if (s == null) return null;
        s = normalize(s).replace(",", "");

        return s;
    }

    public static int nullZero(Integer value) { return value == null ? 0 : value; }


}
