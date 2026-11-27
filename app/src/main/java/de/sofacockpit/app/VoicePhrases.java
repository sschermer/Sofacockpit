package de.sofacockpit.app;

import java.util.*;

/**
 * Finite, command-specific full-sentence templates. Not a keyword or fuzzy matcher.
 * Complete custom phrases remain exact; only short names/expressions receive variants.
 */
final class VoicePhrases {
    private static final Set<String> SENTENCE_WORDS=new HashSet<>(Arrays.asList(
        "ich","du","wir","ihr","er","sie","es","man","mir","dir","uns","mich","dich",
        "moechte","moechtest","will","willst","wuerde","wuerdest","kann","kannst","koennte","koenntest",
        "mach","mache","macht","machen","starte","start","starten","oeffne","oeffnen","schalte","schalten",
        "wechsel","wechsle","wechseln","waehle","waehlen","auswaehlen","aktiviere","aktivieren",
        "deaktiviere","deaktivieren","einschalten","ausschalten","anwenden",
        "an","ein","aus","auf","schauen","gucken","sehen","spielen",
        "und","oder","dann","danach","wenn","falls","bevor","nachdem","aber","sondern","statt",
        "stattdessen","ausser","ob","obwohl","weder","noch","nur","auch","vielleicht",
        "nicht","kein","keine","keinen","keinem","keiner","keines","niemals","nie","ohne"));

    private static boolean shortName(String normalized) {
        if(normalized.isEmpty()||VoiceCommands.negated(normalized))return false;
        String[] tokens=normalized.split(" ");
        if(tokens.length>5)return false;
        for(String word:tokens)if(SENTENCE_WORDS.contains(word))return false;
        return true;
    }

    /** The original user spelling is retained for display; deduplication uses the parser's normalization. */
    static List<String> variants(VoiceCommands.Kind kind,String target,String raw) {
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        if(raw==null)return new ArrayList<>();
        String text=raw.trim();
        String normalized=VoiceCommands.phrase(text);
        if(normalized.isEmpty())return new ArrayList<>();
        add(result,text);
        // Don't insert a complete request into a second sentence. Terminal punctuation
        // is harmless for exact matches; punctuation inside aliases isn't an instruction separator.
        if(!shortName(normalized)||text.matches(".*[;,!?\\n\\r].*"))return new ArrayList<>(result.values());

        switch(kind){
            case APP:
                add(result,"Starte "+text,"Öffne "+text,text+" starten",text+" öffnen",
                    "App "+text,"Streaming "+text,"Mach "+text+" an","Mache "+text+" an",
                    "Mach "+text+" auf","Schalte "+text+" ein","Ich möchte "+text+" schauen",
                    "Ich möchte "+text+" sehen","Ich möchte "+text+" gucken",
                    "Ich will "+text+" schauen","Ich will "+text+" sehen",
                    "Ich möchte gerne "+text+" schauen","Ich würde gerne "+text+" schauen",
                    "Ich würde gern "+text+" schauen","Ich möchte "+text+" starten","Ich möchte "+text+" öffnen");
                break;
            case HDMI:
                add(result,"Schalte auf "+text,"Wechsel zu "+text,"Wechsle zu "+text,"Wechsel auf "+text,
                    "Wechsle auf "+text,"Wähle "+text,text+" spielen",text+" auswählen",
                    "Ich möchte "+text+" spielen","Ich will "+text+" spielen",
                    "Ich möchte gerne "+text+" spielen","Ich würde gerne "+text+" spielen");
                break;
            case PROFILE:
                add(result,"Profil "+text,"Klangprofil "+text,"Wähle Profil "+text,
                    "Wähle Klangprofil "+text,"Wähle das Klangprofil "+text,
                    "Aktiviere Profil "+text,"Klangprofil "+text+" anwenden",
                    "Ich möchte das Klangprofil "+text);
                break;
            case MODE:
                add(result,"Klangmodus "+text,"Soundmodus "+text,"Wähle Klangmodus "+text,
                    "Wähle den Klangmodus "+text,"Klangmodus "+text+" auswählen",
                    "Ich möchte den Klangmodus "+text);
                break;
            case NIGHT:
            case DRC:
                // Explicit polarity only. Never generate an opposite switch action.
                if("on".equals(target))add(result,"Aktiviere "+text,"Mach "+text+" an",
                    "Schalte "+text+" ein",text+" einschalten",text+" aktivieren");
                else if("off".equals(target))add(result,"Deaktiviere "+text,"Mach "+text+" aus",
                    "Schalte "+text+" aus",text+" ausschalten",text+" deaktivieren");
                break;
            case KEY:
                if("UP".equals(target)||"DOWN".equals(target))
                    add(result,"Mach "+text,"Mache "+text,"Etwas "+text,"Mach es "+text);
                break;
            default:
                // Power, mute, transport and other controls retain exact personal phrases.
                break;
        }
        return new ArrayList<>(result.values());
    }
    private static void add(Map<String,String> result,String... values){
        for(String value:values){
            String key=VoiceCommands.phrase(value);
            if(!key.isEmpty()&&!VoiceCommands.negated(key))result.putIfAbsent(key,value);
        }
    }
    static Set<String> keys(VoiceCommands.Kind kind,String target,String text){
        Set<String> keys=new LinkedHashSet<>();
        for(String variant:variants(kind,target,text)){
            String key=VoiceCommands.phrase(variant);
            if(!key.isEmpty())keys.add(key);
        }
        return keys;
    }
}
