package ir.satno.voiceassistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_SPEECH = 10;
    private static final int REQUEST_PERMISSIONS = 11;

    private TextView transcriptView;
    private TextView statusView;
    private ContactFinder contactFinder;

    private final String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        contactFinder = new ContactFinder(getContentResolver());
        setContentView(buildUi());
        requestMissingPermissions();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(36, 48, 36, 36);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setTextDirection(View.TEXT_DIRECTION_RTL);
        root.setBackgroundColor(0xfff7fbfc);

        TextView title = new TextView(this);
        title.setText("دستیار صوتی شخصی");
        title.setTextSize(25);
        title.setTextColor(0xff083344);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView help = new TextView(this);
        help.setText("نمونه فرمان‌ها:\nبا علی تماس بگیر\nبه پیمان پیام بده جلسه ساعت ۵ است\nآخرین پیام از مجتبی را برای علی بفرست");
        help.setTextSize(16);
        help.setTextColor(0xff334155);
        help.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(-1, -2);
        helpParams.setMargins(0, 28, 0, 28);
        root.addView(help, helpParams);

        Button micButton = new Button(this);
        micButton.setText("شروع فرمان صوتی");
        micButton.setTextSize(18);
        micButton.setAllCaps(false);
        micButton.setOnClickListener(v -> startVoiceInput());
        root.addView(micButton, new LinearLayout.LayoutParams(-1, -2));

        Button permissionButton = new Button(this);
        permissionButton.setText("بررسی و گرفتن مجوزها");
        permissionButton.setAllCaps(false);
        permissionButton.setOnClickListener(v -> requestMissingPermissions());
        LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(-1, -2);
        permissionParams.setMargins(0, 16, 0, 0);
        root.addView(permissionButton, permissionParams);

        transcriptView = new TextView(this);
        transcriptView.setText("متن شنیده‌شده اینجا نمایش داده می‌شود.");
        transcriptView.setTextSize(17);
        transcriptView.setTextColor(0xff0f172a);
        transcriptView.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams transcriptParams = new LinearLayout.LayoutParams(-1, -2);
        transcriptParams.setMargins(0, 30, 0, 0);
        root.addView(transcriptView, transcriptParams);

        statusView = new TextView(this);
        statusView.setText("آماده تست");
        statusView.setTextSize(16);
        statusView.setTextColor(0xff0369a1);
        statusView.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.setMargins(0, 20, 0, 0);
        root.addView(statusView, statusParams);

        return root;
    }

    private void requestMissingPermissions() {
        List<String> missing = new ArrayList<>();
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
        } else {
            setStatus("همه مجوزهای لازم فعال است.");
        }
    }

    private void startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMissingPermissions();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "فرمان را بگویید");

        try {
            startActivityForResult(intent, REQUEST_SPEECH);
            setStatus("در حال گوش دادن...");
        } catch (ActivityNotFoundException exception) {
            setStatus("تشخیص گفتار روی این گوشی فعال نیست. Google app یا سرویس Speech را بررسی کنید.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SPEECH || resultCode != RESULT_OK || data == null) {
            return;
        }
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) {
            setStatus("چیزی دریافت نشد.");
            return;
        }
        String spoken = results.get(0);
        transcriptView.setText(spoken);
        handleCommand(spoken);
    }

    private void handleCommand(String spoken) {
        CommandParser.Command command = CommandParser.parse(spoken);
        switch (command.type) {
            case CommandParser.Command.CALL:
                chooseContact(command.contactName, contact -> call(contact));
                break;
            case CommandParser.Command.SMS:
                chooseContact(command.contactName, contact -> sendSms(contact, command.messageBody));
                break;
            case CommandParser.Command.FORWARD_LATEST:
                forwardLatest(command.sourceName, command.contactName);
                break;
            default:
                setStatus("فرمان را متوجه نشدم. نمونه: «به علی پیام بده سلام»");
        }
    }

    private void forwardLatest(String sourceName, String targetName) {
        if (TextUtils.isEmpty(targetName)) {
            setStatus("مخاطب مقصد مشخص نشد.");
            return;
        }

        chooseContact(targetName, target -> {
            if (TextUtils.isEmpty(sourceName)) {
                SmsStore.StoredSms latest = SmsStore.latest(this);
                if (latest == null) {
                    setStatus("هنوز SMS ورودی ذخیره نشده است.");
                    return;
                }
                sendSms(target, latest.body);
                return;
            }

            chooseContact(sourceName, source -> {
                SmsStore.StoredSms latest = SmsStore.latestFrom(this, source.phone);
                if (latest == null) {
                    setStatus("از این مخاطب SMS ذخیره‌شده پیدا نشد.");
                    return;
                }
                sendSms(target, latest.body);
            });
        });
    }

    private void chooseContact(String spokenName, ContactAction action) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestMissingPermissions();
            return;
        }

        List<ContactMatch> matches = contactFinder.find(cleanName(spokenName));
        if (matches.isEmpty()) {
            setStatus("مخاطبی برای «" + spokenName + "» پیدا نشد.");
            return;
        }

        if (matches.size() == 1 || matches.get(0).score >= matches.get(1).score + 120) {
            action.run(matches.get(0));
            return;
        }

        int count = Math.min(matches.size(), 5);
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            ContactMatch item = matches.get(i);
            labels[i] = item.name + "\n" + item.phone;
        }

        new AlertDialog.Builder(this)
                .setTitle("کدام مخاطب؟")
                .setItems(labels, (dialog, which) -> action.run(matches.get(which)))
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void call(ContactMatch contact) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestMissingPermissions();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + Uri.encode(contact.phone)));
        startActivity(intent);
        setStatus("تماس با " + contact.name);
    }

    private void sendSms(ContactMatch contact, String body) {
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestMissingPermissions();
            return;
        }
        if (TextUtils.isEmpty(body)) {
            setStatus("متن پیام خالی است.");
            return;
        }
        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> parts = smsManager.divideMessage(body);
        smsManager.sendMultipartTextMessage(contact.phone, null, parts, null, null);
        setStatus("SMS برای " + contact.name + " ارسال شد: " + body);
        Toast.makeText(this, "پیام ارسال شد", Toast.LENGTH_SHORT).show();
    }

    private static String cleanName(String value) {
        String text = TextTools.removeLeadingNoise(value);
        String[] suffixes = {" را برای", " را", " بفرست", " ارسال کن", " تماس بگیر", " زنگ بزن"};
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : suffixes) {
                if (text.endsWith(suffix.trim())) {
                    text = text.substring(0, text.length() - suffix.trim().length()).trim();
                    changed = true;
                }
            }
        }
        return text;
    }

    private void setStatus(String message) {
        statusView.setText(message);
    }

    private interface ContactAction {
        void run(ContactMatch contact);
    }
}
