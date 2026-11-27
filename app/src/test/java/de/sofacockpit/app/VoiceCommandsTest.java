package de.sofacockpit.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceCommandsTest {
    private VoiceCommands.Library library(){
        VoiceCommands.Library l=new VoiceCommands.Library();
        l.app("Netflix","com.netflix.ninja","Netflix");
        l.app("YouTube","com.google.android.youtube.tv","YouTube");
        l.hdmi("PS5",1);l.hdmi("Xbox",2);
        l.profile("Kino");l.mode("Cinema",7);
        return l;
    }
    private void expect(String phrase,VoiceCommands.Kind kind,String target){
        VoiceCommands.Command c=library().resolve(phrase).command;
        assertNotNull(phrase,c);assertEquals(kind,c.kind);assertEquals(target,c.target);
    }
    @Test public void simpleAndPoliteCommands(){
        expect("Mach bitte leiser!",VoiceCommands.Kind.KEY,"DOWN");
        expect("Lautstärke erhöhen",VoiceCommands.Kind.KEY,"UP");
        expect("Schalte den Fernseher aus",VoiceCommands.Kind.TV_POWER,"off");
        expect("Schalte die Soundbar ein",VoiceCommands.Kind.SOUND_POWER,"on");
        expect("Mach bitte Night Time an.",VoiceCommands.Kind.NIGHT,"on");
        expect("DRC deaktivieren",VoiceCommands.Kind.DRC,"off");
        expect("Stumm",VoiceCommands.Kind.TV_MUTE,"on");
        expect("Ton an",VoiceCommands.Kind.TV_MUTE,"off");
    }
    @Test public void dynamicNamesAndDigits(){
        expect("Starte Netflix",VoiceCommands.Kind.APP,"com.netflix.ninja");
        expect("Schalte auf PS 5",VoiceCommands.Kind.HDMI,"1");
        expect("PS fünf spielen",VoiceCommands.Kind.HDMI,"1");
        expect("HDMI zwei",VoiceCommands.Kind.HDMI,"2");
        expect("Wähle das Klangprofil Kino",VoiceCommands.Kind.PROFILE,"Kino");
        expect("Klangmodus Cinema",VoiceCommands.Kind.MODE,"7");
        expect("Soundbar lauter",VoiceCommands.Kind.SOUND_VOLUME,"up");
    }
    @Test public void rejectsNegationUnknownAndChains(){
        for(String p:new String[]{"Fernseher nicht ausschalten","Nicht leiser","Netflix und YouTube starten",
            "Schalte Fernseher aus und starte Netflix","Das ist lauter als vorher","Starte unbekannte App","",
            "Pause","Weiter","Nachtmodus","Fernseher","Kein Ton aus"})
            assertNull(p,library().resolve(p).command);
    }
    @Test public void collisionDoesNotChooseArbitrarily(){
        VoiceCommands.Library l=library();l.hdmi("Netflix",4);
        assertNull(l.resolve("Netflix").command);
        assertNull(l.resolve("Starte Netflix").command);
        assertEquals("com.netflix.ninja",l.resolve("App Netflix").command.target);
        l.app("Netflix","com.netflix.ninja","Netflix");
        assertEquals("com.netflix.ninja",l.resolve("App Netflix").command.target);
        l.profile("Kino!");assertNull(l.resolve("Klangprofil Kino").command);
    }
}
