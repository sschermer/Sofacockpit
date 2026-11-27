package de.sofacockpit.app.update;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class ReleaseCatalogTest {
    private JSONObject release(String version,boolean pre)throws Exception{
        String tag="sofacockpit-lab-v"+version;
        return new JSONObject().put("tag_name",tag).put("draft",false).put("prerelease",pre)
            .put("published_at","2026-11-26T12:00:00Z").put("body","Notes")
            .put("assets",new JSONArray().put(new JSONObject().put("name",ReleaseCatalog.APK_NAME)
                .put("state","uploaded").put("size",1234)
                .put("browser_download_url",ReleaseCatalog.REPO_URL+"/releases/download/"+tag+"/"+ReleaseCatalog.APK_NAME)));
    }
    private ReleaseCatalog.Version version(String value){return ReleaseCatalog.Version.parse(value);}
    @Test public void numericVersionSelectionAndIsolation()throws Exception{
        ReleaseCatalog c=new ReleaseCatalog();
        JSONObject stable=release("99.0.0",true);
        stable.put("tag_name","sofacockpit-v99.0.0");
        stable.getJSONArray("assets").getJSONObject(0).put("browser_download_url",
            ReleaseCatalog.REPO_URL+"/releases/download/sofacockpit-v99.0.0/"+ReleaseCatalog.APK_NAME);
        JSONObject wrongAsset=release("98.0.0",true);
        wrongAsset.getJSONArray("assets").getJSONObject(0).put("name","Sofacockpit.apk")
            .put("browser_download_url",ReleaseCatalog.REPO_URL+"/releases/download/sofacockpit-lab-v98.0.0/Sofacockpit.apk");
        c.addPage(new JSONArray().put(stable).put(wrongAsset).put(release("97.0.0",false)).put(release("0.8.9",true)));
        c.addPage(new JSONArray().put(release("0.10.0",true)));
        assertEquals("0.10.0",c.choose(version("0.8.0"),true,"",false).version.toString());
        assertTrue(c.choose(version("0.8.0"),true,"",false).prerelease);
        assertNull(c.choose(version("0.8.0"),false,"",false));
        assertNull(c.choose(version("0.10.0"),true,"",true));
        assertNull(c.choose(version("1.0.0"),true,"",true));
    }

    @Test public void skipFloorAndManualOverride()throws Exception{
        ReleaseCatalog c=new ReleaseCatalog();
        c.addPage(new JSONArray().put(release("0.10.0",true)));
        assertNull(c.choose(version("0.9.0"),true,"0.10.0",false));
        assertNotNull(c.choose(version("0.9.0"),true,"0.10.0",true));
        c.addPage(new JSONArray().put(release("0.11.0",true)));
        assertEquals("0.11.0",c.choose(version("0.9.0"),true,"0.10.0",false).version.toString());
        assertNull(c.choose(version("0.9.0"),false,"",true));
    }
    @Test public void ignoresDraftMissingOrUntrustedApks()throws Exception{
        JSONObject draft=release("9.0.0",true).put("draft",true);
        JSONObject missing=release("8.0.0",true).put("assets",new JSONArray());
        JSONObject untrusted=release("7.0.0",true);
        untrusted.getJSONArray("assets").getJSONObject(0).put("browser_download_url","https://example.org/app.apk");
        JSONObject pending=release("6.0.0",true);
        pending.getJSONArray("assets").getJSONObject(0).put("state","new");
        JSONObject invalidDigest=release("5.0.0",true);
        invalidDigest.getJSONArray("assets").getJSONObject(0).put("digest","bad");
        JSONObject oversized=release("4.0.0",true);
        oversized.getJSONArray("assets").getJSONObject(0).put("size",ReleaseCatalog.MAX_APK_BYTES+1);
        JSONObject unpublished=release("3.0.0",true).put("published_at",JSONObject.NULL);
        ReleaseCatalog c=new ReleaseCatalog();
        c.addPage(new JSONArray().put(draft).put(missing).put(untrusted).put(pending)
            .put(invalidDigest).put(oversized).put(unpublished).put(release("1.0.0",true)));
        assertEquals("1.0.0",c.choose(version("0.9.0"),true,"",false).version.toString());
    }
    @Test public void strictVersionGrammar(){
        assertNull(version("0.9.0-debug"));
        assertNull(version("0.09.0"));
        assertNull(version("9999999999.0.0"));
        assertNull(ReleaseCatalog.Version.fromTag("other-v1.0.0"));
        assertNull(ReleaseCatalog.Version.fromTag("v1.2.3"));
        assertNull(ReleaseCatalog.Version.fromTag("sofacockpit-v1.2.3"));
        assertEquals("1.2.3",ReleaseCatalog.Version.fromTag("sofacockpit-lab-v1.2.3").toString());
    }
    @Test public void validDownloadHostsOnly()throws Exception{
        assertTrue(GithubUpdates.allowedDownload(new URL("https://github.com/path")));
        assertTrue(GithubUpdates.allowedDownload(new URL("https://release-assets.githubusercontent.com/path?signature=1")));
        assertFalse(GithubUpdates.allowedDownload(new URL("http://github.com/path")));
        assertFalse(GithubUpdates.allowedDownload(new URL("https://github.com.evil.example/path")));
        assertFalse(GithubUpdates.allowedDownload(new URL("https://github.com:8443/path")));
        assertFalse(GithubUpdates.allowedDownload(new URL("https://user@github.com/path")));
    }
    @Test public void checksSizeAndDigest()throws Exception{
        byte[] data="abc".getBytes(StandardCharsets.UTF_8);
        String digest="sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        GithubUpdates.copyVerified(new ByteArrayInputStream(data),out,3,digest,()->false,p->{},Long.MAX_VALUE);
        assertArrayEquals(data,out.toByteArray());
        for(long size:new long[]{2,4}){
            try{
                GithubUpdates.copyVerified(new ByteArrayInputStream(data),new ByteArrayOutputStream(),size,digest,()->false,p->{},Long.MAX_VALUE);
                fail("Wrong size accepted");
            }catch(IOException expected){}
        }
        try{
            GithubUpdates.copyVerified(new ByteArrayInputStream(data),new ByteArrayOutputStream(),3,
                "sha256:0000000000000000000000000000000000000000000000000000000000000000",()->false,p->{},Long.MAX_VALUE);
            fail("Wrong digest accepted");
        }catch(IOException expected){}
        try{
            GithubUpdates.copyVerified(new ByteArrayInputStream(data),new ByteArrayOutputStream(),3,digest,()->true,p->{},Long.MAX_VALUE);
            fail("Cancelled transfer accepted");
        }catch(InterruptedIOException expected){}
    }
}
