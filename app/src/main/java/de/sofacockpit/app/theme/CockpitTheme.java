package de.sofacockpit.app.theme;

import org.json.JSONObject;
import org.json.JSONException;
import java.util.Set;
import java.util.Iterator;

/** Versioned, data-only palette and geometry, independent of Android I/O. */
public final class CockpitTheme {
    public static final String DEFAULT_ID = "minimal-premium";
    public final String id, name, author, style, font;
    public final int background, panel, control, text, muted, accent, selected, border, dial;
    public final int cornerDp, paddingDp, onAccent;
    public final float fontScale, centerRatio;
    public static final int ERROR = 0xffeb6868, PENDING = 0xfff0c34c;

    private CockpitTheme(JSONObject manifest, JSONObject theme) throws JSONException {
        keys(manifest, Set.of("format","schema","id","name","author","version"));
        keys(theme, Set.of("background","panel","control","text","muted","accent","selected","border","dial",
            "style","font","cornerDp","paddingDp","fontScale","centerRatio"));
        if (!"sofacockpit-theme".equals(manifest.getString("format")) ||
            integer(manifest,"schema",1,1) != 1) throw new IllegalArgumentException("Nicht unterstützte Theme-Version");
        id = manifest.getString("id");
        if (!validId(id)) throw new IllegalArgumentException("Ungültige Theme-ID");
        name = shortText(manifest.getString("name"),48);
        author = shortText(manifest.getString("author"),64);
        shortText(manifest.getString("version"),24);
        style = theme.getString("style");
        font = theme.getString("font");
        if (!Set.of("flat","soft","metal","hud").contains(style) ||
            !Set.of("sans","condensed","mono").contains(font)) throw new IllegalArgumentException("Unbekannter Stil oder Schrift");
        background = color(theme,"background"); panel = color(theme,"panel"); control = color(theme,"control");
        text = color(theme,"text"); muted = color(theme,"muted"); accent = color(theme,"accent");
        selected = color(theme,"selected"); border = color(theme,"border"); dial = color(theme,"dial");
        cornerDp = integer(theme,"cornerDp",0,24);
        paddingDp = integer(theme,"paddingDp",8,18);
        fontScale = decimal(theme,"fontScale",.9f,1.12f);
        centerRatio = decimal(theme,"centerRatio",.28f,.40f);
        onAccent = contrast(accent,0xff000000) >= contrast(accent,0xffffffff) ? 0xff000000 : 0xffffffff;
        for (int surface : new int[]{background,panel,control,selected,dial}) {
            if (luminance(surface) > .16 || contrast(surface,text) < 4.5 ||
                contrast(surface,muted) < 4.5 || contrast(surface,accent) < 4.5)
                throw new IllegalArgumentException("Zu geringer Text- oder Akzentkontrast");
        }
    }

    public static CockpitTheme parse(JSONObject manifest, JSONObject theme) throws JSONException {
        return new CockpitTheme(manifest, theme);
    }
    public static boolean validId(String id) { return id != null && id.matches("[a-z0-9][a-z0-9-]{0,47}"); }
    private static String shortText(String text,int max) {
        String v = text.trim();
        if (v.isEmpty() || v.length()>max || v.codePoints().anyMatch(c -> Character.isISOControl(c) ||
            Character.getType(c) == Character.FORMAT)) throw new IllegalArgumentException("Ungültige Theme-Metadaten");
        return v;
    }
    private static void keys(JSONObject o, Set<String> expected) {
        Iterator<String> i=o.keys();
        while(i.hasNext()) if(!expected.contains(i.next())) throw new IllegalArgumentException("Unbekanntes Theme-Feld");
        for(String k:expected) if(!o.has(k)) throw new IllegalArgumentException("Fehlendes Theme-Feld: "+k);
    }
    private static int color(JSONObject o,String key) throws JSONException {
        String v=o.getString(key);
        if(!v.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Ungültige Farbe: "+key);
        return 0xff000000 | Integer.parseInt(v.substring(1),16);
    }
    private static int integer(JSONObject o,String k,int min,int max) throws JSONException {
        float value=decimal(o,k,min,max);
        if(value!=(int)value) throw new IllegalArgumentException("Ungültige Ganzzahl");
        return (int)value;
    }
    private static float decimal(JSONObject o,String k,float min,float max) throws JSONException {
        Object value=o.get(k);
        if(!(value instanceof Number)) throw new IllegalArgumentException("Ungültige Zahl");
        double n=((Number)value).doubleValue();
        if(!Double.isFinite(n)||n<min-0.000001||n>max+0.000001) throw new IllegalArgumentException("Wert außerhalb des Bereichs: "+k);
        return (float)n;
    }
    private static double linear(int v) { double x=v/255.0; return x<=.04045 ? x/12.92 : Math.pow((x+.055)/1.055,2.4); }
    private static double luminance(int c) { return .2126*linear(c>>16&255)+.7152*linear(c>>8&255)+.0722*linear(c&255); }
    static double contrast(int a,int b) { double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05); }
    public static CockpitTheme fallback() {
        try {
            return parse(new JSONObject("{\"format\":\"sofacockpit-theme\",\"schema\":1,\"id\":\"minimal-premium\",\"name\":\"Minimal Premium\",\"author\":\"Sofacockpit\",\"version\":\"1.0\"}"),
                new JSONObject("{\"background\":\"#101719\",\"panel\":\"#192326\",\"control\":\"#283235\",\"text\":\"#F2F5F9\",\"muted\":\"#B4C1C3\",\"accent\":\"#58D792\",\"selected\":\"#20392E\",\"border\":\"#455459\",\"dial\":\"#263332\",\"style\":\"soft\",\"font\":\"sans\",\"cornerDp\":14,\"paddingDp\":12,\"fontScale\":1.0,\"centerRatio\":0.32}"));
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
}
