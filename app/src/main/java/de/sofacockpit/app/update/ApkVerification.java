package de.sofacockpit.app.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;

/** Android reads/verifies the archive signer; the system installer verifies again. */
final class ApkVerification {
    static final String PACKAGE = "de.sofacockpit.app";
    static final String CERT_SHA256 = "6bc8b1ff2f2f2bc6db4c56d4ffa53c080c05f48089dab7b66ccb7d74fa54ac83";
    private ApkVerification(){}
    private static int flags(){
        return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
    }
    static PackageInfo installed(Context context)throws PackageManager.NameNotFoundException{
        return context.getPackageManager().getPackageInfo(context.getPackageName(),flags());
    }
    static void verify(Context context, File apk, ReleaseCatalog.Release expected)throws Exception{
        PackageInfo current=installed(context);
        PackageInfo next=context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),flags());
        if(next==null||!PACKAGE.equals(current.packageName)||!PACKAGE.equals(next.packageName))
            throw new IOException("APK-Paketname ist nicht Sofacockpit.");
        ReleaseCatalog.Version currentVersion=ReleaseCatalog.Version.parse(current.versionName);
        ReleaseCatalog.Version nextVersion=ReleaseCatalog.Version.parse(next.versionName);
        if(currentVersion==null||nextVersion==null||
            nextVersion.compareTo(expected.version)!=0||nextVersion.compareTo(currentVersion)<=0||
            code(next)<=code(current))
            throw new IOException("APK-Version stimmt nicht überein oder ist nicht neuer.");
        if(!trusted(current)||!trusted(next))
            throw new IOException("APK-Signatur stimmt nicht mit der offiziellen Sofacockpit-Signatur überein.");
    }
    private static long code(PackageInfo info){
        return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
    }
    private static boolean trusted(PackageInfo info)throws Exception{
        Signature[] signers=Build.VERSION.SDK_INT>=28?
            (info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()):info.signatures;
        return signers!=null&&signers.length==1&&CERT_SHA256.equals(
            GithubUpdates.hex(MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray())));
    }
}
