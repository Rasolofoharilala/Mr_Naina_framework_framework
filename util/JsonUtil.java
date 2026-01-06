package util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Iterator;

public class JsonUtil {

    public static String toJson(Object obj) {
        StringBuilder sb = new StringBuilder();
        serialize(obj, sb);
        return sb.toString();
    }

    private static void serialize(Object obj, StringBuilder sb) {
        if (obj == null) {
            sb.append("null");
            return;
        }
        if (obj instanceof String) {
            sb.append('"').append(escape((String) obj)).append('"');
            return;
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj.toString());
            return;
        }
        if (obj instanceof Map) {
            sb.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = ((Map<?, ?>) obj).entrySet().iterator();
            boolean first = true;
            while (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append('"').append(':');
                serialize(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (obj instanceof Collection) {
            sb.append('[');
            Iterator<?> it = ((Collection<?>) obj).iterator();
            boolean first = true;
            while (it.hasNext()) {
                if (!first) sb.append(',');
                first = false;
                serialize(it.next(), sb);
            }
            sb.append(']');
            return;
        }
        if (obj.getClass().isArray()) {
            sb.append('[');
            int len = Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(',');
                serialize(Array.get(obj, i), sb);
            }
            sb.append(']');
            return;
        }

        // Fallback: treat as bean, serialize fields
        sb.append('{');
        Field[] fields = obj.getClass().getDeclaredFields();
        boolean first = true;
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Object val = f.get(obj);
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(f.getName())).append('"').append(':');
                serialize(val, sb);
            } catch (Throwable t) {
                // skip problematic fields
            }
        }
        sb.append('}');
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
