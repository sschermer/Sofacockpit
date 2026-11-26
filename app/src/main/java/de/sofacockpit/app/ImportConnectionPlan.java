package de.sofacockpit.app;

/** One-shot connection consent from the confirmed import, not a saved preference. */
final class ImportConnectionPlan {
    final String tvAddress, soundAddress;
    final boolean tvNeedsPairing;

    private ImportConnectionPlan(String tvAddress, String soundAddress, boolean tvNeedsPairing) {
        this.tvAddress = tvAddress;
        this.soundAddress = soundAddress;
        this.tvNeedsPairing = tvNeedsPairing;
    }

    static ImportConnectionPlan create(boolean connect, boolean paired, String tvAddress, String soundAddress) {
        if (!connect) return new ImportConnectionPlan("", "", false);
        String tv = tvAddress == null ? "" : tvAddress.trim();
        String sound = soundAddress == null ? "" : soundAddress.trim();
        return new ImportConnectionPlan(paired ? tv : "", sound, !paired && !tv.isEmpty());
    }
    boolean hasTargets() { return !tvAddress.isEmpty() || !soundAddress.isEmpty(); }
}
