package de.sofacockpit.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class VoiceLibraryIndexTest {
    @Test public void groupsAndFiltersByAliasesAndGeneratedPhrases(){
        VoiceCommands.Library l=new VoiceCommands.Library();l.app("Dyn","pkg.dyn","Dyn");l.hdmi("PS5",1);l.profile("Kino");
        VoiceCommands.Command dyn=l.find(VoiceCommands.Kind.APP,"pkg.dyn");
        List<VoiceAliases.Entry> entries=VoiceAliases.change(l,Collections.emptyList(),null,dyn,"Basketball");
        List<VoiceLibraryIndex.Item> items=VoiceLibraryIndex.build(l,entries);
        VoiceLibraryIndex.Item app=items.stream().filter(i->i.command.equals(dyn)).findFirst().get();
        assertEquals(VoiceLibraryIndex.Group.APPS,app.group);
        assertTrue(app.matches("basketball",false));assertTrue(app.matches("ich möchte Basketball schauen",true));
        assertFalse(app.matches("Kino",false));
        assertEquals(1,items.stream().filter(i->i.matches("",true)).count());
        assertTrue(items.stream().anyMatch(i->i.group==VoiceLibraryIndex.Group.HDMI&&i.matches("PS5",false)));
        assertTrue(items.stream().anyMatch(i->i.group==VoiceLibraryIndex.Group.PROFILES&&i.matches("Kino",false)));
    }
    @Test public void missingTargetsAndCurrentLabelsArePreserved(){
        VoiceCommands.Library l=new VoiceCommands.Library();l.hdmi("PS5",1);
        VoiceCommands.Command hdmi=l.find(VoiceCommands.Kind.HDMI,"1");
        List<VoiceAliases.Entry> entries=VoiceAliases.change(l,Collections.emptyList(),null,hdmi,"Zocken");
        List<VoiceLibraryIndex.Item> missing=VoiceLibraryIndex.build(new VoiceCommands.Library(),entries);
        VoiceLibraryIndex.Item old=missing.stream().filter(i->i.command.equals(hdmi)).findFirst().get();
        assertFalse(old.live());assertTrue(old.matches("Zocken",true));
        VoiceCommands.Library changed=new VoiceCommands.Library();changed.hdmi("Konsole",1);
        VoiceLibraryIndex.Item current=VoiceLibraryIndex.build(changed,entries).stream().filter(i->i.command.equals(hdmi)).findFirst().get();
        assertTrue(current.live());assertTrue(current.command.label.contains("Konsole"));
    }
}
