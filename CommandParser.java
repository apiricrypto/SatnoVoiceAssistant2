package ir.satno.voiceassistant;

final class CommandParser {
    private CommandParser() {
    }

    static Command parse(String spokenText) {
        String text = TextTools.normalize(spokenText);
        if (text.isEmpty()) {
            return Command.unknown(spokenText);
        }

        if (containsAny(text, "تماس بگیر", "زنگ بزن", "زنگ بگیر")) {
            String name = text;
            name = removePhrase(name, "تماس بگیر");
            name = removePhrase(name, "زنگ بزن");
            name = removePhrase(name, "زنگ بگیر");
            name = TextTools.removeLeadingNoise(name);
            return Command.call(name);
        }

        if (containsAny(text, "پیام بده", "پیامک بده", "اس ام اس بده", "sms بده", "پیامک بفرست", "پیام بفرست")) {
            SplitResult split = splitSmsCommand(text);
            if (!split.name.isEmpty() && !split.body.isEmpty()) {
                return Command.sms(split.name, split.body);
            }
        }

        if (text.contains("اخرین پیام") || text.contains("آخرین پیام")) {
            String source = "";
            String target = "";
            int forIndex = text.indexOf(" برای ");
            if (forIndex >= 0) {
                target = text.substring(forIndex + " برای ".length()).trim();
                String beforeFor = text.substring(0, forIndex).trim();
                int fromIndex = beforeFor.indexOf(" از ");
                if (fromIndex >= 0) {
                    source = beforeFor.substring(fromIndex + " از ".length()).trim();
                }
            }
            if (!target.isEmpty()) {
                return Command.forwardLatest(source, target);
            }
        }

        return Command.unknown(spokenText);
    }

    private static SplitResult splitSmsCommand(String text) {
        String[] phrases = {"پیامک بفرست", "پیام بفرست", "اس ام اس بده", "sms بده", "پیامک بده", "پیام بده"};
        for (String phrase : phrases) {
            int index = text.indexOf(phrase);
            if (index < 0) {
                continue;
            }

            String before = text.substring(0, index).trim();
            String after = text.substring(index + phrase.length()).trim();
            String name = TextTools.removeLeadingNoise(before);
            if (name.isEmpty() && after.contains(" بگو ")) {
                int tellIndex = after.indexOf(" بگو ");
                name = TextTools.removeLeadingNoise(after.substring(0, tellIndex));
                after = after.substring(tellIndex + " بگو ".length()).trim();
            }
            if (name.isEmpty() && after.contains(" متن ")) {
                int bodyIndex = after.indexOf(" متن ");
                name = TextTools.removeLeadingNoise(after.substring(0, bodyIndex));
                after = after.substring(bodyIndex + " متن ".length()).trim();
            }
            if (!name.isEmpty() && !after.isEmpty()) {
                return new SplitResult(name, after);
            }
        }
        return new SplitResult("", "");
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static String removePhrase(String text, String phrase) {
        return text.replace(phrase, " ").replaceAll("\\s+", " ").trim();
    }

    private static final class SplitResult {
        final String name;
        final String body;

        SplitResult(String name, String body) {
            this.name = name;
            this.body = body;
        }
    }

    static final class Command {
        static final int UNKNOWN = 0;
        static final int CALL = 1;
        static final int SMS = 2;
        static final int FORWARD_LATEST = 3;

        final int type;
        final String contactName;
        final String messageBody;
        final String sourceName;
        final String raw;

        private Command(int type, String contactName, String messageBody, String sourceName, String raw) {
            this.type = type;
            this.contactName = contactName;
            this.messageBody = messageBody;
            this.sourceName = sourceName;
            this.raw = raw;
        }

        static Command call(String name) {
            return new Command(CALL, name, "", "", "");
        }

        static Command sms(String name, String body) {
            return new Command(SMS, name, body, "", "");
        }

        static Command forwardLatest(String sourceName, String targetName) {
            return new Command(FORWARD_LATEST, targetName, "", sourceName, "");
        }

        static Command unknown(String raw) {
            return new Command(UNKNOWN, "", "", "", raw);
        }
    }
}
