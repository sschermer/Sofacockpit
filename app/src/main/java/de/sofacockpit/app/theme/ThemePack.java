package de.sofacockpit.app.theme;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;

/** Decode only bounded, non-executable raster assets. */
public final class ThemePack {
    public final CockpitTheme theme;
    public final Bitmap preview, texture, arrow;
    final byte[] bytes;
    public final boolean builtin;

    public ThemePack(ThemeArchive archive,boolean builtin) throws IOException {
        theme=archive.theme;bytes=archive.bytes;this.builtin=builtin;
        preview=decode(archive.preview,768);
        texture=decode(archive.texture,512);
        arrow=decode(archive.arrow,128);
    }
    private ThemePack() {
        theme=CockpitTheme.fallback(); bytes=null; builtin=true;
        preview=texture=arrow=null;
    }
    public static ThemePack fallback() { return new ThemePack(); }
    private static Bitmap decode(byte[] data,int limit) throws IOException {
        if(data==null) return null;
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,data.length,bounds);
        if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>limit||bounds.outHeight>limit||
            !("image/png".equals(bounds.outMimeType)||"image/webp".equals(bounds.outMimeType)))
            throw new IOException("Ungültiges oder zu großes Theme-Bild");
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap image=BitmapFactory.decodeByteArray(data,0,data.length,opts);
        if(image==null) throw new IOException("Theme-Bild nicht lesbar");
        return image;
    }
}
