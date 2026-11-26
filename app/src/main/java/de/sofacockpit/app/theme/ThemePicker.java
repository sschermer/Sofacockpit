package de.sofacockpit.app.theme;

import android.app.AlertDialog;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-initiated file import/export and a two-column gallery. Never controls devices. */
public final class ThemePicker implements AutoCloseable {
    private final ComponentActivity activity;
    private final ThemeStore store;
    private final Runnable changed;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String[]> open;
    private final ActivityResultLauncher<String> save;
    private ThemePack exporting;
    private AlertDialog dialog;
    private boolean busy;
    private volatile boolean closed;

    public ThemePicker(ComponentActivity activity,ThemeStore store,Runnable changed){
        this.activity=activity;this.store=store;this.changed=changed;
        open=activity.registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{
            if(uri!=null&&!closed)importFile(uri);
        });
        save=activity.registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"),uri->{
            ThemePack pack=exporting;exporting=null;
            if(uri!=null&&!closed&&pack!=null)exportFile(uri,pack);
        });
    }
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}}
    private TextView text(String value,int size,int color,ThemePack pack){
        TextView t=new TextView(activity);t.setText(value);t.setTextColor(color);
        t.setTextSize(size);t.setTypeface(ThemeArt.typeface(pack.theme,Typeface.NORMAL));return t;
    }
    private TextView action(String name,ThemePack pack,Runnable action){
        TextView button=text(name,14,pack.theme.text,pack);button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));button.setPadding(dp(8),dp(6),dp(8),dp(6));
        button.setBackground(ThemeArt.surface(pack,pack.theme.control,pack.theme.cornerDp,
            activity.getResources().getDisplayMetrics().density,false,true));
        button.setOnClickListener(v->action.run());button.setFocusable(true);return button;
    }
    public void show(){
        if(closed||busy||activity.isFinishing())return;
        dismiss();
        ThemePack current=store.selected();
        CockpitTheme t=current.theme;
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14),dp(12),dp(14),dp(12));body.setBackgroundColor(t.background);
        TextView title=text("Design auswählen",22,t.text,current);title.setTypeface(ThemeArt.typeface(t,Typeface.BOLD));
        body.addView(title);
        TextView hint=text("Antippen: übernehmen · lange drücken: importiertes Theme entfernen",12,t.muted,current);
        hint.setPadding(0,dp(5),0,dp(12));body.addView(hint);
        List<ThemePack> list=store.all();
        for(boolean builtin:new boolean[]{true,false}){
            TextView caption=text(builtin?"VORINSTALLIERT":"IMPORTIERT",12,t.muted,current);
            caption.setPadding(0,dp(10),0,dp(6));body.addView(caption);
            LinearLayout row=null;int index=0;
            for(ThemePack pack:list){
                if(pack.builtin!=builtin)continue;
                if(index%2==0){row=new LinearLayout(activity);body.addView(row,new LinearLayout.LayoutParams(-1,-2));}
                LinearLayout tile=new LinearLayout(activity);tile.setOrientation(LinearLayout.VERTICAL);
                boolean selected=pack.theme.id.equals(current.theme.id);
                tile.setPadding(dp(7),dp(7),dp(7),dp(7));
                tile.setBackground(ThemeArt.surface(current,selected?t.selected:t.panel,t.cornerDp,
                    activity.getResources().getDisplayMetrics().density,selected,true));
                tile.addView(new Preview(pack),new LinearLayout.LayoutParams(-1,dp(144)));
                TextView label=text((selected?"✓  ":"")+pack.theme.name,13,selected?t.accent:t.text,current);
                label.setGravity(Gravity.CENTER);label.setMinHeight(dp(42));label.setMaxLines(2);
                tile.addView(label,new LinearLayout.LayoutParams(-1,-2));
                tile.setContentDescription(pack.theme.name+(selected?", ausgewählt":"")+
                    (!builtin?", lange drücken zum Entfernen":""));
                tile.setFocusable(true);
                tile.setOnClickListener(v->{store.select(pack.theme.id);dismiss();changed.run();});
                if(!builtin)tile.setOnLongClickListener(v->{confirmRemove(pack);return true;});
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
                lp.setMargins(0,0,dp(6),dp(6));row.addView(tile,lp);index++;
            }
            if(index%2==1&&row!=null)row.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));
            if(index==0&&!builtin)body.addView(text("Noch keine eigenen Themes.",13,t.muted,current));
        }
        TextView imp=action("＋  Theme importieren",current,()->{
            dismiss();open.launch(new String[]{"*/*"});
        });
        body.addView(imp,new LinearLayout.LayoutParams(-1,-2));
        TextView exp=action("Aktuelles Theme exportieren",current,()->{
            if(current.bytes==null){toast("Standarddatei fehlt.");return;}
            exporting=current;dismiss();save.launch(current.theme.id+".sofatheme");
        });
        LinearLayout.LayoutParams expLp=new LinearLayout.LayoutParams(-1,-2);expLp.topMargin=dp(8);
        body.addView(exp,expLp);
        if(!current.builtin){
            TextView del=action("Aktuelles Theme entfernen",current,()->confirmRemove(current));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);body.addView(del,lp);
        }
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.addView(body);
        dialog=new AlertDialog.Builder(activity).setView(scroll).setNegativeButton("Schließen",null).create();
        dialog.show();
    }
    private void confirmRemove(ThemePack pack){
        new AlertDialog.Builder(activity).setTitle("Theme entfernen?")
            .setMessage(pack.theme.name+" wird aus der App entfernt. Falls aktiv, wird Minimal Premium verwendet.")
            .setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen",(d,w)->{
                try{store.remove(pack.theme.id);dismiss();changed.run();show();}
                catch(IOException e){toast(e.getMessage());}
            }).show();
    }
    private void importFile(Uri uri){
        if(busy)return;busy=true;
        worker.execute(()->{
            try(InputStream in=activity.getContentResolver().openInputStream(uri)){
                ThemePack pack=new ThemePack(ThemeArchive.read(in),false);
                if(closed)return;
                store.add(pack);
                activity.runOnUiThread(()->{
                    busy=false;if(!closed&&!activity.isFinishing()){toast("Theme importiert: "+pack.theme.name);show();}
                });
            }catch(Exception e){
                finish("Theme nicht importiert: "+(e instanceof IOException||e instanceof IllegalArgumentException?
                    e.getMessage():"Ungültiges Dateiformat"));
            }
        });
    }
    private void exportFile(Uri uri,ThemePack pack){
        if(busy)return;busy=true;
        worker.execute(()->{
            try(OutputStream out=activity.getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new IOException("Datei nicht beschreibbar");
                out.write(pack.bytes);out.flush();finish("Theme exportiert: "+pack.theme.name);
            }catch(Exception e){finish("Theme-Export fehlgeschlagen.");}
        });
    }
    private void finish(String message){activity.runOnUiThread(()->{
        busy=false;if(!closed&&!activity.isFinishing())toast(message);
    });}
    private void toast(String message){Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
    @Override public void close(){closed=true;dismiss();worker.shutdown();}

    private final class Preview extends View {
        private final ThemePack pack;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        Preview(ThemePack pack){super(activity);this.pack=pack;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            CockpitTheme t=pack.theme;
            c.drawColor(t.background);
            if(pack.preview!=null){
                Bitmap b=pack.preview;
                float scale=Math.min((float)getWidth()/b.getWidth(),(float)getHeight()/b.getHeight());
                float w=b.getWidth()*scale,h=b.getHeight()*scale;
                c.drawBitmap(b,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),p);
                return;
            }
            float w=getWidth(),h=getHeight(),pad=w*.055f,tab=(w-4*pad)/3;
            p.setTypeface(ThemeArt.typeface(t,Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Math.min(w*.075f,h*.075f));
            String[] names={"Start","TV","Sound"};
            for(int i=0;i<3;i++){
                float x=pad+i*(tab+pad);
                ThemeArt.drawPanel(c,new RectF(x,pad,x+tab,pad+h*.14f),pack,i==1?t.selected:t.control,
                    t.cornerDp*w/360f,i==1);
                p.setColor(i==1?t.accent:t.muted);c.drawText(names[i],x+tab/2,pad+h*.092f,p);
            }
            float side=Math.min(w-2*pad,h*.64f);
            ThemeArt.drawDial(c,(w-side)/2,h*.22f,side,pack,-1,true);
            p.setColor(t.control);c.drawRoundRect(pad,h*.91f,w-pad,h*.95f,3,3,p);
            p.setColor(t.accent);c.drawRoundRect(pad,h*.91f,w*.62f,h*.95f,3,3,p);
        }
    }
}
