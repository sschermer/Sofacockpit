package de.sofacockpit.app;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Base64;

public class HdmiSettingsTest {
    private static final String PNG="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==";
    private HdmiSettings.Snapshot example(String[] images){
        return new HdmiSettings.Snapshot(new String[]{"Switch","PS5","Xbox","HDMI 4"},
            new AppPresentation.Mode[]{null,AppPresentation.Mode.BOTH,AppPresentation.Mode.TEXT,null},7,images);
    }
    @Test public void optionalImagesAndModesRoundTrip()throws Exception{
        HdmiSettings.Snapshot original=example(new String[]{PNG,null,PNG,null});
        HdmiSettings.Snapshot read=HdmiSettings.Snapshot.parse(new JSONObject(original.toJson().toString()));
        assertArrayEquals(original.names,read.names);assertArrayEquals(original.modes,read.modes);
        assertEquals(7,read.favorites);assertArrayEquals(original.images,read.images);
        assertEquals(AppPresentation.Mode.ICON,HdmiSettings.effective(AppPresentation.Mode.ICON,read.modes[0]));
        assertEquals(AppPresentation.Mode.BOTH,HdmiSettings.effective(AppPresentation.Mode.ICON,read.modes[1]));
        assertEquals(AppPresentation.Mode.TEXT,HdmiSettings.effective(AppPresentation.Mode.BOTH,read.modes[2]));
    }
    @Test public void omittedImageSetIsNotAnEmptyImageSet()throws Exception{
        JSONObject omitted=example(null).toJson();
        assertFalse(omitted.has("images"));
        assertNull(HdmiSettings.Snapshot.parse(omitted).images);
        HdmiSettings.Snapshot empty=HdmiSettings.Snapshot.parse(example(new String[4]).toJson());
        assertNotNull(empty.images);assertArrayEquals(new String[4],empty.images);
    }
    @Test public void invalidImagesPortsAndModesRejected()throws Exception{
        assertTrue(HdmiSettings.imageBytes(PNG).length>33);
        byte[] oversized=Base64.getDecoder().decode(PNG);oversized[16]=1;
        for(String invalid:new String[]{"bad!",Base64.getEncoder().encodeToString(oversized),
                Base64.getEncoder().encodeToString(new byte[HdmiSettings.MAX_IMAGE_BYTES+1])}){
            try{HdmiSettings.imageBytes(invalid);fail();}catch(IllegalArgumentException expected){}
        }
        JSONObject bad=example(new String[4]).toJson();
        bad.getJSONObject("images").put("5",PNG);
        try{HdmiSettings.Snapshot.parse(bad);fail();}catch(IllegalArgumentException expected){}
        JSONObject invalidMode=example(null).toJson();
        invalidMode.getJSONArray("modes").put(1,"UNKNOWN");
        try{HdmiSettings.Snapshot.parse(invalidMode);fail();}catch(IllegalArgumentException expected){}
    }
}
