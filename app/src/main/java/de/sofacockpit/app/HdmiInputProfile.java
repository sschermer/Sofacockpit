package de.sofacockpit.app;

import java.util.function.Predicate;

/** Supported native input profile; selection/legacy confirmations belong to one paired TV. */
final class HdmiInputProfile {
    private HdmiInputProfile() {}

    static String profileKey(String device) {
        if (device == null || device.isEmpty()) throw new IllegalArgumentException("TV identity required");
        return "hdmi_native_hw15_profile_v1_" + device;
    }

    static String legacyPortKey(String device, int port) {
        return "hdmi_native_hw15_v1_" + device + "_" + port;
    }

    static int mask(String device,Predicate<String> enabled){
        int result=0;
        for(int port=1;port<=4;port++)if(canSelect(device,port,enabled))result|=1<<(port-1);
        return result;
    }
    static void restoreMask(String expected,String actual,int mask,java.util.function.BiConsumer<String,Boolean> save){
        if(mask<0||mask>15)throw new IllegalArgumentException("HDMI-Direktwahl");
        if(expected==null||!expected.equals(actual))return;
        save.accept(profileKey(actual),mask==15);
        for(int port=1;port<=4;port++)save.accept(legacyPortKey(actual,port),(mask&(1<<(port-1)))!=0);
    }

    /** Preserve v1.1.9 confirmations without copying them to other devices.
     * A user-selected reference-model profile enables all four ports at once.
     */
    static boolean canSelect(String device, int port, Predicate<String> enabled) {
        if (device == null || device.isEmpty() || port < 1 || port > 4) return false;
        return enabled.test(profileKey(device)) || enabled.test(legacyPortKey(device, port));
    }
}
