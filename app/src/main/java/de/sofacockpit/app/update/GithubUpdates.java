package de.sofacockpit.app.update;

import org.json.JSONArray;
import org.json.JSONException;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Public GitHub HTTPS only. No credentials, LAN discovery or device commands. */
final class GithubUpdates {
    private static final String API="https://api.github.com/repos/"+ReleaseCatalog.REPO+"/releases";
    private static final int PAGE_SIZE=100, MAX_PAGES=20, MAX_JSON=4*1024*1024;
    private static final Set<String> DOWNLOAD_HOSTS=new HashSet<>(Arrays.asList(
        "github.com","release-assets.githubusercontent.com","objects.githubusercontent.com",
        "github-releases.githubusercontent.com"));
    private GithubUpdates(){}
    interface Cancellation { boolean cancelled(); }
    interface Progress { void changed(int percent); }

    static ReleaseCatalog query(Cancellation cancel) throws Exception {
        ReleaseCatalog catalog=new ReleaseCatalog();
        long deadline=System.nanoTime()+90_000_000_000L;
        for(int page=1;page<=MAX_PAGES;page++){
            check(cancel,deadline);
            URL url=new URL(API+"?per_page="+PAGE_SIZE+"&page="+page);
            HttpsURLConnection conn=connect(url);
            try{
                conn.setRequestProperty("Accept","application/vnd.github+json");
                conn.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
                int code=conn.getResponseCode();
                if(code==403||code==429)throw new IOException("GitHub-Abfragelimit oder Zugriffsbeschränkung. Später erneut versuchen.");
                if(code!=200)throw new IOException("GitHub antwortet mit HTTP "+code+".");
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=conn.getInputStream()){
                    byte[] buffer=new byte[8192];
                    for(int n;(n=in.read(buffer))!=-1;){
                        check(cancel,deadline);
                        if(n>MAX_JSON-out.size())throw new IOException("GitHub-Antwort zu groß.");
                        out.write(buffer,0,n);
                    }
                }
                JSONArray arr=new JSONArray(new String(out.toByteArray(),StandardCharsets.UTF_8));
                catalog.addPage(arr);
                if(arr.length()<PAGE_SIZE)return catalog;
            }finally{conn.disconnect();}
        }
        throw new IOException("Zu viele GitHub-Releases für eine vollständige Prüfung.");
    }

    static void download(ReleaseCatalog.Release release, File target,
                         Cancellation cancel,Progress progress) throws Exception {
        URL url=new URL(release.downloadUrl);
        if(!allowedDownload(url)||!"github.com".equals(url.getHost()))
            throw new IOException("Ungültige Downloadadresse.");
        long deadline=System.nanoTime()+300_000_000_000L;
        for(int redirects=0;redirects<=5;redirects++){
            check(cancel,deadline);
            HttpsURLConnection conn=connect(url);
            try{
                conn.setRequestProperty("Accept","application/octet-stream");
                conn.setRequestProperty("Accept-Encoding","identity");
                int code=conn.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308){
                    String location=conn.getHeaderField("Location");
                    if(location==null)throw new IOException("Downloadumleitung fehlt.");
                    URL next=new URL(url,location);
                    if(!allowedDownload(next))throw new IOException("Nicht erlaubte Downloadumleitung.");
                    url=next;continue;
                }
                if(code!=200)throw new IOException("Download fehlgeschlagen (HTTP "+code+").");
                long advertised=conn.getContentLengthLong();
                if(advertised>=0&&advertised!=release.size)
                    throw new IOException("Die APK-Dateigröße hat sich geändert.");
                try(InputStream in=conn.getInputStream();OutputStream out=new FileOutputStream(target)){
                    copyVerified(in,out,release.size,release.digest,cancel,progress,deadline);
                }
                return;
            }finally{conn.disconnect();}
        }
        throw new IOException("Zu viele Downloadumleitungen.");
    }

    static void copyVerified(InputStream in,OutputStream out,long size,String expectedDigest,
            Cancellation cancel,Progress progress,long deadline) throws Exception {
        if(size<=0||size>ReleaseCatalog.MAX_APK_BYTES)throw new IOException("Ungültige APK-Dateigröße.");
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        byte[] buffer=new byte[32*1024];long total=0;int previous=-1;
        for(int n;(n=in.read(buffer))!=-1;){
            check(cancel,deadline);
            if(n>size-total)throw new IOException("APK ist größer als angekündigt.");
            out.write(buffer,0,n);sha.update(buffer,0,n);total+=n;
            int percent=(int)(total*100/size);
            if(percent!=previous){previous=percent;progress.changed(percent);}
        }
        check(cancel,deadline);
        if(total!=size)throw new IOException("APK-Download unvollständig.");
        String actual="sha256:"+hex(sha.digest());
        if(!expectedDigest.isEmpty()&&!actual.equalsIgnoreCase(expectedDigest))
            throw new IOException("SHA-256-Prüfsumme stimmt nicht überein.");
        out.flush();
    }

    static String hex(byte[] bytes){
        final char[] digits="0123456789abcdef".toCharArray();
        char[] out=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++){
            out[i*2]=digits[(bytes[i]&255)>>>4];out[i*2+1]=digits[bytes[i]&15];
        }
        return new String(out);
    }
    static boolean allowedDownload(URL u){
        return "https".equals(u.getProtocol())&&DOWNLOAD_HOSTS.contains(u.getHost())&&
            u.getUserInfo()==null&&u.getRef()==null&&(u.getPort()==-1||u.getPort()==443);
    }
    private static HttpsURLConnection connect(URL url)throws IOException{
        if(!"https".equals(url.getProtocol()))throw new IOException("HTTPS erforderlich.");
        HttpsURLConnection c=(HttpsURLConnection)url.openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(15000);
        c.setUseCaches(false);c.setRequestProperty("User-Agent","Sofacockpit-Android-Updater");
        return c;
    }
    private static void check(Cancellation cancel,long deadline)throws IOException{
        if(cancel.cancelled()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Abgebrochen");
        if(System.nanoTime()>deadline)throw new IOException("Zeitlimit überschritten.");
    }
}
