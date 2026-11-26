package de.sofacockpit.app.tv;

/** UI-thread session intent. Never reconnect an explicitly disconnected or newly configured TV. */
final class RefreshIntent {
    private String host;
    private boolean standby;
    private long revision;
    void begin(String address) { host=address; standby=false; revision++; }
    void clear() { begin(null); }
    void confirmed(String address,Boolean powerOn) {
        if(address==null)return;
        // A newly paired session becomes eligible only after its actual handshake.
        if(!address.equals(host)){host=address;standby=false;}
        if(powerOn!=null)standby=!powerOn;
    }
    boolean eligible(String savedAddress,boolean paired) {
        return paired&&host!=null&&!host.isEmpty()&&host.equals(savedAddress);
    }
    boolean standby(){return standby;}
    long revision(){return revision;}
}
