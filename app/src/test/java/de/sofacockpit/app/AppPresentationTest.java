package de.sofacockpit.app;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class AppPresentationTest {
    @Test public void globalDefaultAndIndividualOverrideRoundTrip() throws Exception {
        String prime=AppPresentation.key("https://app.primevideo.com");
        assertEquals(prime,AppPresentation.key("market://launch?id=com.amazon.amazonvideo.livingroom"));
        String netflix=AppPresentation.key("netflix://");
        AppPresentation p=AppPresentation.defaults();
        assertEquals(AppPresentation.Mode.TEXT,p.effective(prime));
        p=p.withGlobal(AppPresentation.Mode.ICON).withOverride(prime,AppPresentation.Mode.BOTH);
        assertEquals(AppPresentation.Mode.ICON,p.effective(netflix));
        assertEquals(AppPresentation.Mode.BOTH,p.effective(prime));
        p=AppPresentation.parse(new JSONObject(p.toJson().toString())).withGlobal(AppPresentation.Mode.TEXT);
        assertEquals(AppPresentation.Mode.TEXT,p.effective(netflix));
        assertEquals(AppPresentation.Mode.BOTH,p.effective(prime));
        p=p.withOverride(prime,null);
        assertNull(p.override(prime));assertEquals(AppPresentation.Mode.TEXT,p.effective(prime));
    }
    @Test public void unrelatedContentLinksDoNotInheritByServiceName() {
        String film=AppPresentation.key("https://www.netflix.com/title/123");
        assertNotEquals(film,AppPresentation.key("netflix://"));
        AppPresentation p=AppPresentation.defaults().withOverride(film,AppPresentation.Mode.ICON);
        assertEquals(AppPresentation.Mode.TEXT,p.effective(AppPresentation.key("netflix://")));
        assertEquals(AppPresentation.Mode.ICON,AppPresentation.load(p.toJson().toString()).effective(film));
    }
    @Test public void corruptOrMissingSettingsFallBackToText() {
        assertEquals(AppPresentation.Mode.TEXT,AppPresentation.load(null).global);
        assertEquals(AppPresentation.Mode.TEXT,AppPresentation.load("{\"global\":\"WRONG\"}").global);
        try {AppPresentation.defaults().withOverride("bad>>OPEN",AppPresentation.Mode.ICON);fail();}
        catch(IllegalArgumentException expected){}
    }
}
