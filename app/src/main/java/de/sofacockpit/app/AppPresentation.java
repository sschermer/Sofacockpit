package de.sofacockpit.app;

import de.sofacockpit.app.tv.TclAppLauncher;
import de.sofacockpit.app.tv.TvProtocol;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Iterator;
import org.json.JSONObject;
import org.json.JSONException;

/** App identity rather than label: renames and views share the same override. */
final class AppPresentation {
    static final String PREF = "lab_app_presentation";
    private static final int MAX_OVERRIDES = 500;
    enum Mode {
        ICON("Nur Icon"), TEXT("Nur Text"), BOTH("Icon und Text");
        final String label;
        Mode(String label) { this.label = label; }
    }
    final Mode global;
    private final Map<String,Mode> overrides;
    private AppPresentation(Mode global, Map<String,Mode> overrides) {
        this.global=global;this.overrides=new LinkedHashMap<>(overrides);
    }
    static AppPresentation defaults() { return new AppPresentation(Mode.TEXT,new LinkedHashMap<>()); }
    static String key(String link) {
        String normalized=TvProtocol.normalizeLaunchLink(link);
        String pkg=TclAppLauncher.packageForLink(normalized);
        return pkg==null?"link:"+normalized:pkg;
    }
    Mode override(String key) { return overrides.get(key); }
    Mode effective(String key) { Mode mode=override(key);return mode==null?global:mode; }
    AppPresentation withGlobal(Mode mode) { return new AppPresentation(mode,overrides); }
    AppPresentation withOverride(String key,Mode mode) {
        if(key==null||!key.equals(key(key.startsWith("link:")?key.substring(5):key)))
            throw new IllegalArgumentException("Ungültiges App-Ziel");
        Map<String,Mode> next=new LinkedHashMap<>(overrides);
        if(mode==null)next.remove(key);
        else{
            if(!next.containsKey(key)&&next.size()>=MAX_OVERRIDES)
                throw new IllegalArgumentException("Zu viele individuelle Darstellungen.");
            next.put(key,mode);
        }
        return new AppPresentation(global,next);
    }
    JSONObject toJson() {
        try{
            JSONObject entries=new JSONObject();
            for(Map.Entry<String,Mode> e:overrides.entrySet())entries.put(e.getKey(),e.getValue().name());
            return new JSONObject().put("global",global.name()).put("overrides",entries);
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    static AppPresentation parse(JSONObject json) throws JSONException {
        AppPresentation parsed=defaults().withGlobal(Mode.valueOf(json.getString("global")));
        JSONObject entries=json.getJSONObject("overrides");
        if(entries.length()>MAX_OVERRIDES)throw new IllegalArgumentException("Zu viele individuelle Darstellungen.");
        Map<String,Mode> result=new LinkedHashMap<>();
        for(Iterator<String> it=entries.keys();it.hasNext();){
            String id=it.next();
            String canonical=key(id.startsWith("link:")?id.substring(5):id);
            if(!id.equals(canonical))throw new IllegalArgumentException("Ungültiges App-Ziel");
            result.put(id,Mode.valueOf(entries.getString(id)));
        }
        return new AppPresentation(parsed.global,result);
    }
    static AppPresentation load(String saved) {
        if(saved!=null)try{return parse(new JSONObject(saved));}catch(JSONException|IllegalArgumentException ignored){}
        return defaults();
    }
}
