package de.sofacockpit.app;

/** Select reported values without inventing an audio-route signal. */
final class VolumePresentation {
    private VolumePresentation() {}
    enum Mode { TV, SOUNDBAR, BOTH, UNKNOWN }

    static Mode select(boolean tvConnected, Boolean tvPower, Integer tvLevel, Integer tvMax,
                       boolean soundConnected, Boolean soundPower, Integer soundInput, Integer soundLevel) {
        boolean tv = tvConnected && !Boolean.FALSE.equals(tvPower) &&
            tvLevel != null && tvMax != null && tvMax > 0 && tvLevel >= 0 && tvLevel <= tvMax;
        // 7 = HDMI ARC, 20 = HDMI eARC. 15 (Optical/ARC) is ambiguous.
        boolean sound = soundConnected && Boolean.TRUE.equals(soundPower) &&
            soundInput != null && (soundInput == 7 || soundInput == 20) &&
            soundLevel != null && soundLevel >= 0 && soundLevel <= 100;
        if (tv && sound) return Mode.BOTH;
        if (tv) return Mode.TV;
        if (sound) return Mode.SOUNDBAR;
        return Mode.UNKNOWN;
    }

    static String arcLabel(Integer input) {
        return Integer.valueOf(20).equals(input) ? "HDMI eARC" : "HDMI ARC";
    }
}
