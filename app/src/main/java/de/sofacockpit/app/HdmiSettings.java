package de.sofacockpit.app;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.Base64;
import java.util.Iterator;

/** Four local HDMI assignments, independent of the native HDMI control profile. */
final class HdmiSettings {
    static final int PORTS=4, MAX_IMAGE_BYTES=48*1024, MAX_IMAGE_SIDE=256;
    private HdmiSettings(){}
    static String nameKey(int port){checkPort(port);return "hdmi_name_"+port;}
    static String modeKey(int port){checkPort(port);return "lab_hdmi_mode_"+port;}
    static String imageKey(int port){checkPort(port);return "lab_hdmi_image_"+port;}
    static void checkPort(int port){if(port<1||port>PORTS)throw new IllegalArgumentException("HDMI-Port");}
    static String defaultName(int port){checkPort(port);return "HDMI "+port;}
    static AppPresentation.Mode override(SharedPreferences prefs,int port){
        String value=prefs.getString(modeKey(port),null);
        try{return value==null?null:AppPresentation.Mode.valueOf(value);}
        catch(IllegalArgumentException e){return null;}
    }
    static AppPresentation.Mode effective(AppPresentation.Mode global,AppPresentation.Mode individual){
        return individual==null?global:individual;
    }
    static byte[] imageBytes(String encoded){
        if(encoded==null||encoded.length()>((MAX_IMAGE_BYTES+2)/3)*4)
            throw new IllegalArgumentException("HDMI-Bild zu groß");
        byte[] b=Base64.getDecoder().decode(encoded);
        if(b.length<33||b.length>MAX_IMAGE_BYTES||
            (b[0]&255)!=137||b[1]!=80||b[2]!=78||b[3]!=71||b[4]!=13||b[5]!=10||b[6]!=26||b[7]!=10||
            b[8]!=0||b[9]!=0||b[10]!=0||b[11]!=13||b[12]!=73||b[13]!=72||b[14]!=68||b[15]!=82)
            throw new IllegalArgumentException("Ungültiges HDMI-Bildformat");
        int width=integer(b,16),height=integer(b,20);
        if(width<1||height<1||width>MAX_IMAGE_SIDE||height>MAX_IMAGE_SIDE)
            throw new IllegalArgumentException("Ungültige HDMI-Bildgröße");
        return b;
    }
    private static int integer(byte[] b,int i){
        return ((b[i]&255)<<24)|((b[i+1]&255)<<16)|((b[i+2]&255)<<8)|(b[i+3]&255);
    }

    /** Omitted images are distinct from an explicitly included empty image set. */
    static final class Snapshot {
        final String[] names;
        final AppPresentation.Mode[] modes;
        final int favorites;
        final String[] images; // null: leave existing images untouched
        Snapshot(String[] names,AppPresentation.Mode[] modes,int favorites,String[] images){
            if(names.length!=PORTS||modes.length!=PORTS||favorites<0||favorites>15||
                (images!=null&&images.length!=PORTS))throw new IllegalArgumentException("HDMI-Konfiguration");
            this.names=names.clone();this.modes=modes.clone();this.favorites=favorites;
            this.images=images==null?null:images.clone();
            for(String name:this.names)
                if(name==null||name.trim().isEmpty()||name.length()>40)throw new IllegalArgumentException("HDMI-Name");
            if(this.images!=null)for(String image:this.images)if(image!=null)imageBytes(image);
        }
        JSONObject toJson() throws JSONException {
            JSONArray n=new JSONArray(),m=new JSONArray();
            for(int i=0;i<PORTS;i++){n.put(names[i]);m.put(modes[i]==null?JSONObject.NULL:modes[i].name());}
            JSONObject result=new JSONObject().put("names",n).put("modes",m).put("favorites",favorites);
            if(images!=null){
                JSONObject values=new JSONObject();
                for(int i=0;i<PORTS;i++)if(images[i]!=null)values.put(String.valueOf(i+1),images[i]);
                result.put("images",values);
            }
            return result;
        }
        static Snapshot parse(JSONObject object) throws JSONException {
            JSONArray namesJson=object.getJSONArray("names"), modesJson=object.getJSONArray("modes");
            if(namesJson.length()!=PORTS||modesJson.length()!=PORTS)throw new IllegalArgumentException("HDMI-Anzahl");
            Object mask=object.get("favorites");
            if(!(mask instanceof Integer))throw new IllegalArgumentException("HDMI-Favoriten");
            String[] names=new String[PORTS];
            AppPresentation.Mode[] modes=new AppPresentation.Mode[PORTS];
            for(int i=0;i<PORTS;i++){
                names[i]=namesJson.getString(i);
                modes[i]=modesJson.isNull(i)?null:AppPresentation.Mode.valueOf(modesJson.getString(i));
            }
            String[] images=null;
            if(object.has("images")){
                images=new String[PORTS];
                JSONObject imageJson=object.getJSONObject("images");
                for(Iterator<String> it=imageJson.keys();it.hasNext();){
                    String key=it.next();
                    if(!key.matches("[1-4]"))throw new IllegalArgumentException("HDMI-Bildport");
                    images[Integer.parseInt(key)-1]=imageJson.getString(key);
                }
            }
            return new Snapshot(names,modes,(Integer)mask,images);
        }
    }
    static Snapshot capture(SharedPreferences prefs,boolean includeImages){
        String[] names=new String[PORTS],images=includeImages?new String[PORTS]:null;
        AppPresentation.Mode[] modes=new AppPresentation.Mode[PORTS];
        for(int p=1;p<=PORTS;p++){
            names[p-1]=prefs.getString(nameKey(p),defaultName(p));
            modes[p-1]=override(prefs,p);
            if(images!=null)images[p-1]=prefs.getString(imageKey(p),null);
        }
        return new Snapshot(names,modes,prefs.getInt("lab_hdmi_favorites",0)&15,images);
    }
    static void restore(SharedPreferences.Editor edit,Snapshot snapshot,boolean restoreImages){
        edit.putInt("lab_hdmi_favorites",snapshot.favorites);
        for(int p=1;p<=PORTS;p++){
            edit.putString(nameKey(p),snapshot.names[p-1]);
            AppPresentation.Mode mode=snapshot.modes[p-1];
            if(mode==null)edit.remove(modeKey(p));else edit.putString(modeKey(p),mode.name());
            if(restoreImages&&snapshot.images!=null){
                String image=snapshot.images[p-1];
                if(image==null)edit.remove(imageKey(p));else edit.putString(imageKey(p),image);
            }
        }
    }
}
