package ir.satno.voiceassistant;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ContactFinder {
    private final ContentResolver resolver;

    ContactFinder(ContentResolver resolver) {
        this.resolver = resolver;
    }

    List<ContactMatch> find(String spokenName) {
        String query = TextTools.removeLeadingNoise(spokenName);
        List<String> queryTokens = TextTools.tokens(query);
        List<ContactMatch> matches = new ArrayList<>();
        if (queryTokens.isEmpty()) return matches;

        Cursor cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                null,
                null,
                null
        );
        if (cursor == null) return matches;

        Map<String, ContactMatch> bestByPhone = new HashMap<>();
        try {
            int nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int phoneIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);

            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                String phone = cursor.getString(phoneIndex);
                int score = score(name, query, queryTokens);
                if (score <= 0) continue;

                String phoneKey = TextTools.phoneKey(phone);
                ContactMatch candidate = new ContactMatch(name, phone, score);
                ContactMatch existing = bestByPhone.get(phoneKey);
                if (existing == null || candidate.score > existing.score) {
                    bestByPhone.put(phoneKey, candidate);
                }
            }
        } finally {
            cursor.close();
        }

        matches.addAll(bestByPhone.values());
        matches.sort(Comparator.comparingInt((ContactMatch item) -> item.score).reversed());
        return matches;
    }

    private static int score(String contactName, String query, List<String> queryTokens) {
        String normalizedName = TextTools.normalize(contactName);
        String normalizedQuery = TextTools.normalize(query);

        if (normalizedName.equals(normalizedQuery)) return 1200;
        if (normalizedName.contains(normalizedQuery)) return 900 + normalizedQuery.length();

        String nameCompact = compact(normalizedName);
        String queryCompact = compact(normalizedQuery);
        if (!queryCompact.isEmpty() && nameCompact.contains(queryCompact)) return 820;

        String nameLatin = latinKey(normalizedName);
        String queryLatin = latinKey(normalizedQuery);
        if (!queryLatin.isEmpty() && nameLatin.contains(queryLatin)) return 780;

        int score = 0;
        List<String> nameTokens = TextTools.tokens(normalizedName);

        for (String queryToken : queryTokens) {
            String qLatin = latinKey(queryToken);
            String qCompact = compact(queryToken);

            for (String nameToken : nameTokens) {
                String nLatin = latinKey(nameToken);
                String nCompact = compact(nameToken);

                if (nameToken.equals(queryToken)) score += 220;
                else if (nameToken.startsWith(queryToken) || queryToken.startsWith(nameToken)) score += 130;
                else if (nameToken.contains(queryToken) || queryToken.contains(nameToken)) score += 80;
                else if (!qLatin.isEmpty() && (nLatin.contains(qLatin) || qLatin.contains(nLatin))) score += 120;
                else if (!qCompact.isEmpty() && (nCompact.contains(qCompact) || qCompact.contains(nCompact))) score += 90;
            }
        }

        return score >= 90 ? score : 0;
    }

    private static String compact(String value) {
        return TextTools.normalize(value).replace(" ", "").replaceAll("[aeiou]", "");
    }

    private static String latinKey(String value) {
        String text = TextTools.normalize(value);
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case 'ا': case 'آ': case 'ع': out.append('a'); break;
                case 'ب': out.append('b'); break;
                case 'پ': out.append('p'); break;
                case 'ت': case 'ط': out.append('t'); break;
                case 'ث': case 'س': case 'ص': out.append('s'); break;
                case 'ج': out.append('j'); break;
                case 'چ': out.append("ch"); break;
                case 'ح': case 'ه': out.append('h'); break;
                case 'خ': out.append("kh"); break;
                case 'د': out.append('d'); break;
                case 'ذ': case 'ز': case 'ض': case 'ظ': out.append('z'); break;
                case 'ر': out.append('r'); break;
                case 'ژ': out.append("zh"); break;
                case 'ش': out.append("sh"); break;
                case 'غ': case 'ق': out.append("gh"); break;
                case 'ف': out.append('f'); break;
                case 'ک': out.append('k'); break;
                case 'گ': out.append('g'); break;
                case 'ل': out.append('l'); break;
                case 'م': out.append('m'); break;
                case 'ن': out.append('n'); break;
                case 'و': out.append('v'); break;
                case 'ی': out.append('i'); break;
                default:
                    if ((c >= 'a' && c <= 'z') || Character.isDigit(c)) out.append(c);
            }
        }

        return out.toString().replaceAll("[aeiou]", "");
    }
}