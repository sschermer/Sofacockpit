package de.sofacockpit.app;

import java.text.Collator;
import java.util.*;

/** Display/search index only; never used to resolve or execute speech. */
final class VoiceLibraryIndex {
    enum Group {
        APPS("Streaming & Apps"), HDMI("HDMI-Eingänge"), TV("TV-Bedienung"),
        SOUND("Ton & Lautstärke"), PROFILES("Klangprofile"), MODES("Klangmodi"),
        MISSING("Derzeit nicht verfügbar");
        final String label;Group(String label){this.label=label;}
    }
    static final class Item {
        final VoiceCommands.Command command;
        final Group group;
        final List<VoiceAliases.Entry> own;
        final List<String> standard;
        final String searchable;
        Item(VoiceCommands.Command c,boolean live,List<VoiceAliases.Entry> own,List<String> standard){
            this.command=c;this.group=group(c,live);this.own=own;this.standard=standard;
            StringBuilder b=new StringBuilder(VoiceCommands.normalize(c.label));
            for(String s:standard)b.append(' ').append(VoiceCommands.normalize(s));
            for(VoiceAliases.Entry e:own){
                b.append(' ').append(VoiceCommands.normalize(e.text));
                for(String variant:VoicePhrases.variants(e.kind,e.target,e.text))
                    b.append(' ').append(VoiceCommands.normalize(variant));
            }
            searchable=b.toString();
        }
        boolean live(){return group!=Group.MISSING;}
        boolean matches(String query,boolean onlyOwn){
            if(onlyOwn&&own.isEmpty())return false;
            String normalized=VoiceCommands.normalize(query);
            if(normalized.isEmpty())return true;
            for(String token:normalized.split(" "))if(!searchable.contains(token))return false;
            return true;
        }
    }
    static Group group(VoiceCommands.Command c,boolean live){
        if(!live)return Group.MISSING;
        switch(c.kind){
            case APP:return Group.APPS;
            case HDMI:return Group.HDMI;
            case PROFILE:return Group.PROFILES;
            case MODE:return Group.MODES;
            case SOUND_POWER:case SOUND_VOLUME:case TV_MUTE:case NIGHT:case DRC:return Group.SOUND;
            case KEY:return ("UP".equals(c.target)||"DOWN".equals(c.target))?Group.SOUND:Group.TV;
            default:return Group.TV;
        }
    }
    static List<Item> build(VoiceCommands.Library library,List<VoiceAliases.Entry> entries){
        Map<VoiceCommands.Command,List<VoiceAliases.Entry>> own=new LinkedHashMap<>();
        for(VoiceAliases.Entry e:entries){
            VoiceCommands.Command key=new VoiceCommands.Command(e.kind,e.target,e.label);
            own.computeIfAbsent(key,k->new ArrayList<>()).add(e);
        }
        Set<VoiceCommands.Command> targets=new LinkedHashSet<>(library.commands());targets.addAll(own.keySet());
        List<Item> result=new ArrayList<>();
        for(VoiceCommands.Command c:targets)result.add(new Item(c,library.find(c.kind,c.target)!=null,
            own.getOrDefault(c,Collections.emptyList()),library.standardPhrases(c)));
        Collator sort=Collator.getInstance(Locale.GERMAN);
        result.sort((a,b)->a.group!=b.group?Integer.compare(a.group.ordinal(),b.group.ordinal()):sort.compare(a.command.label,b.command.label));
        return result;
    }
}
