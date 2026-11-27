package de.sofacockpit.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Narrow, read-only HTTP subset for the observed local TCL icon endpoint.
 * No DNS, proxy, redirect, cookies, arbitrary host/path or global cleartext policy change. */
public final class TvIconHttp {
    static final int MAX_BYTES=512*1024;
    private static final int MAX_HEADERS=16384;
    private static final String BASE="/data/user_de/0/com.tcl.ttvs/files/";
    private TvIconHttp(){}

    /** Only locally generated descriptions, safe to display without exposing URLs or IPs. */
    public static final class IconFailure extends IOException {
        public IconFailure(String reason) { super(reason); }
    }
    public static final class Target {
        private final InetAddress peer;
        public final int port;
        public final String path;
        private Target(InetAddress peer,int port,String path) {
            this.peer=peer;this.port=port;this.path=path;
        }
    }

    public static Target target(String url, InetAddress peer) throws IconFailure {
        if(peer==null)throw new IconFailure("Kein aktiver gekoppelter TV");
        if(!(peer instanceof Inet4Address))throw new IconFailure("TV-Adresse nicht IPv4");
        if(url==null||url.isEmpty())throw new IconFailure("TV meldet keine Icon-URL");
        if(url.length()>4096)throw new IconFailure("Icon-URL zu lang");
        try{
            URI u=new URI(url);
            if(!"http".equalsIgnoreCase(u.getScheme())||u.getRawUserInfo()!=null
                ||u.getRawQuery()!=null||u.getRawFragment()!=null)
                throw new IconFailure("Nicht erlaubtes Icon-URL-Format");
            if(!peer.getHostAddress().equals(u.getHost()))
                throw new IconFailure("Icon-Adresse stimmt nicht mit gekoppeltem TV überein");
            // TCL binds its file server to port 0 and publishes the actual listening
            // port in GET/PACKAGEINFO. Accept only the reported nonprivileged port
            // on the existing peer, excluding the known control endpoints.
            int port=u.getPort();
            if(port<1024||port>65535||port==6553||port==6466||port==6467)
                throw new IconFailure("Nicht erlaubter Icon-Port");
            String raw=u.getRawPath(),decoded=u.getPath();
            if(raw==null||decoded==null||!raw.startsWith(BASE)||!decoded.startsWith(BASE))
                throw new IconFailure("Icon-Pfad außerhalb des TCL-Bildordners");
            String name=decoded.substring(BASE.length());
            if(name.startsWith("screenShot/"))name=name.substring(11);
            if(name.isEmpty()||name.length()>1024||name.indexOf('/')>=0||name.indexOf('\\')>=0
                ||name.indexOf('%')>=0||name.contains(".."))
                throw new IconFailure("Nicht erlaubter Icon-Dateiname");
            for(int i=0;i<name.length();i++)if(Character.isISOControl(name.charAt(i)))
                throw new IconFailure("Nicht erlaubter Icon-Dateiname");
            String lower=name.toLowerCase(Locale.ROOT);
            if(!lower.endsWith(".png")&&!lower.endsWith(".jpg")&&!lower.endsWith(".jpeg"))
                throw new IconFailure("Nicht erlaubtes Icon-Dateiformat");
            String path=URI.create(u.toASCIIString()).getRawPath();
            for(int i=0;i<path.length();i++)if(path.charAt(i)<33||path.charAt(i)>126)
                throw new IconFailure("Nicht erlaubter Icon-Pfad");
            return new Target(peer,port,path);
        }catch(IllegalArgumentException|URISyntaxException e){
            throw new IconFailure("Ungültige Icon-URL");
        }
    }
    public static String requestPath(String url,InetAddress peer){
        try{return target(url,peer).path;}catch(IconFailure e){return null;}
    }
    public static byte[] fetch(Socket socket,Target target) throws IOException {
        socket.connect(new InetSocketAddress(target.peer,target.port),1500);
        socket.setSoTimeout(1800);
        long deadline=System.nanoTime()+5_000_000_000L;
        OutputStream out=socket.getOutputStream();
        out.write(("GET "+target.path+" HTTP/1.0\r\nHost: "+target.peer.getHostAddress()+":"+target.port+
            "\r\nAccept: image/png, image/jpeg\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        return readResponse(new BufferedInputStream(socket.getInputStream()),deadline);
    }
    static byte[] readResponse(InputStream in,long deadline) throws IOException {
        String status=line(in,deadline);
        if(!status.matches("HTTP/1\\.[01] 200(?: .*)?"))
            throw new IconFailure(status.matches("HTTP/1\\.[01] [0-9]{3}(?: .*)?") ?
                "HTTP " + status.substring(9,12) : "Ungültiger HTTP-Status");
        int length=-1,headerBytes=status.length();boolean chunked=false;
        for(;;){
            String h=line(in,deadline);headerBytes+=h.length()+2;
            if(headerBytes>MAX_HEADERS)throw new IconFailure("Headers too large");
            if(h.isEmpty())break;
            int colon=h.indexOf(':');if(colon<=0)throw new IconFailure("Invalid header");
            String key=h.substring(0,colon).trim().toLowerCase(Locale.ROOT),value=h.substring(colon+1).trim();
            if(key.equals("content-length")){
                int n;
                try{n=Integer.parseInt(value);}catch(NumberFormatException e){throw new IconFailure("Invalid size");}
                if(n<0||n>MAX_BYTES||(length>=0&&length!=n))throw new IconFailure("Invalid size");
                length=n;
            }else if(key.equals("transfer-encoding")){
                if(!value.equalsIgnoreCase("chunked"))throw new IconFailure("Unsupported encoding");
                chunked=true;
            }else if(key.equals("content-encoding")&&!value.equalsIgnoreCase("identity"))
                throw new IconFailure("Unsupported encoding");
            else if(key.equals("content-type")){
                String mime=value.split(";",2)[0].trim().toLowerCase(Locale.ROOT);
                if(!mime.equals("image/png")&&!mime.equals("image/jpeg")&&!mime.equals("image/jpg")
                    &&!mime.equals("application/octet-stream"))throw new IconFailure("Not an icon");
            }
        }
        if(chunked&&length>=0)throw new IconFailure("Ambiguous body");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        if(chunked){
            while(true){
                String size=line(in,deadline).split(";",2)[0].trim();
                int n;try{n=Integer.parseInt(size,16);}catch(NumberFormatException e){throw new IconFailure("Invalid chunk");}
                if(n==0)break;
                if(n<0||n>MAX_BYTES-bytes.size())throw new IconFailure("Icon too large");
                copy(in,bytes,n,deadline);
                if(!line(in,deadline).isEmpty())throw new IconFailure("Invalid chunk");
            }
        }else if(length>=0)copy(in,bytes,length,deadline);
        else{
            byte[] buffer=new byte[8192];
            for(;;){
                check(deadline);
                int n=in.read(buffer);
                if(n<0)break;
                if(n>MAX_BYTES-bytes.size())throw new IconFailure("Icon too large");
                bytes.write(buffer,0,n);
            }
        }
        byte[] body=bytes.toByteArray();
        if(!isImage(body))throw new IconFailure("Unsupported image");
        return body;
    }
    public static String diagnostic(Throwable error) {
        if(error instanceof IconFailure)return error.getMessage();
        if(error instanceof SocketTimeoutException)return "Zeitüberschreitung";
        if(error instanceof ConnectException)return "Verbindung zum Bildserver abgelehnt";
        if(error instanceof EOFException)return "Unvollständige Antwort";
        if(error instanceof InterruptedIOException)return "Abruf abgebrochen/Zeitlimit";
        if(error instanceof SocketException)return "Socket-Verbindung unterbrochen";
        return "Abruf fehlgeschlagen ("+error.getClass().getSimpleName()+")";
    }
    static boolean isImage(byte[] b){
        return b.length>=8&&((b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10
            ||(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255);
    }
    private static void copy(InputStream in,ByteArrayOutputStream out,int remaining,long deadline) throws IOException{
        byte[] buf=new byte[8192];
        while(remaining>0){
            check(deadline);
            int n=in.read(buf,0,Math.min(buf.length,remaining));
            if(n<0)throw new EOFException();
            if(n==0)continue;
            out.write(buf,0,n);remaining-=n;
        }
    }
    private static String line(InputStream in,long deadline) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        while(b.size()<MAX_HEADERS){
            check(deadline);int c=in.read();
            if(c<0)throw new EOFException();
            if(c==10){String s=new String(b.toByteArray(),StandardCharsets.US_ASCII);return s.endsWith("\r")?s.substring(0,s.length()-1):s;}
            b.write(c);
        }
        throw new IconFailure("Line too long");
    }
    private static void check(long deadline) throws IOException{
        if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw new InterruptedIOException();
    }
}
