package ir.satno.voiceassistant;

final class ContactMatch {
    final String name;
    final String phone;
    final int score;

    ContactMatch(String name, String phone, int score) {
        this.name = name;
        this.phone = phone;
        this.score = score;
    }
}
