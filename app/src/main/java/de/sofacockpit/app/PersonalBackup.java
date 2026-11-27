package de.sofacockpit.app;

import org.json.JSONObject;
import org.json.JSONException;
import java.util.List;
import de.sofacockpit.app.update.ReleaseCatalog;

/** Explicit portable preferences, never a dump of SharedPreferences or Android secrets.
 * Optional fields distinguish an old file from a deliberate empty/disabled value. */
final class PersonalBackup {
    final Boolean haptics,autoUpdates;
    final String skippedLabVersion;
    final List<VoiceAliases.Entry> aliases;
    final Integer hdmiMask;
    private PersonalBackup(Boolean haptics,Boolean autoUpdates,String skippedLabVersion,
                           List<VoiceAliases.Entry> aliases,Integer hdmiMask){
        this.haptics=haptics;this.autoUpdates=autoUpdates;this.skippedLabVersion=skippedLabVersion;
        this.aliases=aliases;this.hdmiMask=hdmiMask;
    }
    static JSONObject capture(boolean haptics,boolean autoUpdates,String skipped,List<VoiceAliases.Entry> aliases,Integer mask)throws JSONException{
        JSONObject json=new JSONObject().put("haptics",haptics).put("automaticUpdates",autoUpdates)
            .put("skippedLabVersion",skipped).put("voiceAliases",new JSONObject(VoiceAliases.encode(aliases)));
        if(mask!=null)json.put("hdmiDirectMask",mask);
        parse(json);
        return json;
    }
    static PersonalBackup parse(JSONObject json)throws JSONException{
        Boolean haptics=optionalBoolean(json,"haptics"),auto=optionalBoolean(json,"automaticUpdates");
        String skipped=json.has("skippedLabVersion")?json.getString("skippedLabVersion"):null;
        if(skipped!=null&&!skipped.isEmpty()&&ReleaseCatalog.Version.parse(skipped)==null)
            throw new IllegalArgumentException("Ungültige übersprungene Lab-Version");
        List<VoiceAliases.Entry> aliases=json.has("voiceAliases")?
            VoiceAliases.decode(json.getJSONObject("voiceAliases").toString()):null;
        Integer mask=null;
        if(json.has("hdmiDirectMask")){
            Object raw=json.get("hdmiDirectMask");
            if(!(raw instanceof Integer)||(Integer)raw<0||(Integer)raw>15)
                throw new IllegalArgumentException("Ungültige HDMI-Direktwahl");
            mask=(Integer)raw;
        }
        return new PersonalBackup(haptics,auto,skipped,aliases,mask);
    }
    private static Boolean optionalBoolean(JSONObject json,String key)throws JSONException{
        if(!json.has(key))return null;
        Object raw=json.get(key);
        if(!(raw instanceof Boolean))throw new IllegalArgumentException("Ungültige Einstellung: "+key);
        return (Boolean)raw;
    }
}
