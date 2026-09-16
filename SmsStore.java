package ir.satno.voiceassistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class SmsStore {
    private static final String PREFS = "sms_store";
    private static final String KEY_MESSAGES = "messages";
    private static final int LIMIT = 30;

    private SmsStore() {
    }

    static void saveIncoming(Context context, String from, String body, long timestamp) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray messages = readArray(prefs);
        JSONArray next = new JSONArray();

        try {
            JSONObject item = new JSONObject();
            item.put("from", from);
            item.put("fromKey", TextTools.phoneKey(from));
            item.put("body", body);
            item.put("timestamp", timestamp);
            next.put(item);

            for (int i = 0; i < messages.length() && next.length() < LIMIT; i++) {
                next.put(messages.getJSONObject(i));
            }
        } catch (JSONException ignored) {
            return;
        }

        prefs.edit().putString(KEY_MESSAGES, next.toString()).apply();
    }

    static StoredSms latest(Context context) {
        List<StoredSms> messages = all(context);
        return messages.isEmpty() ? null : messages.get(0);
    }

    static StoredSms latestFrom(Context context, String phone) {
        String key = TextTools.phoneKey(phone);
        for (StoredSms item : all(context)) {
            if (TextTools.phoneKey(item.from).equals(key)) {
                return item;
            }
        }
        return null;
    }

    private static List<StoredSms> all(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray array = readArray(prefs);
        List<StoredSms> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject item = array.getJSONObject(i);
                result.add(new StoredSms(
                        item.optString("from"),
                        item.optString("body"),
                        item.optLong("timestamp")
                ));
            } catch (JSONException ignored) {
            }
        }
        return result;
    }

    private static JSONArray readArray(SharedPreferences prefs) {
        try {
            return new JSONArray(prefs.getString(KEY_MESSAGES, "[]"));
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    static final class StoredSms {
        final String from;
        final String body;
        final long timestamp;

        StoredSms(String from, String body, long timestamp) {
            this.from = from;
            this.body = body;
            this.timestamp = timestamp;
        }
    }
}
