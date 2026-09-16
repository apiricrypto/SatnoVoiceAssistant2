package ir.satno.voiceassistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class TextTools {
    private TextTools() {
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String text = value
                .replace('ي', 'ی')
                .replace('ك', 'ک')
                .replace('ۀ', 'ه')
                .replace('ة', 'ه')
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .toLowerCase(Locale.ROOT);

        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '۰' && c <= '۹') {
                out.append((char) ('0' + (c - '۰')));
            } else if (c >= '٠' && c <= '٩') {
                out.append((char) ('0' + (c - '٠')));
            } else if (Character.isLetterOrDigit(c) || Character.isWhitespace(c) || c == '+') {
                out.append(c);
            } else {
                out.append(' ');
            }
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    static String phoneKey(String phone) {
        String normalized = normalize(phone);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isDigit(c) || c == '+') {
                out.append(c);
            }
        }
        String value = out.toString();
        if (value.startsWith("+98")) {
            return "0" + value.substring(3);
        }
        if (value.startsWith("0098")) {
            return "0" + value.substring(4);
        }
        return value;
    }

    static List<String> tokens(String value) {
        String normalized = normalize(value);
        List<String> result = new ArrayList<>();
        if (normalized.isEmpty()) {
            return result;
        }
        for (String part : normalized.split(" ")) {
            if (part.length() > 1) {
                result.add(part);
            }
        }
        return result;
    }

    static String removeLeadingNoise(String value) {
        String text = normalize(value);
        String[] noise = {"لطفا", "لطفاً", "یه", "یک", "برای", "به", "با"};
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String word : noise) {
                if (text.equals(word)) {
                    return "";
                }
                if (text.startsWith(word + " ")) {
                    text = text.substring(word.length()).trim();
                    changed = true;
                }
            }
        }
        return text;
    }
}
