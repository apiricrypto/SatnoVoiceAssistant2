package ir.satno.voiceassistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public final class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            return;
        }

        String from = messages[0].getOriginatingAddress();
        StringBuilder body = new StringBuilder();
        long timestamp = System.currentTimeMillis();
        for (SmsMessage message : messages) {
            if (message == null) {
                continue;
            }
            body.append(message.getMessageBody());
            timestamp = message.getTimestampMillis();
        }

        SmsStore.saveIncoming(context, from == null ? "" : from, body.toString(), timestamp);
    }
}
