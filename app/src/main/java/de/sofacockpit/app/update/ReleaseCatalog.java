package de.sofacockpit.app.update;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure numeric release selection. GitHub's prerelease flag, not tag spelling, determines channel. */
public final class ReleaseCatalog {
    public static final String REPO = "sschermer/Sofacockpit";
    public static final String REPO_URL = "https://github.com/" + REPO;
    public static final String APK_NAME = "Sofacockpit.apk";
    public static final long MAX_APK_BYTES = 128L * 1024 * 1024;
    private static final Pattern VERSION = Pattern.compile("(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})");
    private static final String PREFIX = "sofacockpit-v";
    private Release newest, stable;

    public static final class Version implements Comparable<Version> {
        private final int major, minor, patch;
        private Version(int a,int b,int c){major=a;minor=b;patch=c;}
        public static Version parse(String value){
            if(value==null)return null;
            Matcher m=VERSION.matcher(value);
            if(!m.matches())return null;
            return new Version(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));
        }
        public static Version fromTag(String tag){
            if(tag==null)return null;
            if(tag.startsWith(PREFIX))return parse(tag.substring(PREFIX.length()));
            if(tag.startsWith("v"))return parse(tag.substring(1));
            return null;
        }
        @Override public int compareTo(Version v){
            int n=Integer.compare(major,v.major);
            if(n==0)n=Integer.compare(minor,v.minor);
            if(n==0)n=Integer.compare(patch,v.patch);
            return n;
        }
        @Override public String toString(){return major+"."+minor+"."+patch;}
    }

    public static final class Release {
        public final Version version;
        public final String tag, notes, downloadUrl, digest;
        public final boolean prerelease;
        public final long size;
        private Release(Version version,String tag,boolean prerelease,String notes,String url,String digest,long size){
            this.version=version;this.tag=tag;this.prerelease=prerelease;this.notes=notes;
            downloadUrl=url;this.digest=digest;this.size=size;
        }
    }

    public void addPage(JSONArray entries){
        for(int i=0;i<entries.length();i++){
            JSONObject o=entries.optJSONObject(i);
            Release r=parseRelease(o);
            if(r==null)continue;
            if(newer(r,newest))newest=r;
            if(!r.prerelease&&newer(r,stable))stable=r;
        }
    }
    private static boolean newer(Release r,Release current){
        if(current==null)return true;
        int c=r.version.compareTo(current.version);
        return c>0 || (c==0 && current.prerelease && !r.prerelease);
    }
    public Release choose(Version installed, boolean includePre, String skippedVersion, boolean manual){
        Release candidate=includePre?newest:stable;
        if(installed==null||candidate==null||candidate.version.compareTo(installed)<=0)return null;
        Version skip=Version.parse(skippedVersion);
        if(!manual && skip!=null && candidate.version.compareTo(skip)<=0)return null;
        return candidate;
    }

    private static Release parseRelease(JSONObject o){
        if(o==null||!Boolean.FALSE.equals(o.opt("draft"))||
            !(o.opt("prerelease") instanceof Boolean)||
            o.isNull("published_at")||o.optString("published_at","").isEmpty())return null;
        String tag=o.optString("tag_name","");
        Version v=Version.fromTag(tag);
        if(v==null)return null;
        JSONArray assets=o.optJSONArray("assets");
        if(assets==null)return null;
        String expected=REPO_URL+"/releases/download/"+tag+"/"+APK_NAME;
        JSONObject apk=null;
        for(int i=0;i<assets.length();i++){
            JSONObject asset=assets.optJSONObject(i);
            if(asset!=null&&APK_NAME.equals(asset.optString("name",""))){
                if(apk!=null)return null;
                apk=asset;
            }
        }
        if(apk==null||!"uploaded".equals(apk.optString("state",""))||
            !expected.equals(apk.optString("browser_download_url","")))return null;
        Object bytes=apk.opt("size");
        if(!(bytes instanceof Number))return null;
        long size=((Number)bytes).longValue();
        if(size<=0||size>MAX_APK_BYTES||((Number)bytes).doubleValue()!=size)return null;
        String digest=apk.isNull("digest")?"":apk.optString("digest","");
        if(!digest.isEmpty()&&!digest.matches("sha256:[a-fA-F0-9]{64}"))return null;
        String body=o.isNull("body")?"":o.optString("body","");
        if(body.length()>5000)body=body.substring(0,5000)+"\n…";
        return new Release(v,tag,o.optBoolean("prerelease"),body,expected,
            digest.toLowerCase(Locale.ROOT),size);
    }
}
