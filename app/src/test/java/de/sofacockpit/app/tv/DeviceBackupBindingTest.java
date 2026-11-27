package de.sofacockpit.app.tv;

import java.util.Base64;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceBackupBindingTest {
    private static final String PIN=Base64.getEncoder().encodeToString(new byte[32]);
    private static final String HOST="192.168.0.2",MAC="02:11:22:33:44:55";
    @Test public void roundTripAndBinding()throws Exception{
        DeviceBackupBinding saved=new DeviceBackupBinding(HOST,PIN,"021122334455");
        DeviceBackupBinding restored=DeviceBackupBinding.parse(HOST,new JSONObject(saved.toJson().toString()));
        assertEquals(MAC,restored.mac);
        assertTrue(restored.matches(HOST,PIN));
        assertFalse(restored.matches("192.168.0.3",PIN));
        assertFalse(restored.matches(HOST,null));
        byte[] other=new byte[32];other[0]=1;
        assertFalse(restored.matches(HOST,Base64.getEncoder().encodeToString(other)));
        assertEquals(saved.key(),restored.key());
        assertNotEquals(saved.key(),DeviceBackupBinding.key("192.168.0.3",PIN));
        assertNull(DeviceBackupBinding.key(HOST,null));
    }
    @Test public void onlyBoundWakeTargetIsExported(){
        assertEquals(MAC,DeviceBackupBinding.matchingMac(HOST,PIN,HOST,PIN,MAC));
        assertEquals("",DeviceBackupBinding.matchingMac(HOST,PIN,"192.168.0.3",PIN,MAC));
        assertEquals("",DeviceBackupBinding.matchingMac(HOST,PIN,HOST,"old-pin",MAC));
        assertEquals("",DeviceBackupBinding.matchingMac(HOST,null,HOST,null,MAC));
        assertEquals("",new DeviceBackupBinding(HOST,PIN,"").mac);
    }
    @Test public void rejectsInvalidData(){
        try{new DeviceBackupBinding(HOST,"not-a-pin",MAC);fail();}catch(IllegalArgumentException expected){}
        try{new DeviceBackupBinding(HOST,PIN,"FF:FF:FF:FF:FF:FF");fail();}catch(IllegalArgumentException expected){}
    }
}
