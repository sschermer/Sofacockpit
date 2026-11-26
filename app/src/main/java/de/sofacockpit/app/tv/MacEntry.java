package de.sofacockpit.app.tv;

/** Editing a MAC independently of Android's device-dependent keyboard. */
final class MacEntry {
    private MacEntry() {}
    static String digits(CharSequence value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 'a' && c <= 'f') c = (char)(c - 'a' + 'A');
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) result.append(c);
            else if (c != ':' && c != '-' && !Character.isWhitespace(c))
                throw new IllegalArgumentException("Nur Hexadezimalzeichen (0–9, A–F).");
            if (result.length() > 12)
                throw new IllegalArgumentException("Eine MAC-Adresse hat zwölf Hexadezimalzeichen.");
        }
        return result.toString();
    }

    static String format(CharSequence value) {
        String raw = digits(value);
        StringBuilder out = new StringBuilder(17);
        for (int i = 0; i < raw.length(); i++) {
            out.append(raw.charAt(i));
            if ((i & 1) == 1 && i < 11) out.append(':');
        }
        return out.toString();
    }

    static int cursorAfterDigits(String formatted, int count) {
        if (count <= 0) return 0;
        int index = 0;
        while (index < formatted.length() && count > 0) {
            if (formatted.charAt(index++) != ':') count--;
        }
        if (index < formatted.length() && formatted.charAt(index) == ':') index++;
        return index;
    }
}
