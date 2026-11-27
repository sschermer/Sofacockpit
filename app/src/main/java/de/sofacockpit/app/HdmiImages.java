package de.sofacockpit.app;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.util.Base64;
import java.util.Objects;

/** Private, bounded thumbnails. No external URI, path or original file metadata is retained. */
final class HdmiImages {
    private static final int MAX_INPUT=8*1024*1024;
    private final SharedPreferences prefs;
    private final String[] cached=new String[4];
    private final Bitmap[] bitmaps=new Bitmap[4];
    HdmiImages(SharedPreferences prefs){this.prefs=prefs;}
    Bitmap get(int port){
        HdmiSettings.checkPort(port);
        String image=prefs.getString(HdmiSettings.imageKey(port),null);
        int i=port-1;
        if(!Objects.equals(image,cached[i])){
            Bitmap bitmap=null;
            if(image!=null)try{bitmap=decode(image);}catch(RuntimeException ignored){}
            cached[i]=image;bitmaps[i]=bitmap;
        }
        return bitmaps[i];
    }
    static Bitmap decode(String encoded){
        byte[] bytes=HdmiSettings.imageBytes(encoded);
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        if(bitmap==null||bitmap.getWidth()>HdmiSettings.MAX_IMAGE_SIDE||bitmap.getHeight()>HdmiSettings.MAX_IMAGE_SIDE)
            throw new IllegalArgumentException("HDMI-Bild nicht lesbar");
        return bitmap;
    }
    /** Run on a worker. Decode bounds before allocation, resample and re-encode as PNG. */
    static String importImage(InputStream input) throws IOException {
        if(input==null)throw new IOException("Bild nicht lesbar");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];
        for(int n;(n=input.read(buf))!=-1;){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            if(n>MAX_INPUT-bytes.size())throw new IOException("Bild zu groß (maximal 8 MB)");
            bytes.write(buf,0,n);
        }
        byte[] data=bytes.toByteArray();
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,data.length,options);
        if(options.outWidth<=0||options.outHeight<=0||options.outWidth>16384||options.outHeight>16384||
            (long)options.outWidth*options.outHeight>64L*1024*1024)
            throw new IOException("Nicht unterstütztes Bild oder Bildgröße");
        options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>HdmiSettings.MAX_IMAGE_SIDE)
            options.inSampleSize*=2;
        options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);
        if(bitmap==null)throw new IOException("Bild nicht lesbar");
        try{
            for(int attempt=0;attempt<8;attempt++){
                int longest=Math.max(bitmap.getWidth(),bitmap.getHeight());
                if(longest<=HdmiSettings.MAX_IMAGE_SIDE){
                    ByteArrayOutputStream png=new ByteArrayOutputStream();
                    if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,png))throw new IOException("Bild nicht speicherbar");
                    if(png.size()<=HdmiSettings.MAX_IMAGE_BYTES){
                        String encoded=Base64.getEncoder().encodeToString(png.toByteArray());
                        HdmiSettings.imageBytes(encoded);
                        return encoded;
                    }
                }
                float scale=Math.min(.75f,(float)HdmiSettings.MAX_IMAGE_SIDE/longest);
                int w=Math.max(1,Math.round(bitmap.getWidth()*scale)),h=Math.max(1,Math.round(bitmap.getHeight()*scale));
                if(w==bitmap.getWidth()&&h==bitmap.getHeight())break;
                Bitmap next=Bitmap.createScaledBitmap(bitmap,w,h,true);
                if(next!=bitmap)bitmap.recycle();
                bitmap=next;
            }
            throw new IOException("Bild konnte nicht ausreichend verkleinert werden");
        }finally{bitmap.recycle();}
    }
}
