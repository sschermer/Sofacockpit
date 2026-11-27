package de.sofacockpit.app;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class PersonalBackupTest {
    @Test public void roundTripExplicitEmptyAndFalse()throws Exception{
        PersonalBackup p=PersonalBackup.parse(new JSONObject(PersonalBackup.capture(false,true,"",
            Collections.emptyList(),0).toString()));
        assertEquals(Boolean.FALSE,p.haptics);assertEquals(Boolean.TRUE,p.autoUpdates);
        assertEquals("",p.skippedLabVersion);assertNotNull(p.aliases);assertTrue(p.aliases.isEmpty());
        assertEquals(Integer.valueOf(0),p.hdmiMask);
    }
    @Test public void oldBackupPreservesMissingValues()throws Exception{
        PersonalBackup p=PersonalBackup.parse(new JSONObject());
        assertNull(p.haptics);assertNull(p.autoUpdates);assertNull(p.skippedLabVersion);
        assertNull(p.aliases);assertNull(p.hdmiMask);
    }
    @Test public void rejectsMalformedSettings()throws Exception{
        for(JSONObject bad:new JSONObject[]{new JSONObject().put("haptics","false"),
            new JSONObject().put("hdmiDirectMask",16),new JSONObject().put("skippedLabVersion","not-version"),
            new JSONObject().put("voiceAliases",new JSONObject().put("version",2))}){
            try{PersonalBackup.parse(bad);fail();}catch(IllegalArgumentException|org.json.JSONException expected){}
        }
    }
}
