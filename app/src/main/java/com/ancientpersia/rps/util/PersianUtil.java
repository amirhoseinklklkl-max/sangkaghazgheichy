package com.ancientpersia.rps.util;

/**
 * تبدیل ارقام لاتین به فارسی.
 * این کلاس عمداً با Java نوشته شده تا همکاری Java + Kotlin در پروژه حفظ شود.
 */
public final class PersianUtil {

    private static final String[] FA = {
            "۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹"
    };

    private PersianUtil() {
    }

    public static String toFa(int n) {
        return toFa(String.valueOf(n));
    }

    public static String toFa(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') {
                sb.append(FA[c - '0']);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
