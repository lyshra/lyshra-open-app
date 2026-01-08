package com.lyshra.open.app.core.util;

public class CastUtil {
    
    private CastUtil() {}

    public static Boolean castAsBoolean(Object e) {
        switch (e) {
            case Boolean bool -> {
                return bool;
            }
            case Number number -> {
                return number.intValue() == 1;
            }
            case String s -> {
                if ("true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s) || "1".equals(e)) {
                    return true;
                } else if ("false".equalsIgnoreCase(s) || "no".equalsIgnoreCase(s) || "0".equals(e)) {
                    return false;
                } else {
                    return false;
                }
            }
            case null, default -> {
                return false;
            }
        }
    }

    public static int castAsInteger(Object e) {
        if (e instanceof Number number) {
            return number.intValue();
        } else if (e instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return Integer.parseInt(e.toString());
    }

    public static double castAsDouble(Object e) {
        if (e instanceof Number number) {
            return number.doubleValue();
        } else if (e instanceof Boolean bool) {
            return bool ? 1.0 : 0.0;
        }
        return Double.parseDouble(e.toString());
    }

    public static long castAsLong(Object e) {
        if (e instanceof Number number) {
            return number.longValue();
        } else if (e instanceof Boolean bool) {
            return bool ? 1L : 0L;
        }
        return Long.parseLong(e.toString());
    }

    public static String castAsString(Object e) {
        if (e instanceof String string) {
            return string;
        }
        return e.toString();
    }
}
