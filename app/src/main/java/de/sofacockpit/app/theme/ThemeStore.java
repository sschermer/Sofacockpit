package de.sofacockpit.app.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import java.io.*;
import java.util.*;

/** Private storage; only known IDs are ever used as filenames. */
public final class ThemeStore {
    private static final String[] BUILTINS={"minimal-premium","av-konsole","high-tech-hud","soft-industrial","executive-monochrome"};
    private static final int MAX_IMPORTED=ThemeBackup.MAX_COUNT;
    private final Context context;
    private final SharedPreferences options;
    private final File directory;
    private final Map<String,ThemePack> packs=new LinkedHashMap<>();
    public ThemeStore(Context context,SharedPreferences options) {
        this.context=context.getApplicationContext();this.options=options;
        directory=new File(context.getFilesDir(),"themes");
        for(String id:BUILTINS) {
            try(InputStream in=context.getAssets().open("themes/"+id+".sofatheme")) {
                ThemePack pack=new ThemePack(ThemeArchive.read(in),true);
                if(id.equals(pack.theme.id)) packs.put(id,pack);
            } catch(Exception ignored) {}
        }
        packs.putIfAbsent(CockpitTheme.DEFAULT_ID,ThemePack.fallback());
        File[] files=directory.listFiles((dir,name)->name.endsWith(".sofatheme"));
        if(files!=null) {
            Arrays.sort(files);
            int count=0;
            for(File file:files) {
                if(count>=MAX_IMPORTED) break;
                try(InputStream in=new FileInputStream(file)) {
                    ThemePack pack=new ThemePack(ThemeArchive.read(in),false);
                    String id=pack.theme.id;
                    if(file.getName().equals(id+".sofatheme")&&!packs.containsKey(id)&&!reserved(id)) {
                        packs.put(id,pack);count++;
                    }
                } catch(Exception ignored) {}
            }
        }
    }
    static boolean reserved(String id) { return Arrays.asList(BUILTINS).contains(id); }
    public synchronized List<ThemePack> all() { return new ArrayList<>(packs.values()); }
    public synchronized boolean has(String id) { return packs.containsKey(id); }
    public synchronized ThemePack selected() {
        return packs.getOrDefault(options.getString("theme_id",CockpitTheme.DEFAULT_ID),packs.get(CockpitTheme.DEFAULT_ID));
    }
    public synchronized String selectedId() { return selected().theme.id; }
    public synchronized void select(String id) {
        if(!packs.containsKey(id)) id=CockpitTheme.DEFAULT_ID;
        options.edit().putString("theme_id",id).apply();
    }
    public synchronized org.json.JSONArray exportImported()throws IOException{
        return ThemeBackup.encode(all());
    }
    /** Validate the merge before the first write; built-ins can never be overwritten. */
    public synchronized void validateRestore(List<ThemePack> imported)throws IOException{
        Set<String> ids=new HashSet<>();
        for(ThemePack existing:packs.values())if(!existing.builtin)ids.add(existing.theme.id);
        Set<String> incoming=new HashSet<>();
        for(ThemePack pack:imported){
            String id=pack.theme.id;
            if(pack.builtin||reserved(id)||!incoming.add(id)||pack.bytes==null)
                throw new IOException("Ungültiges Sicherungsdesign");
            ids.add(id);
        }
        if(ids.size()>MAX_IMPORTED)throw new IOException("Maximal "+MAX_IMPORTED+" importierte Themes; vorhandene Designs zuerst verwalten");
    }
    /** Confirmed merge: replace matching imported IDs, keep other existing themes. */
    public synchronized void restoreImported(List<ThemePack> imported)throws IOException{
        validateRestore(imported);
        for(ThemePack pack:imported)persist(pack);
    }
    public synchronized ThemePack add(ThemePack pack) throws IOException {
        String id=pack.theme.id;
        if(reserved(id)||packs.containsKey(id)) throw new IOException("Theme-ID bereits vorhanden");
        if(packs.values().stream().filter(p->!p.builtin).count()>=MAX_IMPORTED)
            throw new IOException("Maximal "+MAX_IMPORTED+" importierte Themes");
        return persist(pack);
    }
    private ThemePack persist(ThemePack pack)throws IOException{
        String id=pack.theme.id;
        if(!directory.isDirectory()&&!directory.mkdirs()) throw new IOException("Theme-Ordner nicht verfügbar");
        AtomicFile target=new AtomicFile(new File(directory,id+".sofatheme"));
        FileOutputStream out=null;
        try {out=target.startWrite();out.write(pack.bytes);target.finishWrite(out);}
        catch(IOException e){if(out!=null)target.failWrite(out);throw e;}
        packs.put(id,pack);return pack;
    }
    public synchronized void remove(String id) throws IOException {
        ThemePack pack=packs.get(id);
        if(pack==null||pack.builtin) throw new IOException("Vorinstallierte Themes können nicht gelöscht werden");
        File file=new File(directory,id+".sofatheme");
        if(!file.delete()&&file.exists()) throw new IOException("Theme konnte nicht gelöscht werden");
        packs.remove(id);
        if(id.equals(options.getString("theme_id",""))) select(CockpitTheme.DEFAULT_ID);
    }
}
