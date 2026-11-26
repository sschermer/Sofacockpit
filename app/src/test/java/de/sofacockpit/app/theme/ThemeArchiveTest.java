package de.sofacockpit.app.theme;

import org.junit.Test;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import static org.junit.Assert.*;

public class ThemeArchiveTest {
    private File assets() {
        File f=new File("src/main/assets/themes");
        if(!f.isDirectory()) f=new File("app/src/main/assets/themes");
        assertTrue("Theme assets directory",f.isDirectory());
        return f;
    }
    private byte[] original() throws Exception {
        try(InputStream in=new FileInputStream(new File(assets(),"minimal-premium.sofatheme"))) {
            return ThemeArchive.limited(in,ThemeArchive.MAX_FILE);
        }
    }
    private java.util.Map<String,byte[]> unpack(byte[] bytes) throws Exception {
        java.util.Map<String,byte[]> out=new java.util.LinkedHashMap<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for(ZipEntry e;(e=zip.getNextEntry())!=null;) {
                out.put(e.getName(),ThemeArchive.limited(zip,ThemeArchive.MAX_FILE));zip.closeEntry();
            }
        }
        return out;
    }
    private byte[] zip(java.util.Map<String,byte[]> files) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)) {
            for(java.util.Map.Entry<String,byte[]> e:files.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
    @Test public void allFiveRealPackagesAreValid() throws Exception {
        String[] ids={"minimal-premium","av-konsole","high-tech-hud","soft-industrial","executive-monochrome"};
        for(String id:ids) {
            try(InputStream in=new FileInputStream(new File(assets(),id+".sofatheme"))) {
                ThemeArchive a=ThemeArchive.read(in);
                assertEquals(id,a.theme.id);
                assertTrue(CockpitTheme.contrast(a.theme.background,a.theme.text)>=4.5);
                assertTrue(CockpitTheme.contrast(a.theme.control,a.theme.accent)>=4.5);
            }
        }
        assertEquals(CockpitTheme.DEFAULT_ID,CockpitTheme.fallback().id);
    }
    @Test public void customThemeRoundTrip() throws Exception {
        java.util.Map<String,byte[]> files=unpack(original());
        JSONObject manifest=new JSONObject(new String(files.get("manifest.json"),StandardCharsets.UTF_8));
        manifest.put("id","my-custom-theme").put("name","My Theme");
        files.put("manifest.json",manifest.toString().getBytes(StandardCharsets.UTF_8));
        byte[] data=zip(files);
        ThemeArchive archive=ThemeArchive.read(new ByteArrayInputStream(data));
        assertEquals("my-custom-theme",archive.theme.id);
        assertArrayEquals(data,archive.bytes);
    }
    @Test public void unknownPathAndExecutableRejected() throws Exception {
        for(String entry:new String[]{"../theme.json","script.js","icons/evil.svg","/absolute.png"}) {
            java.util.Map<String,byte[]> files=unpack(original());files.put(entry,new byte[]{1});
            try { ThemeArchive.read(new ByteArrayInputStream(zip(files)));fail(entry); }
            catch(IOException expected) {}
        }
    }
    @Test public void tooLargeCompressedContentRejected() throws Exception {
        java.util.Map<String,byte[]> files=unpack(original());
        files.put("preview.png",new byte[ThemeArchive.MAX_FILE+1]);
        try { ThemeArchive.read(new ByteArrayInputStream(zip(files)));fail(); }
        catch(IOException expected) {}
    }
    @Test public void rejectsBadContrastAndIncompatibleSchema() throws Exception {
        java.util.Map<String,byte[]> files=unpack(original());
        JSONObject theme=new JSONObject(new String(files.get("theme.json"),StandardCharsets.UTF_8));
        theme.put("text","#101719");
        files.put("theme.json",theme.toString().getBytes(StandardCharsets.UTF_8));
        try { ThemeArchive.read(new ByteArrayInputStream(zip(files)));fail(); }
        catch(IllegalArgumentException expected) {}
        files=unpack(original());
        JSONObject manifest=new JSONObject(new String(files.get("manifest.json"),StandardCharsets.UTF_8));
        manifest.put("schema",2);
        files.put("manifest.json",manifest.toString().getBytes(StandardCharsets.UTF_8));
        try { ThemeArchive.read(new ByteArrayInputStream(zip(files)));fail(); }
        catch(IllegalArgumentException expected) {}
    }
    @Test public void wholeDialSectorGeometry() {
        assertEquals(DialGeometry.OK,DialGeometry.hit(100,100,200,.32f));
        assertEquals(DialGeometry.UP,DialGeometry.hit(100,3,200,.32f));
        assertEquals(DialGeometry.RIGHT,DialGeometry.hit(196,100,200,.32f));
        assertEquals(DialGeometry.DOWN,DialGeometry.hit(100,196,200,.32f));
        assertEquals(DialGeometry.LEFT,DialGeometry.hit(3,100,200,.32f));
        assertEquals(DialGeometry.UP,DialGeometry.hit(133,42,200,.32f));
        assertEquals(DialGeometry.RIGHT,DialGeometry.hit(164,65,200,.32f));
        assertEquals(DialGeometry.NONE,DialGeometry.hit(0,0,200,.32f));
    }
}
