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
        if (queryTokens.isEmpty()) {
            return matches;
        }

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
        if (cursor == null) {
            return matches;
        }

        Map<String, ContactMatch> bestByPhone = new HashMap<>();
        try {
            int nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int phoneIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                String phone = cursor.getString(phoneIndex);
                int score = score(name, query, queryTokens);
                if (score <= 0) {
                    continue;
                }
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
        if (normalizedName.isEmpty()) {
            return 0;
        }
        if (normalizedName.equals(query)) {
            return 1000;
        }
        if (normalizedName.contains(query)) {
            return 750 + query.length();
        }

        List<String> nameTokens = TextTools.tokens(normalizedName);
        int score = 0;
        for (String queryToken : queryTokens) {
            for (String nameToken : nameTokens) {
                if (nameToken.equals(queryToken)) {
                    score += 180;
                } else if (nameToken.startsWith(queryToken) || queryToken.startsWith(nameToken)) {
                    score += 95;
                } else if (nameToken.contains(queryToken) || queryToken.contains(nameToken)) {
                    score += 55;
                }
            }
        }
        return score >= 90 ? score : 0;
    }
}
