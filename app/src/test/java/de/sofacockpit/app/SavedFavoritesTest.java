package de.sofacockpit.app;

import de.sofacockpit.app.tv.TclAppLauncher;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SavedFavoritesTest {
    private static final String CONTENT="https://www.netflix.com/title/80057281?source=30";
    private static StreamingShortcuts.Entry favorite(String name,String link){
        return new StreamingShortcuts.Entry(name,link);
    }
    private static TclAppLauncher.App app(String name,String pkg){
        return new TclAppLauncher.App(name,pkg);
    }
    private void reject(Runnable r){
        try{r.run();fail("Expected rejection");}catch(IllegalArgumentException expected){}
    }
    @Test public void contentLinkRemainsSavedAndCanBeReassignedWithoutTouchingOtherEntries() throws Exception {
        JSONArray stored=new JSONArray().put(new JSONObject().put("name","Dyn").put("link","sport.dyn.app.android.prod"))
            .put(new JSONObject().put("name","Netflix").put("link",CONTENT))
            .put(new JSONObject().put("name","YouTube").put("link","https://www.youtube.com"));
        List<StreamingShortcuts.Entry> entries=StreamingShortcuts.parseEntries(stored);
        StreamingShortcuts.Entry broken=entries.get(1);
        TvAppInventory tv=new TvAppInventory();tv.device("tv");
        tv.received(Arrays.asList(app("Netflix","com.netflix.ninja"),app("Dyn","sport.dyn.app.android.prod")));
        assertEquals(CONTENT,broken.link);
        assertNull(TclAppLauncher.packageForLink(broken.link));
        assertEquals(TvAppInventory.FavoriteStatus.UNMAPPED,tv.favoriteStatus(broken));
        assertEquals(TvAppInventory.FavoriteStatus.NOT_REPORTED,tv.favoriteStatus(entries.get(2)));
        assertEquals(Collections.singletonList(entries.get(0)),tv.activeFavorites(entries));
        assertEquals(3,entries.size());
        StreamingShortcuts.Entry replacement=StreamingShortcuts.forInstalledApp(broken,tv.find("com.netflix.ninja"));
        assertNull(StreamingShortcuts.conflict(entries,broken,replacement));
        List<StreamingShortcuts.Entry> repaired=StreamingShortcuts.updated(entries,broken,replacement);
        assertEquals(3,repaired.size());assertSame(entries.get(0),repaired.get(0));assertSame(entries.get(2),repaired.get(2));
        assertEquals("Netflix",repaired.get(1).name);
        assertEquals("com.netflix.ninja",TclAppLauncher.packageForLink(repaired.get(1).link));
        assertEquals(TvAppInventory.FavoriteStatus.AVAILABLE,tv.favoriteStatus(repaired.get(1)));
        assertEquals(CONTENT,entries.get(1).link);
        assertEquals(2,tv.activeFavorites(repaired).size());
        assertEquals(2,tv.apps().size());
    }
    @Test public void allSavedEntriesHaveStatusesEvenWithoutAnInventory(){
        TvAppInventory tv=new TvAppInventory();
        StreamingShortcuts.Entry mapped=favorite("Netflix","netflix://");
        StreamingShortcuts.Entry custom=favorite("Film",CONTENT);
        assertEquals(TvAppInventory.FavoriteStatus.UNCONFIRMED,tv.favoriteStatus(mapped));
        assertEquals(TvAppInventory.FavoriteStatus.UNMAPPED,tv.favoriteStatus(custom));
        assertTrue(tv.activeFavorites(Arrays.asList(mapped,custom)).isEmpty());
        tv.received(Collections.emptyList());
        assertEquals(TvAppInventory.FavoriteStatus.NOT_REPORTED,tv.favoriteStatus(mapped));
        tv.stale();assertEquals(TvAppInventory.FavoriteStatus.UNCONFIRMED,tv.favoriteStatus(mapped));
    }
    @Test public void duplicateNamesLinksAndCanonicalPackagesRejectedForAddAndReplace(){
        StreamingShortcuts.Entry broken=favorite("Netflix",CONTENT);
        StreamingShortcuts.Entry dyn=favorite("Sport","sport.dyn.app.android.prod");
        List<StreamingShortcuts.Entry> saved=Arrays.asList(broken,dyn);
        reject(()->StreamingShortcuts.updated(saved,null,favorite("Netflix","netflix://")));
        reject(()->StreamingShortcuts.updated(saved,null,favorite("Anderer Film",CONTENT)));
        reject(()->StreamingShortcuts.updated(saved,null,favorite("Dyn","market://launch?id=sport.dyn.app.android.prod")));
        reject(()->StreamingShortcuts.updated(saved,broken,StreamingShortcuts.forInstalledApp(broken,app("Dyn","sport.dyn.app.android.prod"))));
        List<StreamingShortcuts.Entry> replaced=StreamingShortcuts.updated(saved,broken,favorite("Netflix","netflix://"));
        reject(()->StreamingShortcuts.updated(replaced,null,favorite("Netflix 2","com.netflix.ninja")));
        assertEquals(CONTENT,broken.link);
    }
    @Test public void hiddenEntriesCanBeMovedEditedAndRemoved(){
        StreamingShortcuts.Entry a=favorite("Film",CONTENT),b=favorite("Dyn","sport.dyn.app.android.prod");
        List<StreamingShortcuts.Entry> saved=Arrays.asList(a,b);
        List<StreamingShortcuts.Entry> moved=StreamingShortcuts.moved(saved,a,1);
        assertEquals(Arrays.asList(b,a),moved);
        StreamingShortcuts.Entry edited=favorite("Serie","https://example.com/series/1");
        List<StreamingShortcuts.Entry> changed=StreamingShortcuts.updated(moved,a,edited);
        assertEquals(Arrays.asList(b,edited),changed);
        assertEquals(Collections.singletonList(b),StreamingShortcuts.removed(changed,edited));
        reject(()->StreamingShortcuts.moved(changed,a,1));
        reject(()->StreamingShortcuts.updated(changed,a,edited));
        reject(()->StreamingShortcuts.removed(changed,a));
        assertEquals(Arrays.asList(a,b),saved);
    }
}
