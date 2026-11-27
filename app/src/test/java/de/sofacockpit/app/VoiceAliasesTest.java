package de.sofacockpit.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class VoiceAliasesTest {
    private VoiceCommands.Library base(){
        VoiceCommands.Library l=new VoiceCommands.Library();
        l.app("Netflix","com.netflix.ninja","Netflix");
        l.hdmi("PS5",1);l.profile("Kino");return l;
    }
    private void rejected(Runnable r){
        try{r.run();fail("Expected rejection");}catch(IllegalArgumentException expected){}
    }
    @Test public void addEditDeleteAndPersist(){
        VoiceCommands.Library l=base();
        VoiceCommands.Command c=l.find(VoiceCommands.Kind.NIGHT,"on");
        assertNotNull(c);assertTrue(l.standardPhrases(c).contains("night time an"));
        List<VoiceAliases.Entry> first=VoiceAliases.change(l,Collections.emptyList(),null,c,"Leise Nacht!");
        List<VoiceAliases.Entry> restored=VoiceAliases.decode(VoiceAliases.encode(first));
        assertEquals("Leise Nacht!",restored.get(0).text);
        VoiceCommands.Library with=base();with.includeAliases(restored);
        assertEquals(c,with.resolve("Bitte leise Nacht").command);
        List<VoiceAliases.Entry> edited=VoiceAliases.change(base(),restored,restored.get(0),c,"Schlafenszeit");
        VoiceCommands.Library next=base();next.includeAliases(edited);
        assertNull(next.resolve("Leise Nacht").command);assertEquals(c,next.resolve("Schlafenszeit").command);
        assertEquals(c,next.resolve("Night Time an").command);
        assertTrue(VoiceAliases.remove(edited,edited.get(0)).isEmpty());
    }
    @Test public void rejectsCollisionsAndInvalidPhrases(){
        VoiceCommands.Library l=base();VoiceCommands.Command c=l.find(VoiceCommands.Kind.NIGHT,"on");
        List<VoiceAliases.Entry> first=VoiceAliases.change(l,Collections.emptyList(),null,c,"Ruhe");
        for(String s:new String[]{"Leiser","Night Time an","Nicht lauter","bitte","   ","Ruhe!","Starte Netflix","a\nb"})
            rejected(()->VoiceAliases.change(l,first,null,c,s));
        assertEquals(1,first.size());
    }
    @Test public void unavailableAndNewlyConflictingTargetsNeverExecute(){
        VoiceCommands.Library l=base();
        VoiceCommands.Command app=l.find(VoiceCommands.Kind.APP,"com.netflix.ninja");
        List<VoiceAliases.Entry> entries=VoiceAliases.change(l,Collections.emptyList(),null,app,"Filmabend");
        VoiceCommands.Library missing=new VoiceCommands.Library();missing.includeAliases(entries);
        assertNull(missing.resolve("Filmabend").command);
        VoiceCommands.Library changed=new VoiceCommands.Library();changed.hdmi("Filmabend",2);changed.includeAliases(entries);
        assertNull(changed.resolve("Filmabend").command);
        VoiceCommands.Library again=base();again.includeAliases(entries);
        assertEquals(app,again.resolve("Filmabend").command);
        VoiceCommands.Library conflict=base();conflict.hdmi("Filmabend",2);conflict.includeAliases(entries);
        assertNull(conflict.resolve("Filmabend").command);
    }
    @Test public void corruptStorageAndDuplicateEntriesRejected(){
        rejected(()->VoiceAliases.decode("{broken"));
        VoiceCommands.Library l=base();VoiceCommands.Command c=l.find(VoiceCommands.Kind.KEY,"UP");
        List<VoiceAliases.Entry> entries=VoiceAliases.change(l,Collections.emptyList(),null,c,"Mehr Wumms");
        rejected(()->VoiceAliases.encode(Arrays.asList(entries.get(0),entries.get(0))));
        assertEquals(0,VoiceAliases.decode(null).size());
    }
    @Test public void dynamicLabelsStayLive(){
        VoiceCommands.Library l=base();VoiceCommands.Command hdmi=l.find(VoiceCommands.Kind.HDMI,"1");
        List<VoiceAliases.Entry> entries=VoiceAliases.change(l,Collections.emptyList(),null,hdmi,"Zocken");
        VoiceCommands.Library changed=new VoiceCommands.Library();changed.hdmi("Konsole",1);changed.includeAliases(entries);
        VoiceCommands.Command command=changed.resolve("Zocken").command;
        assertEquals(hdmi,command);assertTrue(command.label.contains("Konsole"));
    }
}
