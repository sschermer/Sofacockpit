package de.sofacockpit.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.*;

/** Personal phrases are data, never raw device commands or executable scripts. */
final class VoiceAliases {
    static final int MAX_ENTRIES=256, MAX_TEXT=160;
    static final class Entry {
        final VoiceCommands.Kind kind;
        final String target,label,text;
        Entry(VoiceCommands.Kind kind,String target,String label,String text){
            this.kind=Objects.requireNonNull(kind);
            this.target=Objects.requireNonNull(target);
            this.label=Objects.requireNonNull(label);
            this.text=Objects.requireNonNull(text).trim();
            if(target.isEmpty()||target.length()>1024||label.isEmpty()||label.length()>1024)
                throw new IllegalArgumentException("Ungültiges Befehlsziel.");
            validateText(this.text);
        }
        String key(){return VoiceCommands.phrase(text);}
        boolean pointsTo(VoiceCommands.Command command){return kind==command.kind&&target.equals(command.target);}
    }
    static void validateText(String text){
        String key=VoiceCommands.phrase(text);
        if(text.length()>MAX_TEXT||key.isEmpty()||text.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Bitte eine Formulierung mit 1–160 Zeichen eingeben.");
        if(VoiceCommands.negated(key))throw new IllegalArgumentException("Verneinungen sind nicht als Synonym erlaubt.");
    }
    static List<Entry> change(VoiceCommands.Library base,List<Entry> existing,Entry previous,
                              VoiceCommands.Command selected,String text){
        VoiceCommands.Command live=base.find(selected.kind,selected.target);
        if(live==null)throw new IllegalArgumentException("Befehlsziel nicht verfügbar. Bitte Liste aktualisieren.");
        Entry replacement=new Entry(live.kind,live.target,live.label,text);
        if(base.hasStandardPhrase(replacement.text))
            throw new IllegalArgumentException("Diese Formulierung ist bereits als Standardbefehl belegt.");
        List<Entry> next=new ArrayList<>(existing);
        if(previous!=null)removeChecked(next,previous);
        if(next.size()>=MAX_ENTRIES)throw new IllegalArgumentException("Maximal 256 eigene Synonyme.");
        Set<String> generated=VoicePhrases.keys(replacement.kind,replacement.target,replacement.text);
        for(String variant:generated)if(base.conflictsWith(variant,live))
            throw new IllegalArgumentException("Satzmuster kollidiert mit einem Standardbefehl: "+variant);
        for(Entry entry:next){
            if(entry.key().equals(replacement.key()))
                throw new IllegalArgumentException("Dieses Synonym ist bereits einem Befehl zugeordnet.");
            if(entry.pointsTo(live))continue;
            Set<String> other=VoicePhrases.keys(entry.kind,entry.target,entry.text);
            for(String variant:generated)if(other.contains(variant))
                throw new IllegalArgumentException("Satzmuster kollidiert mit einem anderen Synonym: "+variant);
        }
        next.add(replacement);
        return next;
    }
    static List<Entry> remove(List<Entry> existing,Entry previous){
        List<Entry> next=new ArrayList<>(existing);removeChecked(next,previous);return next;
    }
    private static void removeChecked(List<Entry> entries,Entry previous){
        for(Iterator<Entry> it=entries.iterator();it.hasNext();){
            Entry entry=it.next();
            if(entry.key().equals(previous.key())&&entry.kind==previous.kind&&entry.target.equals(previous.target)
                &&entry.text.equals(previous.text)){it.remove();return;}
        }
        throw new IllegalArgumentException("Eintrag wurde geändert. Bitte Bibliothek neu öffnen.");
    }
    private static void validate(List<Entry> entries){
        if(entries.size()>MAX_ENTRIES)throw new IllegalArgumentException("Zu viele Synonyme.");
        Set<String> keys=new HashSet<>();
        for(Entry entry:entries)if(!keys.add(entry.key()))throw new IllegalArgumentException("Doppelte Synonyme.");
    }
    static String encode(List<Entry> entries){
        validate(entries);
        try{
            JSONArray array=new JSONArray();
            for(Entry e:entries)array.put(new JSONObject().put("kind",e.kind.name()).put("target",e.target)
                .put("label",e.label).put("text",e.text));
            return new JSONObject().put("version",1).put("aliases",array).toString();
        }catch(JSONException ex){throw new IllegalArgumentException("Synonyme konnten nicht gespeichert werden.",ex);}
    }
    static List<Entry> decode(String value){
        if(value==null)return new ArrayList<>();
        if(value.length()>1_000_000)throw new IllegalArgumentException("Synonymliste zu groß.");
        try{
            JSONObject root=new JSONObject(value);
            if(root.getInt("version")!=1)throw new IllegalArgumentException("Unbekanntes Synonymformat.");
            JSONArray array=root.getJSONArray("aliases");
            if(array.length()>MAX_ENTRIES)throw new IllegalArgumentException("Zu viele Synonyme.");
            List<Entry> entries=new ArrayList<>();
            for(int i=0;i<array.length();i++){
                JSONObject e=array.getJSONObject(i);
                entries.add(new Entry(VoiceCommands.Kind.valueOf(e.getString("kind")),
                    e.getString("target"),e.getString("label"),e.getString("text")));
            }
            validate(entries);return entries;
        }catch(JSONException ex){throw new IllegalArgumentException("Gespeicherte Synonyme konnten nicht gelesen werden.",ex);}
    }
}
