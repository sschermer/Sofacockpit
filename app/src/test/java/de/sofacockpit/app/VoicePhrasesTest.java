package de.sofacockpit.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class VoicePhrasesTest {
    private VoiceCommands.Library base(){
        VoiceCommands.Library l=new VoiceCommands.Library();
        l.app("Dyn","com.example.dyn","Dyn");
        l.app("Netflix","com.example.netflix","Netflix");
        l.hdmi("PS5",1);l.profile("Kino");l.mode("Cinema",7);
        return l;
    }
    private void expect(VoiceCommands.Library l,String phrase,VoiceCommands.Kind kind,String target){
        VoiceCommands.Command c=l.resolve(phrase).command;
        assertNotNull(phrase,c);assertEquals(kind,c.kind);assertEquals(target,c.target);
    }
    private VoiceCommands.Library withAlias(VoiceCommands.Library l,VoiceCommands.Kind kind,String target,String text){
        List<VoiceAliases.Entry> a=VoiceAliases.change(l,Collections.emptyList(),null,l.find(kind,target),text);
        l.includeAliases(a);return l;
    }
    @Test public void shortPersonalAppNameSupportsNaturalPhrases(){
        VoiceCommands.Library l=withAlias(base(),VoiceCommands.Kind.APP,"com.example.dyn","Basketball");
        for(String phrase:new String[]{"Basketball","Mach bitte Basketball an","Starte Basketball","Öffne Basketball",
            "Ich möchte Basketball schauen","Ich würde gerne Basketball schauen","Basketball starten","Ich will Basketball sehen"})
            expect(l,phrase,VoiceCommands.Kind.APP,"com.example.dyn");
    }
    @Test public void standardNamesAndTypedTargets(){
        VoiceCommands.Library l=base();
        expect(l,"Mach Netflix an",VoiceCommands.Kind.APP,"com.example.netflix");
        expect(l,"Ich möchte PS5 spielen",VoiceCommands.Kind.HDMI,"1");
        expect(l,"Ich möchte das Klangprofil Kino",VoiceCommands.Kind.PROFILE,"Kino");
        expect(l,"Ich möchte den Klangmodus Cinema",VoiceCommands.Kind.MODE,"7");
        assertNull(l.resolve("Mach PS5 an").command);
        assertNull(l.resolve("Kino").command);
        assertNull(l.resolve("Cinema").command);
    }
    @Test public void negationsUnknownChainsAndOppositeIntentRemainBlocked(){
        VoiceCommands.Library l=withAlias(base(),VoiceCommands.Kind.APP,"com.example.dyn","Basketball");
        for(String phrase:new String[]{"Mach Basketball nicht an","Basketball aus","Mach Basketball aus",
            "Starte Basketball und Netflix","Basketball oder Netflix","Ich habe Basketball gespielt",
            "Vielleicht Basketball","Nicht Basketball starten","Ich möchte kein Basketball schauen",
            "Starte Basketball danach Netflix","Ich möchte Basketball spielen","Basketballer"})
            assertNull(phrase,l.resolve(phrase).command);
        VoiceCommands.Library n=withAlias(base(),VoiceCommands.Kind.NIGHT,"on","Schlafenszeit");
        expect(n,"Mach Schlafenszeit an",VoiceCommands.Kind.NIGHT,"on");
        assertNull(n.resolve("Mach Schlafenszeit aus").command);
    }
    @Test public void completeUserPhrasesStayExact(){
        String text="Ich möchte Basketball schauen";
        VoiceCommands.Library l=withAlias(base(),VoiceCommands.Kind.APP,"com.example.dyn",text);
        expect(l,text,VoiceCommands.Kind.APP,"com.example.dyn");
        assertNull(l.resolve("Starte "+text).command);
        assertNull(l.resolve("Basketball").command);
        assertEquals(1,VoicePhrases.variants(VoiceCommands.Kind.APP,"com.example.dyn",text).size());
    }
    @Test public void generatedConflictRejectedAtEditTime(){
        VoiceCommands.Library l=base();
        VoiceCommands.Command dyn=l.find(VoiceCommands.Kind.APP,"com.example.dyn");
        VoiceCommands.Command night=l.find(VoiceCommands.Kind.NIGHT,"on");
        List<VoiceAliases.Entry> existing=VoiceAliases.change(l,Collections.emptyList(),null,night,"Mach Basketball an");
        try{VoiceAliases.change(l,existing,null,dyn,"Basketball");fail("Generated alias conflict");}
        catch(IllegalArgumentException expected){}
        // A new short name must not displace a built-in complete phrase.
        try{VoiceAliases.change(l,Collections.emptyList(),null,night,"Netflix");fail("Standard conflict");}
        catch(IllegalArgumentException expected){}
    }
    @Test public void existingAliasesAndNewNamesConflictRatherThanSilentlyReassign(){
        VoiceCommands.Library l=base();
        VoiceCommands.Command dyn=l.find(VoiceCommands.Kind.APP,"com.example.dyn");
        List<VoiceAliases.Entry> saved=VoiceAliases.change(l,Collections.emptyList(),null,dyn,"Basketball");
        VoiceCommands.Library changed=new VoiceCommands.Library();
        changed.app("Basketball","com.example.other","Basketball");
        changed.includeAliases(saved);
        assertNull(changed.resolve("Basketball").command);
        assertNull(changed.resolve("Mach Basketball an").command);
        VoiceCommands.Library empty=new VoiceCommands.Library();
        empty.includeAliases(saved);
        assertNull(empty.resolve("Mach Basketball an").command);
    }
    @Test public void personalVolumeAndProfileVariants(){
        VoiceCommands.Library l=withAlias(base(),VoiceCommands.Kind.KEY,"DOWN","Weniger Lärm");
        expect(l,"Mach weniger Lärm",VoiceCommands.Kind.KEY,"DOWN");
        VoiceCommands.Library p=withAlias(base(),VoiceCommands.Kind.PROFILE,"Kino","Filmton");
        expect(p,"Wähle das Klangprofil Filmton",VoiceCommands.Kind.PROFILE,"Kino");
        assertNull(p.resolve("Mach Filmton an").command);
    }
}
