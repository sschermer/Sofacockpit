package de.sofacockpit.app;

import de.sofacockpit.app.tv.TclAppLauncher;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class TvInventoryCacheTest {
    private static final String DEVICE="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String OTHER="BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
    private static final String ICON="http://192.168.0.3:34981/data/user_de/0/com.tcl.ttvs/files/test.jpg";
    @Test public void restoresDisplayOnlyAcrossRestart()throws Exception{
        TclAppLauncher.App prime=new TclAppLauncher.App("Prime Video","com.amazon.amazonvideo.livingroom",ICON,true);
        String encoded=TvInventoryCache.encode(DEVICE,Collections.singletonList(prime));
        TvAppInventory restored=new TvAppInventory();restored.device("device");
        restored.restored(TvInventoryCache.decode(encoded,DEVICE));
        assertTrue(restored.hasSnapshot());assertFalse(restored.verified());assertFalse(restored.confirmed(prime.packageName));
        assertEquals(ICON,restored.find(prime.packageName).iconUrl);
        assertTrue(restored.find(prime.packageName).supplemental);assertTrue(restored.confirmedVisible(false).isEmpty());
        restored.stale();assertEquals(1,restored.apps().size());
        restored.received(Collections.emptyList());assertTrue(restored.verified());assertTrue(restored.apps().isEmpty());
    }
    @Test public void deviceIsolationAndKnownEmptyList()throws Exception{
        String encoded=TvInventoryCache.encode(DEVICE,Collections.emptyList());
        assertNull(TvInventoryCache.decode(encoded,OTHER));assertNull(TvInventoryCache.decode(encoded,null));
        assertNull(TvInventoryCache.decode(null,DEVICE));
        TvAppInventory inventory=new TvAppInventory();inventory.device("device");
        inventory.restored(TvInventoryCache.decode(encoded,DEVICE));
        assertTrue(inventory.hasSnapshot());assertFalse(inventory.verified());
        inventory.device("other");assertFalse(inventory.hasSnapshot());
    }
    @Test public void incompleteSupplementPreservesDisplayNotConfirmation()throws Exception{
        TclAppLauncher.App prime=new TclAppLauncher.App("Prime Video","com.amazon.amazonvideo.livingroom",ICON,true);
        TclAppLauncher.App netflix=new TclAppLauncher.App("Netflix","com.netflix.ninja");
        TclAppLauncher.App dyn=new TclAppLauncher.App("Dyn","sport.dyn.app.android.prod");
        TvAppInventory inventory=new TvAppInventory();inventory.device("device");
        inventory.restored(TvInventoryCache.decode(TvInventoryCache.encode(DEVICE,Arrays.asList(prime,netflix,dyn)),DEVICE));
        inventory.received(Collections.singletonList(netflix),true);
        assertTrue(inventory.confirmed(netflix.packageName));assertFalse(inventory.confirmed(prime.packageName));
        assertNotNull(inventory.find(prime.packageName));assertNull(inventory.find(dyn.packageName));
        assertEquals(1,inventory.confirmedVisible(false).size());
        inventory.received(Collections.singletonList(netflix));assertNull(inventory.find(prime.packageName));
    }
    @Test public void rejectsDuplicatePackagesAndInvalidName()throws Exception{
        TclAppLauncher.App app=new TclAppLauncher.App("Netflix","com.netflix.ninja");
        try{TvInventoryCache.encode(DEVICE,Arrays.asList(app,app));fail();}catch(IllegalArgumentException expected){}
        try{TvInventoryCache.encode(DEVICE,Collections.singletonList(new TclAppLauncher.App("Bad","not a package")));fail();}
        catch(IllegalArgumentException expected){}
    }
}
