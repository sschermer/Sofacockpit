package de.sofacockpit.app;

import java.util.function.Predicate;

/** Supported native input profile; selection/legacy confirmations belong to one paired TV. */
final class HdmiInputProfile {
    private HdmiInputProfile() {}

    static String profileKey(String device) {
        if (device == null || device.isEmpty()) throw new IllegalArgumentException("TV identity required");
        return "hdmi_native_hw15_profile_v1_" + device;
    }

    private static String legacyPortKey(String device, int port) {
        return "hdmi_native_hw15_v1_" + device + "_" + port;
    }

    /** Preserve v1.1.9 confirmations without copying them to other devices.
     * A user-selected reference-model profile enables all four ports at once.
     */
    static boolean canSelect(String device, int port, Predicate<String> enabled) {
        if (device == null || device.isEmpty() || port < 1 || port > 4) return false;
        return enabled.test(profileKey(device)) || enabled.test(legacyPortKey(device, port));
    }
}
