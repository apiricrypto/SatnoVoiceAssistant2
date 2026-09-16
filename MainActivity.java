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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int REQUEST_SPEECH = 10;
    private static final int REQUEST_PERMISSIONS = 11;

    private TextView transcriptView;
    private TextView statusView;
    private ContactFinder contactFinder;

    private final String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
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
        help.setText("نمونه فرمان‌ها:\nبا علی تماس بگیر\nبه پیمان پیام بده جلسه ساعت ۵ است");
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
            setStatus("تشخیص گفتار روی این گوشی فعال نیست.");
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
                chooseContact(command.contactName, this::call);
                break;
            case CommandParser.Command.SMS:
                chooseContact(command.contactName, contact -> openSms(contact, command.messageBody));
                break;
            default:
                setStatus("فرمان را متوجه نشدم. نمونه: «به علی پیام بده سلام»");
        }
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

    private void openSms(ContactMatch contact, String body) {
        if (TextUtils.isEmpty(body)) {
            setStatus("متن پیام خالی است.");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + Uri.encode(contact.phone)));
        intent.putExtra("sms_body", body);

        try {
            startActivity(intent);
            setStatus("پیام برای " + contact.name + " آماده شد. دکمه Send را بزنید.");
        } catch (ActivityNotFoundException exception) {
            setStatus("برنامه پیامک روی گوشی پیدا نشد.");
        }
    }

    private static String cleanName(String value) {
        String text = TextTools.removeLeadingNoise(value);
        String[] suffixes = {" را برای", " را", " بفرست", " ارسال کن", " تماس بگیر", " زنگ بزن"};
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : suffixes) {
                String item = suffix.trim();
                if (text.endsWith(item)) {
                    text = text.substring(0, text.length() - item.length()).trim();
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