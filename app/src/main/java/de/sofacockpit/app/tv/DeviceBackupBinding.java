package de.sofacockpit.app.tv;

import org.json.JSONObject;
import org.json.JSONException;
import java.util.Base64;
import java.util.Objects;
import java.nio.charset.StandardCharsets;

/** Only an existing pairing (address AND certificate pin) may receive saved device permissions.
 * Never imports a new trust pin by itself. Empty MAC means explicitly disabled; missing section
 * means an older backup whose local wake settings must not be changed for the same pairing. */
final class DeviceBackupBinding {
    final String address,pin,mac;
    DeviceBackupBinding(String address,String pin,String mac){
        if(address==null||address.isEmpty()||pin==null)throw new IllegalArgumentException("TV-Gerätebindung fehlt");
        byte[] raw=Base64.getDecoder().decode(pin);
        if(raw.length!=32||!Base64.getEncoder().encodeToString(raw).equals(pin))
            throw new IllegalArgumentException("Ungültige TV-Gerätebindung");
        this.address=address;this.pin=pin;this.mac=WakeOnLan.normalizeMac(mac);
    }
    static DeviceBackupBinding parse(String address,JSONObject value)throws JSONException{
        return new DeviceBackupBinding(address,value.getString("serverPin"),value.getString("wakeMac"));
    }
    JSONObject toJson()throws JSONException{return new JSONObject().put("serverPin",pin).put("wakeMac",mac);}
    boolean matches(String address,String pin){return this.address.equals(address)&&this.pin.equals(pin);}
    String key(){return key(address,pin);}
    static String key(String address,String pin){
        if(address==null||address.isEmpty()||pin==null||pin.isEmpty())return null;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            TvProtocol.sha256((address+"|"+pin).getBytes(StandardCharsets.UTF_8)));
    }
    static String matchingMac(String address,String pin,String wakeHost,String wakePin,String mac){
        if(address==null||address.isEmpty()||pin==null||!address.equals(wakeHost)||!Objects.equals(pin,wakePin))return "";
        return WakeOnLan.normalizeMac(mac);
    }
}
