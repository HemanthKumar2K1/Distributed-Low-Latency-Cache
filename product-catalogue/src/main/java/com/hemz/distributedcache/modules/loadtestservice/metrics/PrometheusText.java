package com.hemz.distributedcache.modules.loadtestservice.metrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small parser for the Prometheus text format that /actuator/prometheus returns.
 *
 * Label values are parsed properly, with quotes and escapes, because real values
 * contain braces and commas: uri="/api/products/{id}". Splitting on '{' would break.
 */
public final class PrometheusText {

    public record Metric(String name, Map<String, String> labels, double value) {
        public String label(String key) {
            return labels.getOrDefault(key, "");
        }
    }

    private PrometheusText() {
    }

    public static List<Metric> parse(String text) {
        List<Metric> out = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Metric m = parseLine(line);
            if (m != null) {
                out.add(m);
            }
        }
        return out;
    }

    static Metric parseLine(String line) {
        int n = line.length();
        int i = 0;
        while (i < n && line.charAt(i) != '{' && !Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        String name = line.substring(0, i);
        if (name.isEmpty()) {
            return null;
        }
        Map<String, String> labels = new LinkedHashMap<>();
        if (i < n && line.charAt(i) == '{') {
            i++;
            while (i < n && line.charAt(i) != '}') {
                char c = line.charAt(i);
                if (c == ',' || Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                int eq = line.indexOf('=', i);
                if (eq < 0) {
                    return null;
                }
                String key = line.substring(i, eq).strip();
                i = eq + 1;
                if (i >= n || line.charAt(i) != '"') {
                    return null;
                }
                i++;
                StringBuilder value = new StringBuilder();
                while (i < n && line.charAt(i) != '"') {
                    char ch = line.charAt(i);
                    if (ch == '\\' && i + 1 < n) {
                        char esc = line.charAt(++i);
                        value.append(esc == 'n' ? '\n' : esc);
                    } else {
                        value.append(ch);
                    }
                    i++;
                }
                i++;                           // closing quote
                labels.put(key, value.toString());
            }
            i++;                               // closing brace
        }
        if (i >= n) {
            return null;
        }
        String rest = line.substring(i).strip();
        if (rest.isEmpty()) {
            return null;
        }
        try {
            return new Metric(name, labels, parseValue(rest.split("\\s+")[0]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static double parseValue(String s) {
        return switch (s) {
            case "+Inf", "Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            case "NaN" -> Double.NaN;
            default -> Double.parseDouble(s);
        };
    }
}
