package de.sofacockpit.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class VoiceDispatchTest {
    @Test public void finalRecognizedHomeAndPersonalAliasMayDispatchOnlyOnce(){
        VoiceCommands.Library l=new VoiceCommands.Library();
        VoiceCommands.Command home=l.find(VoiceCommands.Kind.KEY,"HOME");
        l.includeAliases(VoiceAliases.change(l,Collections.emptyList(),null,home,"Homescreen"));
        for(String phrase:new String[]{"Home","Homescreen","Startseite"}){
            VoiceDispatch dispatch=new VoiceDispatch();
            assertTrue(VoiceDispatch.acceptableTranscript(phrase));
            VoiceCommands.Match match=l.resolve(phrase);
            assertEquals(home,dispatch.claim(match.command,l.resolve(phrase)));
            assertTrue(dispatch.claimed());
            assertNull(dispatch.claim(match.command,l.resolve(phrase)));
        }
    }

    @Test public void unknownNegatedAndConflictingPhrasesCannotDispatch(){
        VoiceCommands.Library l=new VoiceCommands.Library();
        l.app("Kino","pkg.kino","Kino");l.hdmi("Kino",1);
        for(String phrase:new String[]{"Unbekannter Befehl","Nicht Home","Home und leiser","Kino"}){
            VoiceDispatch dispatch=new VoiceDispatch();
            VoiceCommands.Match m=l.resolve(phrase);
            assertNull(m.command);
            assertNull(dispatch.claim(m.command,m));
            assertFalse(dispatch.claimed());
        }
    }

    @Test public void changedMissingAndRenamedTargetCannotDispatchStalePreview(){
        VoiceCommands.Library original=new VoiceCommands.Library();
        original.hdmi("PS5",1);
        VoiceCommands.Command expected=original.resolve("PS5").command;
        assertNotNull(expected);
        VoiceDispatch dispatch=new VoiceDispatch();
        assertNull(dispatch.claim(expected,new VoiceCommands.Library().resolve("PS5")));
        VoiceCommands.Library renamed=new VoiceCommands.Library();renamed.hdmi("Konsole",1);
        assertNull(dispatch.claim(expected,renamed.resolve("HDMI 1")));
        assertNull(dispatch.claim(expected,original.resolve("Home")));
        assertFalse(dispatch.claimed());
        assertEquals(expected,dispatch.claim(expected,original.resolve("PS5")));
    }

    @Test public void rejectsEmptyAndOverlongOriginalSpeechResults(){
        assertFalse(VoiceDispatch.acceptableTranscript(null));
        assertFalse(VoiceDispatch.acceptableTranscript(" "));
        assertFalse(VoiceDispatch.acceptableTranscript("Bitte!"));
        String longText="Home"+String.join("",Collections.nCopies(236," "))+" nicht";
        assertFalse(VoiceDispatch.acceptableTranscript(longText));
        assertTrue(VoiceDispatch.acceptableTranscript("Home"));
    }
}
