package de.sofacockpit.app;

final class CockpitNavigation {
    static final int START = 0, TV = 1, SOUND = 2;
    private static final String[] TABS = {"Start", "TV", "Sound"};
    private static final String[][] PAGES = {
        {"Start"}, {"Fernbedienung", "Quellen", "Bild & System"}, {"Klangsteuerung", "Klangprofile", "Feinabstimmung"}
    };
    private int category, page;
    int category() { return category; }
    int page() { return page; }
    static String tabLabel(int category) { return TABS[category]; }
    static String pageLabel(int category, int page) { return PAGES[category][page]; }
    static int pageCount(int category) { return PAGES[category].length; }
    static boolean valid(int category, int page) {
        return category >= START && category <= SOUND && page >= 0 && page < pageCount(category);
    }
    void select(int category, int page) {
        if (!valid(category, page)) throw new IllegalArgumentException("Ungültige Seite");
        this.category = category; this.page = page;
    }
    static int swipeTarget(int page, int count, float dx, float threshold) {
        if (Math.abs(dx) < threshold) return page;
        return Math.max(0, Math.min(count-1, page + (dx < 0 ? 1 : -1)));
    }
    static boolean horizontal(float dx, float dy, float slop) {
        return Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)*1.25f;
    }
    static boolean vertical(float dx, float dy, float slop) {
        return Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx);
    }
}
