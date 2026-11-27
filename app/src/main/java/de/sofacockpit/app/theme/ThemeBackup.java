package de.sofacockpit.app.theme;

import org.json.JSONArray;
import org.json.JSONException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

/** Bounded archive collection; validates all IDs and contents before anything is written. */
public final class ThemeBackup {
    static final int MAX_COUNT=12;
    private ThemeBackup(){}
    static JSONArray encode(List<ThemePack> packs)throws IOException{
        JSONArray result=new JSONArray();
        for(ThemePack pack:packs)if(!pack.builtin){
            if(pack.bytes==null||pack.bytes.length>ThemeArchive.MAX_FILE||result.length()>=MAX_COUNT)
                throw new IOException("Theme-Sicherung zu groß");
            result.put(Base64.getEncoder().encodeToString(pack.bytes));
        }
        return result;
    }
    static List<ThemeArchive> parseArchives(JSONArray values)throws IOException,JSONException{
        if(values.length()>MAX_COUNT)throw new IOException("Zu viele Designs");
        List<ThemeArchive> archives=new ArrayList<>();
        Set<String> ids=new HashSet<>();
        int maxEncoded=((ThemeArchive.MAX_FILE+2)/3)*4;
        for(int i=0;i<values.length();i++){
            String encoded=values.getString(i);
            if(encoded.length()>maxEncoded)throw new IOException("Theme-Datei zu groß");
            byte[] bytes=Base64.getDecoder().decode(encoded);
            ThemeArchive archive=ThemeArchive.read(new ByteArrayInputStream(bytes));
            if(ThemeStore.reserved(archive.theme.id)||!ids.add(archive.theme.id))
                throw new IOException("Reservierte oder doppelte Theme-ID");
            archives.add(archive);
        }
        return archives;
    }
    public static List<ThemePack> prepare(JSONArray values)throws IOException,JSONException{
        List<ThemePack> result=new ArrayList<>();
        for(ThemeArchive archive:parseArchives(values))result.add(new ThemePack(archive,false));
        return result;
    }
}
