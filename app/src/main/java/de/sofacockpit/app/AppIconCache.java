package de.sofacockpit.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.LruCache;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Device-scoped local icon cache. UI-thread lookups; bounded foreground-only worker.
 * Disk holds only image bytes under hashed keys, never addresses or pairing data. */
final class AppIconCache implements AutoCloseable {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(64));
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(4*1024*1024){
        @Override protected int sizeOf(String key,Bitmap b){return b.getAllocationByteCount();}
    };
    private final Set<String> pending=new HashSet<>();
    private final Map<String,Long> failures=new HashMap<>();
    private final File dir;
    private final Runnable changed;
    private String device="";
    private boolean enabled,closed,refreshPosted;
    private volatile int generation;
    private Socket activeSocket;
    AppIconCache(Context context,Runnable changed){
        dir=new File(context.getCacheDir(),"tv-app-icons");this.changed=changed;
    }
    void device(String key){
        if(!device.equals(key)){device=key;cancelPending();failures.clear();}
    }
    void resume(){if(!closed)enabled=true;}
    void pause(){enabled=false;cancelPending();}
    void retryFailures(){failures.clear();}
    private void cancelPending(){
        synchronized(this){
            generation++;
            if(activeSocket!=null)try{activeSocket.close();}catch(IOException ignored){}
        }
        worker.getQueue().clear();pending.clear();refreshPosted=false;
    }
    @Override public void close(){closed=true;pause();worker.shutdownNow();memory.evictAll();}
    Bitmap get(String pkg,String url,InetAddress peer){
        if(closed||device.isEmpty()||pkg==null||url==null||url.isEmpty())return null;
        String key=hash(device+"\n"+pkg+"\n"+url);
        Bitmap bitmap=memory.get(key);
        if(bitmap!=null)return bitmap;
        if(!enabled||peer==null||pending.contains(key))return null;
        String path=TvIconHttp.requestPath(url,peer);
        if(path==null)return null;
        Long failed=failures.get(key);
        if(failed!=null&&SystemClock.elapsedRealtime()-failed<60000)return null;
        int token=generation;
        pending.add(key);
        try{worker.execute(()->load(key,peer,path,token));}
        catch(RejectedExecutionException e){pending.remove(key);}
        return null;
    }
    private void load(String key,InetAddress peer,String path,int token){
        if(token!=generation)return;
        Bitmap bitmap=null;
        File file=new File(dir,key+".img");
        try{
            byte[] data=read(file);
            bitmap=decode(data);
            if(bitmap!=null)file.setLastModified(System.currentTimeMillis());
            else{
                file.delete();
                try(Socket socket=new Socket(Proxy.NO_PROXY)){
                    synchronized(this){
                        if(token!=generation)return;
                        activeSocket=socket;
                    }
                    try{data=TvIconHttp.fetch(socket,peer,path);}
                    finally{synchronized(this){if(activeSocket==socket)activeSocket=null;}}
                }
                bitmap=decode(data);
                if(bitmap!=null&&token==generation)save(file,data);
            }
        }catch(IOException|RuntimeException ignored){}
        Bitmap result=bitmap;
        main.post(()->{
            if(token!=generation||closed)return;
            pending.remove(key);
            if(result==null){
                if(failures.size()>512)failures.clear();
                failures.put(key,SystemClock.elapsedRealtime());
                return;
            }
            failures.remove(key);memory.put(key,result);
            if(!refreshPosted){
                refreshPosted=true;
                main.postDelayed(()->{
                    if(token!=generation||closed)return;
                    refreshPosted=false;
                    if(enabled)changed.run();
                },90);
            }
        });
    }
    private static byte[] read(File file) throws IOException{
        if(!file.isFile()||file.length()<8||file.length()>TvIconHttp.MAX_BYTES)return null;
        try(InputStream in=new FileInputStream(file)){
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] b=new byte[8192];
            for(int n;(n=in.read(b))!=-1;){
                if(n>TvIconHttp.MAX_BYTES-out.size())return null;out.write(b,0,n);
            }
            return out.toByteArray();
        }catch(FileNotFoundException ignored){return null;}
    }
    private static Bitmap decode(byte[] bytes){
        if(bytes==null||!TvIconHttp.isImage(bytes))return null;
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
        if(o.outWidth<1||o.outHeight<1||o.outWidth>8192||o.outHeight>8192||
            (long)o.outWidth*o.outHeight>16777216L)return null;
        o.inSampleSize=1;
        while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>256)o.inSampleSize*=2;
        o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
    }
    private void save(File file,byte[] data){
        if(!dir.exists()&&!dir.mkdirs())return;
        File temp=new File(dir,file.getName()+".tmp");
        try{
            try(OutputStream out=new FileOutputStream(temp)){out.write(data);}
            if(!temp.renameTo(file))return;
            File[] files=dir.listFiles((d,n)->n.endsWith(".img"));
            if(files==null)return;
            Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());
            long total=0;
            for(int i=0;i<files.length;i++){
                total+=files[i].length();
                if(i>=64||total>16L*1024*1024)files[i].delete();
            }
        }catch(IOException|RuntimeException ignored){}finally{temp.delete();}
    }
    private static String hash(String key){
        try{
            byte[] b=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(64);
            for(byte v:b){out.append(Character.forDigit((v>>4)&15,16));out.append(Character.forDigit(v&15,16));}
            return out.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
