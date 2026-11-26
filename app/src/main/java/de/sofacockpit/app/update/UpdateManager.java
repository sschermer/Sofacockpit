package de.sofacockpit.app.update;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import de.sofacockpit.app.theme.CockpitTheme;
import de.sofacockpit.app.theme.ThemeArt;
import de.sofacockpit.app.theme.ThemePack;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Activity-owned updater. No scheduled service, installation or download without user consent. */
public final class UpdateManager implements AutoCloseable {
    private static final String AUTO="auto_check", ALL="include_prerelease";
    private final ComponentActivity activity;
    private final Supplier<ThemePack> theme;
    private final SharedPreferences prefs;
    private final ActivityResultLauncher<Intent> unknownSources;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final String installedName;
    private final ReleaseCatalog.Version installed;
    private volatile boolean closed;
    private boolean resumed, checking, manualRequested, downloading;
    private ReleaseCatalog pendingCatalog;
    private boolean pendingManual;
    private ReleaseCatalog.Release permissionRelease, approvedDownload;
    private File verifiedApk;
    private ReleaseCatalog.Release verifiedRelease;
    private AtomicBoolean cancelDownload;
    private AlertDialog activeDialog;
    private ProgressBar progress;
    private TextView progressText;
    private final Runnable present=this::maybePresent;

    public UpdateManager(ComponentActivity activity,Supplier<ThemePack> theme){
        this.activity=activity;this.theme=theme;
        prefs=activity.getSharedPreferences("software_updates",ComponentActivity.MODE_PRIVATE);
        String version="Unbekannt";
        try{
            String found=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;
            if(found!=null)version=found;
        }catch(PackageManager.NameNotFoundException ignored){}
        installedName=version;installed=ReleaseCatalog.Version.parse(version);
        unknownSources=activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{
            ReleaseCatalog.Release release=permissionRelease;permissionRelease=null;
            if(closed||release==null)return;
            if(canInstall()){
                approvedDownload=release;schedulePresentation();
            }else toast("Installation nicht erlaubt. Über Software-Updates erneut versuchen.");
        });
    }

    public String versionName(){return installedName;}
    private boolean automatic(){return prefs.getBoolean(AUTO,true);}
    private boolean all(){return prefs.getBoolean(ALL,true);}
    private String skipKey(boolean all){return all?"skipped_all":"skipped_stable";}
    public void startAutomaticCheck(){if(automatic())check(false);}
    public void onResume(){resumed=true;schedulePresentation();}
    public void onPause(){resumed=false;main.removeCallbacks(present);}
    public void onWindowFocus(){schedulePresentation();}
    private void schedulePresentation(){
        if(closed||!resumed)return;
        main.removeCallbacks(present);main.postDelayed(present,220);
    }
    private boolean foreground(){
        return !closed&&resumed&&!activity.isFinishing()&&!activity.isDestroyed()&&activity.hasWindowFocus();
    }

    private void check(boolean manual){
        if(closed)return;
        if(downloading||permissionRelease!=null||approvedDownload!=null||verifiedApk!=null){
            if(manual)toast("Ein Update wird bereits vorbereitet.");
            return;
        }
        if(installed==null){if(manual)toast("Installierte Versionsnummer nicht unterstützt.");return;}
        pendingCatalog=null;
        manualRequested|=manual;
        if(checking)return;
        checking=true;
        if(manual)toast("Suche nach Updates …");
        worker.execute(()->{
            try{
                ReleaseCatalog catalog=GithubUpdates.query(()->closed);
                main.post(()->{
                    if(closed)return;
                    checking=false;pendingManual=manualRequested;manualRequested=false;
                    pendingCatalog=catalog;schedulePresentation();
                });
            }catch(Exception ex){
                main.post(()->{
                    if(closed)return;
                    checking=false;
                    boolean notify=manualRequested;manualRequested=false;
                    if(notify)toast("Updateprüfung nicht möglich. Internetverbindung prüfen und später erneut versuchen.");
                });
            }
        });
    }

    private void maybePresent(){
        if(!foreground()||activeDialog!=null)return;
        if(verifiedApk!=null){
            File file=verifiedApk;ReleaseCatalog.Release release=verifiedRelease;
            verifiedApk=null;verifiedRelease=null;
            install(file,release);return;
        }
        if(approvedDownload!=null){
            ReleaseCatalog.Release release=approvedDownload;approvedDownload=null;
            if(canInstall())download(release);
            else toast("Installationsberechtigung fehlt. Bitte erneut versuchen.");
            return;
        }
        if(downloading||permissionRelease!=null||pendingCatalog==null)return;
        ReleaseCatalog catalog=pendingCatalog;pendingCatalog=null;
        boolean manual=pendingManual;pendingManual=false;
        if(!manual&&!automatic())return;
        boolean include=all();
        ReleaseCatalog.Release release=catalog.choose(installed,include,prefs.getString(skipKey(include),""),manual);
        if(release==null){
            if(manual)toast(include?"Keine neuere veröffentlichte Version verfügbar.":"Kein neueres stabiles Release verfügbar.");
            return;
        }
        showOffer(release,include);
    }

    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private CockpitTheme palette(){return theme.get().theme;}
    private TextView text(String label,int size,int color,boolean bold){
        CockpitTheme t=palette();
        TextView v=new TextView(activity);v.setText(label);v.setTextSize(size*t.fontScale);v.setTextColor(color);
        v.setTypeface(ThemeArt.typeface(t,bold?Typeface.BOLD:Typeface.NORMAL));
        v.setPadding(0,dp(4),0,dp(4));return v;
    }
    private LinearLayout body(){
        CockpitTheme t=palette();
        LinearLayout b=new LinearLayout(activity);b.setOrientation(LinearLayout.VERTICAL);
        b.setBackgroundColor(t.background);b.setPadding(dp(18),dp(12),dp(18),dp(12));return b;
    }
    private void show(AlertDialog dialog){
        if(closed||activity.isFinishing())return;
        activeDialog=dialog;
        dialog.setOnDismissListener(d->{
            if(activeDialog==dialog)activeDialog=null;
            schedulePresentation();
        });
        dialog.show();
        CockpitTheme t=palette();
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
            Button b=dialog.getButton(which);
            if(b!=null){b.setAllCaps(false);b.setTextColor(which==AlertDialog.BUTTON_POSITIVE?t.accent:t.text);}
        }
    }

    /** Explicit settings entry; preferences are applied immediately. */
    public void showSettings(){
        if(closed||activity.isFinishing())return;
        if(downloading||verifiedApk!=null||permissionRelease!=null){toast("Ein Update wird bereits vorbereitet.");return;}
        CockpitTheme t=palette();LinearLayout b=body();
        b.addView(text("Installiert: v"+installedName,18,t.text,true));
        CheckBox auto=new CheckBox(activity);auto.setText("Beim App-Start automatisch nach Updates suchen");
        auto.setTextColor(t.text);auto.setButtonTintList(ColorStateList.valueOf(t.accent));
        auto.setChecked(automatic());
        auto.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean(AUTO,checked).apply());
        b.addView(auto);
        b.addView(text("Update-Kanal",14,t.muted,true));
        RadioGroup group=new RadioGroup(activity);group.setOrientation(RadioGroup.VERTICAL);
        RadioButton stable=new RadioButton(activity),both=new RadioButton(activity);
        stable.setId(View.generateViewId());both.setId(View.generateViewId());
        stable.setText("Nur stabile Releases");both.setText("Releases und Pre-Releases");
        for(RadioButton radio:new RadioButton[]{stable,both}){
            radio.setTextColor(t.text);radio.setButtonTintList(ColorStateList.valueOf(t.accent));group.addView(radio);
        }
        group.check(all()?both.getId():stable.getId());
        group.setOnCheckedChangeListener((g,id)->prefs.edit().putBoolean(ALL,id==both.getId()).apply());
        b.addView(group);
        b.addView(text("Pre-Releases sind Entwicklungsversionen und können Fehler enthalten. "+
            "Die manuelle Prüfung bietet auch übersprungene Versionen wieder an.",13,t.muted,false));
        b.addView(text("Bei der Prüfung wird ausschließlich das öffentliche Sofacockpit-Repository auf GitHub abgefragt. "+
            "Es werden keine TV-, Soundbar- oder Konfigurationsdaten übertragen.",12,t.muted,false));
        ScrollView scroll=new ScrollView(activity);scroll.addView(b);
        show(new AlertDialog.Builder(activity).setTitle("Software-Updates").setView(scroll)
            .setPositiveButton("Jetzt prüfen",(d,w)->check(true))
            .setNegativeButton("Schließen",null).create());
    }

    private void showOffer(ReleaseCatalog.Release r,boolean channelAll){
        CockpitTheme t=palette();LinearLayout b=body();
        b.addView(text(r.prerelease?"PRE-RELEASE · ENTWICKLUNGSVERSION":"STABILES RELEASE",
            14,r.prerelease?CockpitTheme.PENDING:t.accent,true));
        b.addView(text("v"+r.version,28,t.text,true));
        b.addView(text("Installiert: v"+installedName+"   ·   "+String.format(Locale.GERMANY,"%.1f MB",r.size/1_000_000.0),
            13,t.muted,false));
        b.addView(text(r.prerelease?
            "Entwicklungsversion – kann noch Fehler enthalten.":
            "Auf GitHub als reguläres Release veröffentlicht.",14,r.prerelease?CockpitTheme.PENDING:t.accent,false));
        b.addView(text("Neuerungen",15,t.text,true));
        // Plain text, never WebView/HTML, links or executable release content.
        TextView notes=text(r.notes.trim().isEmpty()?"Keine Änderungsbeschreibung vorhanden.":r.notes,13,t.text,false);
        ScrollView notesScroll=new ScrollView(activity);notesScroll.addView(notes);
        notesScroll.setFillViewport(false);
        int max=Math.min(dp(180),activity.getResources().getDisplayMetrics().heightPixels/4);
        notesScroll.setLayoutParams(new LinearLayout.LayoutParams(-1,max));b.addView(notesScroll);
        b.addView(text("Download erst nach deiner Zustimmung. Android prüft die APK und kann eine Installationsbestätigung bzw. einen Play-Protect-Scan verlangen.",
            12,t.muted,false));
        ScrollView scroll=new ScrollView(activity);scroll.addView(b);
        show(new AlertDialog.Builder(activity).setTitle("Update verfügbar").setView(scroll)
            .setPositiveButton("Aktualisieren",(d,w)->requestUpdate(r))
            .setNegativeButton("Später",null)
            .setNeutralButton("Überspringen",(d,w)->{
                String key=skipKey(channelAll);
                ReleaseCatalog.Version previous=ReleaseCatalog.Version.parse(prefs.getString(key,""));
                if(previous==null||r.version.compareTo(previous)>0)prefs.edit().putString(key,r.version.toString()).apply();
                toast("Version v"+r.version+" übersprungen.");
            }).create());
    }

    private boolean canInstall(){
        try{return activity.getPackageManager().canRequestPackageInstalls();}
        catch(RuntimeException ex){return false;}
    }
    private void requestUpdate(ReleaseCatalog.Release r){
        if(closed)return;
        if(canInstall()){approvedDownload=r;schedulePresentation();return;}
        // Do not open settings or download until the user explicitly accepts.
        main.post(()->{
            if(closed||activity.isFinishing())return;
            show(new AlertDialog.Builder(activity).setTitle("Installation erlauben")
                .setMessage("Android benötigt einmalig die Berechtigung „Unbekannte Apps installieren“ für Sofacockpit. "+
                    "Die APK wird erst danach heruntergeladen. Sicherheitsprüfung und Play Protect bleiben aktiv.")
                .setNegativeButton("Abbrechen",null)
                .setPositiveButton("Android-Einstellungen",(d,w)->{
                    permissionRelease=r;
                    try{
                        unknownSources.launch(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:"+activity.getPackageName())));
                    }catch(RuntimeException ex){
                        permissionRelease=null;toast("Installationsberechtigung konnte nicht geöffnet werden.");
                    }
                }).create());
        });
    }

    private void download(ReleaseCatalog.Release r){
        if(closed||downloading)return;
        downloading=true;
        final AtomicBoolean cancelled=new AtomicBoolean(false);cancelDownload=cancelled;
        CockpitTheme t=palette();LinearLayout b=body();
        progressText=text("Download v"+r.version+" … 0 %",15,t.text,true);b.addView(progressText);
        progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setProgressTintList(ColorStateList.valueOf(t.accent));
        b.addView(progress,new LinearLayout.LayoutParams(-1,dp(20)));
        b.addView(text("Anschließend werden Dateigröße, Prüfsumme (falls vorhanden), Paket, Version und Signatur geprüft.",
            12,t.muted,false));
        AlertDialog loading=new AlertDialog.Builder(activity).setTitle("Update wird geladen").setView(b)
            .setNegativeButton("Abbrechen",(d,w)->cancelled.set(true)).setCancelable(false).create();
        show(loading);
        worker.execute(()->{
            File part=null,apk=null;
            try{
                File dir=new File(activity.getCacheDir(),"updates");
                if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Update-Ordner nicht verfügbar.");
                File[] old=dir.listFiles();
                if(old!=null)for(File f:old)if(f.isFile()&&System.currentTimeMillis()-f.lastModified()>172800000L)f.delete();
                part=File.createTempFile("sofacockpit-", ".part",dir);
                GithubUpdates.download(r,part,()->closed||cancelled.get(),percent->main.post(()->{
                    if(!closed&&cancelDownload==cancelled&&progress!=null&&progressText!=null){
                        progress.setProgress(percent);progressText.setText(percent<100?
                            "Download v"+r.version+" … "+percent+" %":"APK wird geprüft …");
                    }
                }));
                if(closed||cancelled.get())throw new IOException("Abgebrochen");
                apk=new File(dir,part.getName().replace(".part",".apk"));
                if(!part.renameTo(apk))throw new IOException("APK konnte nicht gespeichert werden.");
                part=null;
                ApkVerification.verify(activity,apk,r);
                if(closed||cancelled.get())throw new IOException("Abgebrochen");
                final File ready=apk;apk=null;
                main.post(()->{
                    finishDownload(loading,cancelled);
                    if(closed||cancelled.get()){ready.delete();return;}
                    verifiedApk=ready;verifiedRelease=r;schedulePresentation();
                });
            }catch(Exception ex){
                String reason=ex instanceof IOException&&ex.getMessage()!=null?
                    ex.getMessage():"Download oder APK-Prüfung fehlgeschlagen.";
                main.post(()->{
                    finishDownload(loading,cancelled);
                    if(!closed)toast(cancelled.get()?"Update abgebrochen.":"Update nicht installiert: "+reason);
                });
            }finally{if(part!=null)part.delete();if(apk!=null)apk.delete();}
        });
    }
    private void finishDownload(AlertDialog loading,AtomicBoolean token){
        if(cancelDownload!=token)return;
        downloading=false;cancelDownload=null;progress=null;progressText=null;
        if(loading.isShowing())loading.dismiss();
    }
    private void install(File apk,ReleaseCatalog.Release release){
        if(closed){apk.delete();return;}
        if(!canInstall()){apk.delete();toast("Installationsberechtigung fehlt. Update erneut starten.");return;}
        try{
            // Read grant is restricted to this specific private cached APK.
            Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".updates",apk);
            Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(uri);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("Sofacockpit v"+release.version,uri));
            activity.startActivity(intent);
        }catch(android.content.ActivityNotFoundException notFound){
            try{
                Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".updates",apk);
                Intent view=new Intent(Intent.ACTION_VIEW);
                view.setDataAndType(uri,"application/vnd.android.package-archive");
                view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                view.setClipData(ClipData.newRawUri("Sofacockpit",uri));
                activity.startActivity(view);
            }catch(RuntimeException ex){apk.delete();toast("Android-Installer konnte nicht geöffnet werden.");}
        }catch(RuntimeException ex){apk.delete();toast("Android-Installer konnte nicht geöffnet werden.");}
        // Do not announce success: Android controls installation and may refuse/cancel/scan.
        // The installer can still read this APK after the Activity is gone.
    }

    private void toast(String value){if(!closed)Toast.makeText(activity,value,Toast.LENGTH_LONG).show();}
    @Override public void close(){
        closed=true;resumed=false;main.removeCallbacks(present);
        if(cancelDownload!=null)cancelDownload.set(true);
        if(activeDialog!=null){activeDialog.dismiss();activeDialog=null;}
        if(verifiedApk!=null){verifiedApk.delete();verifiedApk=null;}
        approvedDownload=null;permissionRelease=null;pendingCatalog=null;
        worker.shutdownNow();
    }
}
