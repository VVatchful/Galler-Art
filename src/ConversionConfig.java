import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Flat scalar configuration schema; no object construction or YAML tags. */
public final class ConversionConfig {
    private ConversionConfig() { }

    public static Map<String, String> read(Path path) throws Exception {
        String text = Files.readString(path);
        if (text.startsWith("\ufeff")) text = text.substring(1);
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".json")) return new Json(text).object();
        if (!name.endsWith(".yaml") && !name.endsWith(".yml"))
            throw new IllegalArgumentException("Config must be .json, .yaml, or .yml");
        Map<String, String> result = new LinkedHashMap<>();
        boolean documentEnded = false;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (documentEnded) throw new IllegalArgumentException("Only one YAML document is supported");
            if (line.equals("---")) {
                if (!result.isEmpty()) throw new IllegalArgumentException("Only one YAML document is supported");
                continue;
            }
            if (line.equals("...")) { documentEnded = true; continue; }
            int colon = line.indexOf(':');
            if (colon < 1 || !line.substring(0, colon).matches("[A-Za-z][A-Za-z0-9]*"))
                throw new IllegalArgumentException("YAML config requires flat key: value settings: " + line);
            String key = line.substring(0, colon);
            String value = line.substring(colon + 1).strip();
            if (value.startsWith("\"")) {
                Json parser = new Json(value);
                value = parser.string();
                String tail = parser.text.substring(parser.index).strip();
                if (!tail.isEmpty() && !tail.startsWith("#")) throw new IllegalArgumentException("Invalid YAML string");
            } else if (value.startsWith("'")) {
                StringBuilder decoded = new StringBuilder();
                int i = 1;
                boolean ended = false;
                for (; i < value.length(); i++) {
                    char c = value.charAt(i);
                    if (c == '\'') {
                        if (i + 1 < value.length() && value.charAt(i + 1) == '\'') { decoded.append('\''); i++; }
                        else { ended = true; i++; break; }
                    } else decoded.append(c);
                }
                String tail = value.substring(i).strip();
                if (!ended || (!tail.isEmpty() && !tail.startsWith("#"))) throw new IllegalArgumentException("Invalid YAML string");
                value = decoded.toString();
            } else {
                value = value.replaceFirst("\\s+#.*$", "").strip();
                if (value.isEmpty() || value.matches("^[!&*\\[\\]{>|].*") || value.equals("null") || value.equals("~"))
                    throw new IllegalArgumentException("YAML values must be non-null scalars: " + key);
            }
            put(result, key, value);
        }
        return result;
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (map.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate config key: " + key);
    }

    private static final class Json {
        final String text;
        int index;
        Json(String text) { this.text = text; }
        void space() { while (index < text.length() && " \t\r\n".indexOf(text.charAt(index)) >= 0) index++; }
        boolean take(char c) { space(); if (index < text.length() && text.charAt(index) == c) { index++; return true; } return false; }
        void need(char c) { if (!take(c)) throw new IllegalArgumentException("Expected '" + c + "' at config offset " + index); }
        String string() {
            need('"');
            StringBuilder value = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') return value.toString();
                if (c < 32) throw new IllegalArgumentException("Control character in JSON string");
                if (c == '\\') {
                    if (index == text.length()) break;
                    char escape = text.charAt(index++);
                    switch (escape) {
                        case '"': case '\\': case '/': value.append(escape); break;
                        case 'b': value.append('\b'); break;
                        case 'f': value.append('\f'); break;
                        case 'n': value.append('\n'); break;
                        case 'r': value.append('\r'); break;
                        case 't': value.append('\t'); break;
                        case 'u':
                            if (index + 4 > text.length()) throw new IllegalArgumentException("Incomplete Unicode escape");
                            value.append((char) Integer.parseInt(text.substring(index, index + 4), 16)); index += 4; break;
                        default: throw new IllegalArgumentException("Invalid JSON escape");
                    }
                } else value.append(c);
            }
            throw new IllegalArgumentException("Unterminated config string");
        }
        Map<String, String> object() {
            Map<String, String> values = new LinkedHashMap<>();
            need('{');
            if (!take('}')) {
                do {
                    String key = string(); need(':'); space();
                    String value;
                    if (index < text.length() && text.charAt(index) == '"') value = string();
                    else {
                        int start = index;
                        while (index < text.length() && ",} \t\r\n".indexOf(text.charAt(index)) < 0) index++;
                        value = text.substring(start, index);
                        if (!value.matches("true|false|-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))
                            throw new IllegalArgumentException("JSON config values must be strings, numbers, or booleans: " + key);
                    }
                    put(values, key, value);
                } while (take(','));
                need('}');
            }
            space();
            if (index != text.length()) throw new IllegalArgumentException("Unexpected content after JSON config");
            return values;
        }
    }
}
