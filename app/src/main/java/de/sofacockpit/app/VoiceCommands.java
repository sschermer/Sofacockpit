package de.sofacockpit.app;

import java.text.Normalizer;
import java.util.*;

/** Local, deterministic phrase library. Whole phrases only; never execute substrings or chains. */
final class VoiceCommands {
    enum Kind { KEY, TV_POWER, TV_MUTE, SOUND_POWER, SOUND_VOLUME, NIGHT, DRC, APP, HDMI, PROFILE, MODE }
    static final class Command {
        final Kind kind;
        final String target, label;
        Command(Kind kind, String target, String label) {
            this.kind=kind; this.target=target; this.label=label;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Command)) return false;
            Command c=(Command)other;
            return kind==c.kind && target.equals(c.target);
        }
        @Override public int hashCode() { return Objects.hash(kind,target); }
    }
    static String normalize(String input) {
        if (input==null || input.length()>240) return "";
        String s=Normalizer.normalize(input,Normalizer.Form.NFKC).toLowerCase(Locale.GERMAN)
            .replace("ä","ae").replace("ö","oe").replace("ü","ue").replace("ß","ss")
            .replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
        return s.replaceAll("(?<=\\p{L})(?=\\d)|(?<=\\d)(?=\\p{L})"," ")
            .replaceAll("\\s+"," ").trim();
    }
    static String phrase(String input) {
        String s=normalize(input);
        // Only harmless whole-word politeness is ignored, never negative wording.
        return s.replaceAll("\\bbitte\\b"," ").replaceAll("\\s+"," ").trim();
    }
    static boolean negated(String normalized) {
        return normalized.matches(".*\\b(nicht|kein|keine|keinen|keinem|keiner|keines|niemals|nie|ohne)\\b.*");
    }
    static final class Match {
        final Command command;
        final String message;
        Match(Command command,String message){this.command=command;this.message=message;}
    }
    static final class Library {
        private final Map<String,LinkedHashSet<Command>> phrases=new LinkedHashMap<>();
        private final Map<Command,LinkedHashMap<String,String>> standards=new LinkedHashMap<>();
        private String blocked;
        List<Command> commands(){
            return new ArrayList<>(standards.keySet());
        }
        Command find(Kind kind,String target){
            for(Command c:standards.keySet())if(c.kind==kind&&c.target.equals(target))return c;
            return null;
        }
        List<String> standardPhrases(Command command){
            Map<String,String> aliases=standards.get(command);
            return aliases==null?Collections.emptyList():new ArrayList<>(aliases.values());
        }
        boolean hasStandardPhrase(String text){
            return phrases.containsKey(phrase(text));
        }
        void includeAliases(List<VoiceAliases.Entry> aliases){
            for(VoiceAliases.Entry entry:aliases){
                Command command=find(entry.kind,entry.target);
                // Reserve inactive phrases too: a removed target must never silently
                // turn an existing personal phrase into a different device action.
                if(command==null)command=new Command(entry.kind,entry.target,entry.label);
                phrases.computeIfAbsent(phrase(entry.text),k->new LinkedHashSet<>()).add(command);
            }
        }
        void block(String reason){blocked=reason;}
        Library() {
            key("UP","TV lauter","lauter","mach lauter","mache lauter","lautstaerke erhoehen","ton lauter","tv lauter");
            key("DOWN","TV leiser","leiser","mach leiser","mache leiser","lautstaerke senken","ton leiser","tv leiser");
            key("BACK","TV zurück","zurueck","zurueckgehen","geh zurueck","gehe zurueck");
            key("HOME","TV-Startseite","home","startseite","tv startseite","zur startseite");
            key("DPAD_UP","TV nach oben","nach oben","hoch");
            key("DPAD_DOWN","TV nach unten","nach unten","runter");
            key("DPAD_LEFT","TV nach links","nach links","links");
            key("DPAD_RIGHT","TV nach rechts","nach rechts","rechts");
            key("OK","TV OK","ok","okay","bestaetigen");
            key("PLAY_PAUSE","Wiedergabe umschalten","play pause","wiedergabe umschalten","pause weiter");
            key("REWIND","Zurückspulen","zurueckspulen","spule zurueck");
            key("FAST_FORWARD","Vorspulen","vorspulen","spule vor");
            key("INPUT","TV-Eingangsauswahl","tv eingangsauswahl","eingangsauswahl","tv quellen");
            key("SETTINGS","TV-Einstellungen öffnen","tv einstellungen","fernseher einstellungen","tv einstellungen oeffnen");
            toggle(Kind.TV_POWER,"Fernseher","fernseher","tv");
            toggle(Kind.SOUND_POWER,"Soundbar","soundbar");
            toggle(Kind.NIGHT,"Night Time","night time","nighttime","nachtmodus","night mode","nightmode");
            toggle(Kind.DRC,"DRC","drc","d r c","dynamikkompression");
            add(new Command(Kind.TV_MUTE,"on","TV stummschalten"),"stumm","stummschalten","ton aus","tv stumm","mach den ton aus","tv stummschalten","fernseher stummschalten");
            add(new Command(Kind.TV_MUTE,"off","TV-Stummschaltung aufheben"),"ton an","ton ein","stumm aufheben","stummschaltung aufheben","tv ton an");
            add(new Command(Kind.SOUND_VOLUME,"up","Soundbar lauter"),"soundbar lauter","mach soundbar lauter","mach die soundbar lauter");
            add(new Command(Kind.SOUND_VOLUME,"down","Soundbar leiser"),"soundbar leiser","mach soundbar leiser","mach die soundbar leiser");
        }
        private void key(String id,String label,String... aliases){add(new Command(Kind.KEY,id,label),aliases);}
        private void toggle(Kind kind,String label,String... subjects){
            for(String subject:subjects){
                for(boolean enabled:new boolean[]{true,false}){
                    String state=enabled?"an":"aus", verb=enabled?"einschalten":"ausschalten", imperative=enabled?"ein":"aus";
                    Command command=new Command(kind,enabled?"on":"off",label+" "+(enabled?"einschalten":"ausschalten"));
                    add(command,subject+" "+state,subject+" "+imperative,subject+" "+verb,verb+" "+subject,
                        "mach "+subject+" "+state,"mache "+subject+" "+state,"mach den "+subject+" "+state,
                        "mach die "+subject+" "+state,"schalte "+subject+" "+imperative,"schalte den "+subject+" "+imperative,
                        "schalte die "+subject+" "+imperative,"schalte "+subject+" "+state,
                        "schalte den "+subject+" "+state,"schalte die "+subject+" "+state,
                        (enabled?"aktiviere ":"deaktiviere ")+subject,subject+(enabled?" aktivieren":" deaktivieren"));
                }
            }
        }
        private void add(Command command,String... aliases){
            for(String alias:aliases){
                String key=phrase(alias);
                if(!key.isEmpty()){
                    phrases.computeIfAbsent(key,k->new LinkedHashSet<>()).add(command);
                    standards.computeIfAbsent(command,k->new LinkedHashMap<>()).putIfAbsent(key,alias);
                }
            }
        }
        private Set<String> names(String name){
            Set<String> variants=new LinkedHashSet<>();
            String n=normalize(name);
            if(n.isEmpty())return variants;
            variants.add(n);
            String[] words={"null","eins","zwei","drei","vier","fuenf","sechs","sieben","acht","neun"};
            String[] tokens=n.split(" ");
            for(int i=0;i<tokens.length;i++)
                if(tokens[i].length()==1 && tokens[i].charAt(0)>='0' && tokens[i].charAt(0)<='9')
                    tokens[i]=words[tokens[i].charAt(0)-'0'];
            variants.add(String.join(" ",tokens));
            return variants;
        }
        void app(String name,String pkg,String displayName){
            Command c=new Command(Kind.APP,pkg,displayName+" starten");
            for(String n:names(name))add(c,n,"starte "+n,"start "+n,"oeffne "+n,n+" starten",n+" oeffnen",
                "app "+n,"streaming "+n);
        }
        void hdmi(String name,int port){
            Command c=new Command(Kind.HDMI,Integer.toString(port),name+" (HDMI "+port+") auswählen");
            Set<String> variants=names(name);variants.addAll(names("HDMI "+port));variants.addAll(names("h d m i "+port));
            for(String n:variants)add(c,n,"schalte auf "+n,"wechsel zu "+n,"wechsle zu "+n,"wechsel auf "+n,
                "wechsle auf "+n,"waehle "+n,n+" spielen","starte "+n,n+" auswaehlen");
        }
        void profile(String name){
            Command c=new Command(Kind.PROFILE,name,"Klangprofil "+name+" anwenden");
            for(String n:names(name))add(c,"profil "+n,"klangprofil "+n,"waehle profil "+n,"waehle klangprofil "+n,
                "waehle das klangprofil "+n,"aktiviere profil "+n,"klangprofil "+n+" anwenden");
        }
        void mode(String name,int id){
            Command c=new Command(Kind.MODE,Integer.toString(id),"Klangmodus "+name+" auswählen");
            for(String n:names(name))add(c,"klangmodus "+n,"soundmodus "+n,"waehle klangmodus "+n,
                "waehle den klangmodus "+n,"klangmodus "+n+" auswaehlen");
        }
        Match resolve(String text){
            if(blocked!=null)return new Match(null,blocked);
            String n=phrase(text);
            if(n.isEmpty())return new Match(null,"Befehl sprechen oder eingeben.");
            if(negated(n))
                return new Match(null,"Verneinung erkannt. Keine Aktion.");
            Set<Command> candidates=phrases.get(n);
            if(candidates==null || candidates.isEmpty())
                return new Match(null,"Kein eindeutiger Befehl. Bitte kurz und einzeln formulieren.");
            if(candidates.size()!=1)return new Match(null,"Mehrdeutiger Befehl. Quelle oder Funktion genauer benennen.");
            Command c=candidates.iterator().next();
            Command live=find(c.kind,c.target);
            if(live==null)return new Match(null,"Persönliches Ziel derzeit nicht verfügbar. Keine Aktion.");
            return new Match(live,live.label);
        }
    }
}
