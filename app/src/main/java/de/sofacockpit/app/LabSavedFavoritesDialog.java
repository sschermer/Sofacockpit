package de.sofacockpit.app;

import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import androidx.activity.ComponentActivity;
import de.sofacockpit.app.theme.ThemePack;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Management view of ALL saved streaming favorites, never derived from TV inventory. */
final class LabSavedFavoritesDialog implements AutoCloseable {
    private final ComponentActivity activity;
    private final StreamingShortcuts shortcuts;
    private final Supplier<TvAppInventory> inventory;
    private final Supplier<ThemePack> theme;
    private final Consumer<StreamingShortcuts.Entry> options;
    private Dialog active;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView summary;
    private LabVoiceUi ui;
    private boolean closed;
    LabSavedFavoritesDialog(ComponentActivity activity, StreamingShortcuts shortcuts,
            Supplier<TvAppInventory> inventory, Supplier<ThemePack> theme,
            Consumer<StreamingShortcuts.Entry> options) {
        this.activity=activity;this.shortcuts=shortcuts;this.inventory=inventory;
        this.theme=theme;this.options=options;
    }
    void show() {
        if (closed||activity.isFinishing()||activity.isDestroyed()) return;
        if (active!=null&&active.isShowing()) { refresh();return; }
        ui=new LabVoiceUi(activity,theme.get());
        LinearLayout root=ui.root();
        ui.header(root,"Gespeicherte Favoriten","Streaming & Apps",null,this::dismiss);
        summary=ui.text("",13,ui.t.muted,false);ui.add(root,summary,10);
        content=ui.column();scroll=ui.scroll(content);ui.fill(root,scroll);
        Dialog d=new Dialog(activity,android.R.style.Theme_Material_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCanceledOnTouchOutside(false);
        if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        d.setContentView(root);active=d;
        d.setOnDismissListener(v->{if(active==d){active=null;content=null;scroll=null;summary=null;ui=null;}});
        d.show();ui.configure(d,true,root);
        refresh();
    }
    void refresh(){
        if(closed||active==null||!active.isShowing()||content==null)return;
        int oldScroll=scroll.getScrollY();
        List<StreamingShortcuts.Entry> saved=shortcuts.savedFavorites();
        TvAppInventory tv=inventory.get();
        int available=0;
        for(StreamingShortcuts.Entry e:saved)
            if(tv.favoriteStatus(e)==TvAppInventory.FavoriteStatus.AVAILABLE)available++;
        summary.setText(saved.size()+" gespeichert · "+available+" aktuell in der Leiste\n"+
            "Auch nicht zuordenbare und derzeit fehlende Apps bleiben hier bearbeitbar.");
        content.removeAllViews();
        if(saved.isEmpty()){
            LinearLayout card=ui.panel();
            ui.add(card,ui.text("Keine gespeicherten Streaming-Favoriten.",15,ui.t.text,false),0);
            ui.add(content,card,8);
        }
        for(StreamingShortcuts.Entry entry:saved){
            TvAppInventory.FavoriteStatus state=tv.favoriteStatus(entry);
            LinearLayout card=ui.panel();
            ui.add(card,ui.text(entry.name,16,ui.t.text,true),0);
            ui.add(card,ui.text(state.label,13,state==TvAppInventory.FavoriteStatus.AVAILABLE?ui.t.accent:ui.t.muted,false),5);
            TextView link=ui.text(entry.link,12,ui.t.muted,false);
            link.setMaxLines(3);link.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ui.add(card,link,5);
            Button manage=ui.button("Bearbeiten / Verwalten  ›",false,()->options.accept(entry));
            manage.setContentDescription(entry.name+" verwalten");
            ui.add(card,manage,8);ui.add(content,card,8);
        }
        ScrollView current=scroll;current.post(()->current.scrollTo(0,oldScroll));
    }
    private void dismiss(){if(active!=null)active.dismiss();}
    @Override public void close(){closed=true;dismiss();}
}
