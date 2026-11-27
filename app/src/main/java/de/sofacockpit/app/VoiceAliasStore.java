package de.sofacockpit.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;

/** Lab-private preferences survive app updates; never exported in clear text. */
final class VoiceAliasStore {
    private final SharedPreferences prefs;
    VoiceAliasStore(Context context){
        prefs=context.getSharedPreferences("lab_voice_aliases",Context.MODE_PRIVATE);
    }
    List<VoiceAliases.Entry> read(){
        return VoiceAliases.decode(prefs.getString("aliases_v1",null));
    }
    void write(List<VoiceAliases.Entry> entries){
        if(!prefs.edit().putString("aliases_v1",VoiceAliases.encode(entries)).commit())
            throw new IllegalStateException("Synonyme konnten nicht gespeichert werden.");
    }
}
