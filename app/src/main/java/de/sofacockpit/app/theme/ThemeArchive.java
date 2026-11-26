package de.sofacockpit.app.theme;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Never extracts paths. Bounds both compressed and expanded data; explicit allowlist. */
public final class ThemeArchive {
    public static final int MAX_FILE = 1024 * 1024;
    private static final int MAX_JSON = 16 * 1024;
    private static final int MAX_EXPANDED = 1536 * 1024;
    private static final Set<String> ALLOWED = Set.of("manifest.json","theme.json",
        "preview.png","preview.webp","textures/panel.png","textures/panel.webp",
        "icons/arrow.png","icons/arrow.webp");
    public final CockpitTheme theme;
    public final byte[] bytes, preview, texture, arrow;

    private ThemeArchive(CockpitTheme theme,byte[] bytes,byte[] preview,byte[] texture,byte[] arrow) {
        this.theme=theme;this.bytes=bytes;this.preview=preview;this.texture=texture;this.arrow=arrow;
    }
    public static ThemeArchive read(InputStream input) throws IOException, JSONException {
        byte[] raw=limited(input,MAX_FILE);
        if(raw.length<22 || raw[0]!='P' || raw[1]!='K' || raw[2]!=3 || raw[3]!=4)
            throw new IOException("Keine gültige .sofatheme-Datei");
        Map<String,byte[]> entries=new HashMap<>();
        int expanded=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(raw))) {
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;) {
                String name=entry.getName();
                if(entry.isDirectory()) {
                    if(!Set.of("textures/","icons/").contains(name)) throw new IOException("Unbekannter Theme-Ordner");
                    zip.closeEntry(); continue;
                }
                if(!ALLOWED.contains(name)||entries.containsKey(name)) throw new IOException("Nicht erlaubter oder doppelter Theme-Eintrag");
                int limit=name.endsWith(".json")?MAX_JSON:MAX_FILE;
                byte[] data=limited(zip,Math.min(limit,MAX_EXPANDED-expanded));
                expanded+=data.length;
                entries.put(name,data);
                zip.closeEntry();
            }
        }
        if(!entries.containsKey("manifest.json")||!entries.containsKey("theme.json"))
            throw new IOException("Manifest oder Theme-Daten fehlen");
        CockpitTheme theme=CockpitTheme.parse(json(entries.get("manifest.json")),json(entries.get("theme.json")));
        return new ThemeArchive(theme,raw,asset(entries,"preview"),asset(entries,"textures/panel"),asset(entries,"icons/arrow"));
    }
    private static JSONObject json(byte[] bytes) throws JSONException {
        return new JSONObject(new String(bytes,StandardCharsets.UTF_8));
    }
    private static byte[] asset(Map<String,byte[]> entries,String base) throws IOException {
        byte[] png=entries.get(base+".png"),webp=entries.get(base+".webp");
        if(png!=null&&webp!=null) throw new IOException("Doppeltes Bild: "+base);
        return png!=null?png:webp;
    }
    public static byte[] limited(InputStream input,int limit) throws IOException {
        if(input==null||limit<0) throw new IOException("Datei nicht lesbar");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[8192];
        for(int n;(n=input.read(buffer))!=-1;) {
            if(n>limit-out.size()) throw new IOException("Theme-Datei zu groß");
            out.write(buffer,0,n);
        }
        return out.toByteArray();
    }
}
