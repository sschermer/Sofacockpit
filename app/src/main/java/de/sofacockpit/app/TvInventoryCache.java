package de.sofacockpit.app;

import android.content.Context;
import android.content.SharedPreferences;
import de.sofacockpit.app.tv.TclAppLauncher;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.*;

/** Last known display snapshot, not a configuration or authorization to start apps.
 * One bounded app-private record keyed by the saved address/certificate hash. */
final class TvInventoryCache {
    static final int MAX_APPS=500, MAX_CHARS=3*1024*1024;
    private final SharedPreferences prefs;
    TvInventoryCache(Context context){
        prefs=context.getSharedPreferences("tv_inventory_cache",Context.MODE_PRIVATE);
    }
    List<TclAppLauncher.App> load(String key){
        if(key==null)return null;
        try{return decode(prefs.getString("snapshot_v1",null),key);}
        catch(RuntimeException|JSONException e){return null;}
    }
    void save(String key,List<TclAppLauncher.App> apps){
        if(key==null)return;
        try{prefs.edit().putString("snapshot_v1",encode(key,apps)).apply();}
        catch(RuntimeException|JSONException ignored){}
    }
    static String encode(String key,List<TclAppLauncher.App> apps)throws JSONException{
        if(!validKey(key)||apps.size()>MAX_APPS)throw new IllegalArgumentException("TV-Inventar");
        JSONArray list=new JSONArray();Set<String> packages=new HashSet<>();
        for(TclAppLauncher.App app:apps){
            validate(app.name,app.packageName,packages);
            list.put(new JSONObject().put("name",app.name).put("package",app.packageName)
                .put("icon",app.iconUrl.length()<=4096?app.iconUrl:"").put("supplemental",app.supplemental));
        }
        String value=new JSONObject().put("version",1).put("device",key).put("apps",list).toString();
        if(value.length()>MAX_CHARS)throw new IllegalArgumentException("TV-Inventar zu groß");
        return value;
    }
    /** null is absent/mismatched, an empty list is a known empty snapshot. */
    static List<TclAppLauncher.App> decode(String value,String key)throws JSONException{
        if(value==null||!validKey(key)||value.length()>MAX_CHARS)return null;
        JSONObject root=new JSONObject(value);
        if(root.getInt("version")!=1||!key.equals(root.getString("device")))return null;
        JSONArray list=root.getJSONArray("apps");
        if(list.length()>MAX_APPS)throw new IllegalArgumentException("TV-Inventar zu groß");
        List<TclAppLauncher.App> result=new ArrayList<>();Set<String> packages=new HashSet<>();
        for(int i=0;i<list.length();i++){
            JSONObject a=list.getJSONObject(i);
            String name=a.getString("name"),pkg=a.getString("package"),icon=a.getString("icon");
            validate(name,pkg,packages);
            if(icon.length()>4096||!(a.get("supplemental") instanceof Boolean))throw new IllegalArgumentException("TV-App-Daten");
            result.add(new TclAppLauncher.App(name,pkg,icon,a.getBoolean("supplemental")));
        }
        return result;
    }
    private static boolean validKey(String key){return key!=null&&key.matches("[A-Za-z0-9_-]{43}");}
    private static void validate(String name,String pkg,Set<String> packages){
        if(name.isEmpty()||name.length()>255||name.matches("(?s).*[\\p{Cc}\\p{Cf}].*")||
            pkg.length()>255||!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")||!packages.add(pkg))
            throw new IllegalArgumentException("Ungültiger TV-App-Eintrag");
    }
}
