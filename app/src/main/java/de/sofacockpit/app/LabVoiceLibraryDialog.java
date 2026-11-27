package de.sofacockpit.app;

import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import de.sofacockpit.app.theme.ThemePack;
import java.util.*;
import java.util.function.Supplier;

/** Full-screen local library/editor. The index is display-only, never an execution path. */
final class LabVoiceLibraryDialog implements AutoCloseable {
    private final ComponentActivity activity;
    private final Supplier<VoiceCommands.Library> base;
    private final VoiceAliasStore store;
    private final Supplier<ThemePack> theme;
    private Dialog active;
    private Runnable back;
    private boolean closed,onlyOwn;
    private String query="";
    private int listScrollY;
    private final EnumSet<VoiceLibraryIndex.Group> expanded=EnumSet.noneOf(VoiceLibraryIndex.Group.class);

    LabVoiceLibraryDialog(ComponentActivity activity,Supplier<VoiceCommands.Library> base,
                          VoiceAliasStore store,Supplier<ThemePack> theme){
        this.activity=activity;this.base=base;this.store=store;this.theme=theme;
    }
    private boolean available(){return !closed&&!activity.isFinishing()&&!activity.isDestroyed();}
    private LabVoiceUi ui(){return new LabVoiceUi(activity,theme.get());}
    private void dismiss(){if(active!=null)active.dismiss();}
    private void hideKeyboard(){
        if(active!=null&&active.getWindow()!=null){
            View decor=active.getWindow().getDecorView();
            InputMethodManager ime=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if(ime!=null)ime.hideSoftInputFromWindow(decor.getWindowToken(),0);
        }
    }
    private void present(LabVoiceUi u,LinearLayout root,Runnable onBack){
        if(!available())return;
        hideKeyboard();back=onBack;
        boolean fresh=active==null;
        if(fresh){
            Dialog d=new Dialog(activity,android.R.style.Theme_Material_NoActionBar){
                @Override public void onBackPressed(){if(back!=null)back.run();else super.onBackPressed();}
            };
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            d.setCanceledOnTouchOutside(false);
            active=d;
            d.setOnDismissListener(v->{if(active==d){active=null;back=null;}});
        }
        Dialog d=active;d.setContentView(root);root.requestFocus();
        if(fresh)d.show();
        u.configure(d,true,root);
    }
    private void toast(String message){Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
    private String error(RuntimeException ex){return ex.getMessage()==null?"Bibliothek konnte nicht gespeichert oder gelesen werden.":ex.getMessage();}
    private List<VoiceAliases.Entry> read(){
        try{return store.read();}
        catch(RuntimeException ex){
            LabVoiceUi u=ui();LinearLayout root=u.root();
            u.header(root,"Sprachbibliothek",null,null,this::dismiss);
            LinearLayout card=u.panel();u.add(card,u.text("Gespeicherte Synonyme konnten nicht gelesen werden. Daten bleiben unverändert.",15,u.t.text,false),0);
            u.add(root,card,14);present(u,root,null);return null;
        }
    }
    void show(){
        if(!available())return;
        query="";onlyOwn=false;expanded.clear();listScrollY=0;
        showList();
    }
    private void showList(){
        if(!available())return;
        List<VoiceAliases.Entry> entries=read();if(entries==null)return;
        List<VoiceLibraryIndex.Item> items=VoiceLibraryIndex.build(base.get(),entries);
        LabVoiceUi u=ui();LinearLayout root=u.root();
        u.header(root,"Sprachbibliothek",items.size()+" Befehle · "+entries.size()+" eigene Synonyme",null,this::dismiss);
        EditText search=u.input("Befehl oder Synonym suchen",160);
        search.setText(query);u.add(root,search,12);
        LinearLayout filters=u.row();
        Button all=u.button("Alle",!onlyOwn,()->{});
        Button own=u.button("Nur eigene",onlyOwn,()->{});
        u.weighted(filters,all,0);u.weighted(filters,own,8);u.add(root,filters,8);
        LinearLayout content=u.column();ScrollView scroll=u.scroll(content);u.fill(root,scroll);
        TextView count=u.text("",12,u.t.muted,false);u.add(root,count,5);
        Runnable render=()->{
            content.removeAllViews();int found=0;
            boolean searchActive=!VoiceCommands.normalize(query).isEmpty();
            for(VoiceLibraryIndex.Group group:VoiceLibraryIndex.Group.values()){
                List<VoiceLibraryIndex.Item> matches=new ArrayList<>();
                for(VoiceLibraryIndex.Item item:items)if(item.group==group&&item.matches(query,onlyOwn))matches.add(item);
                if(matches.isEmpty())continue;found+=matches.size();
                boolean open=searchActive||onlyOwn||expanded.contains(group);
                LinearLayout panel=u.panel();
                Button groupTitle=u.button(group.label+"  ·  "+matches.size()+(open?"  ▾":"  ›"),false,()->{
                    if(searchActive||onlyOwn)return;
                    listScrollY=scroll.getScrollY();
                    if(!expanded.add(group))expanded.remove(group);
                    showList();
                });
                groupTitle.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.START);
                if(searchActive||onlyOwn){groupTitle.setClickable(false);groupTitle.setFocusable(false);}
                u.add(panel,groupTitle,0);
                if(open)for(VoiceLibraryIndex.Item item:matches){
                    StringBuilder label=new StringBuilder(item.command.label);
                    if(!item.own.isEmpty()){
                        label.append("\n");
                        for(int i=0;i<Math.min(2,item.own.size());i++){
                            if(i>0)label.append(" · ");label.append(item.own.get(i).text);
                        }
                        if(item.own.size()>2)label.append(" +").append(item.own.size()-2);
                    }else label.append("\nKeine eigenen Synonyme");
                    Button target=u.button(label+"   ›",false,()->{listScrollY=scroll.getScrollY();showCommand(item.command);});
                    target.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.START);
                    target.setContentDescription(item.command.label+", "+item.own.size()+" eigene Synonyme"+(item.live()?"":", derzeit nicht verfügbar"));
                    u.add(panel,target,6);
                }
                u.add(content,panel,8);
            }
            if(found==0)u.add(content,u.text("Keine passenden Befehle.",15,u.t.muted,false),14);
            count.setText(found+" von "+items.size()+" Befehlen");
            u.style(all,!onlyOwn);u.style(own,onlyOwn);all.setSelected(!onlyOwn);own.setSelected(onlyOwn);
        };
        all.setOnClickListener(v->{onlyOwn=false;listScrollY=0;render.run();scroll.scrollTo(0,0);});
        own.setOnClickListener(v->{onlyOwn=true;listScrollY=0;render.run();scroll.scrollTo(0,0);});
        watch(search,()->{query=search.getText().toString();listScrollY=0;render.run();scroll.scrollTo(0,0);});
        render.run();present(u,root,null);
        scroll.post(()->scroll.scrollTo(0,listScrollY));
    }
    private List<VoiceAliases.Entry> forCommand(List<VoiceAliases.Entry> entries,VoiceCommands.Command c){
        List<VoiceAliases.Entry> result=new ArrayList<>();
        for(VoiceAliases.Entry e:entries)if(e.pointsTo(c))result.add(e);
        return result;
    }
    private void showCommand(VoiceCommands.Command selected){
        if(!available())return;
        List<VoiceAliases.Entry> entries=read();if(entries==null)return;
        VoiceCommands.Library library=base.get();
        VoiceCommands.Command live=library.find(selected.kind,selected.target);
        VoiceCommands.Command command=live==null?selected:live;
        LabVoiceUi u=ui();LinearLayout root=u.root();
        u.header(root,command.label,VoiceLibraryIndex.group(command,live!=null).label,this::showList,this::dismiss);
        LinearLayout content=u.column();
        if(live==null){
            LinearLayout warning=u.panel();u.add(warning,u.text("Ziel derzeit nicht verfügbar. Eigene Synonyme bleiben gespeichert und können entfernt werden.",14,u.t.muted,false),0);
            u.add(content,warning,8);
        }
        List<VoiceAliases.Entry> own=forCommand(entries,command);
        LinearLayout personal=u.panel();
        u.add(personal,u.text("Eigene Synonyme ("+own.size()+")",16,u.t.text,true),0);
        if(own.isEmpty())u.add(personal,u.text("Noch keine eigenen Formulierungen.",13,u.t.muted,false),8);
        for(VoiceAliases.Entry e:own){
            List<String> variants=VoicePhrases.variants(e.kind,e.target,e.text);
            Button edit=u.button(e.text+"\n"+(live==null?"Entfernen":variants.size()+" Formulierungen · Bearbeiten")+"   ›",false,()->edit(command,e));
            edit.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.START);
            u.add(personal,edit,8);
        }
        if(live!=null)u.add(personal,u.button("+ Synonym hinzufügen",true,()->edit(command,null)),12);
        u.add(content,personal,8);
        List<String> standard=library.standardPhrases(command);
        if(!standard.isEmpty()){
            LinearLayout card=u.panel();
            TextView examples=u.text(String.join("\n",standard),14,u.t.text,false);
            examples.setLineSpacing(u.dp(4),1f);examples.setTextIsSelectable(true);examples.setVisibility(View.GONE);
            Button toggle=u.button("Standardformulierungen ("+standard.size()+")  ›",false,()->{});
            toggle.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.START);
            toggle.setOnClickListener(v->{
                boolean open=examples.getVisibility()!=View.VISIBLE;
                examples.setVisibility(open?View.VISIBLE:View.GONE);
                toggle.setText("Standardformulierungen ("+standard.size()+")  "+(open?"▾":"›"));
            });
            u.add(card,toggle,0);u.add(card,examples,8);u.add(content,card,10);
        }
        u.fill(root,u.scroll(content));present(u,root,this::showList);
    }
    private void edit(VoiceCommands.Command command,VoiceAliases.Entry previous){
        if(!available())return;
        if(base.get().find(command.kind,command.target)==null){
            if(previous!=null)confirmDelete(command,previous);
            else{toast("Befehlsziel derzeit nicht verfügbar.");showCommand(command);}
            return;
        }
        LabVoiceUi u=ui();LinearLayout root=u.root();Runnable goBack=()->showCommand(command);
        u.header(root,previous==null?"Synonym hinzufügen":"Synonym bearbeiten",command.label,goBack,this::dismiss);
        LinearLayout content=u.column();LinearLayout card=u.panel();
        u.add(card,u.text("Eigene Formulierung",16,u.t.text,true),0);
        u.add(card,u.text("Kurze Namen erzeugen passende Satzvarianten. Vollständige Sätze bleiben exakt.",13,u.t.muted,false),5);
        EditText input=u.input("z. B. Basketball",VoiceAliases.MAX_TEXT);
        if(previous!=null)input.setText(previous.text);
        u.add(card,input,10);u.add(content,card,8);
        LinearLayout patterns=u.panel();u.add(patterns,u.text("Erkannte Formulierungen",16,u.t.text,true),0);
        TextView preview=u.text("",14,u.t.muted,false);preview.setLineSpacing(u.dp(4),1f);
        preview.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        u.add(patterns,preview,8);u.add(content,patterns,10);
        u.fill(root,u.scroll(content));
        LinearLayout actions=u.row();
        Button save=u.button("Speichern",true,()->{
            try{
                store.write(VoiceAliases.change(base.get(),store.read(),previous,command,input.getText().toString()));
                showCommand(command);toast("Synonym gespeichert.");
            }catch(RuntimeException ex){input.setError(error(ex));}
        });
        u.weighted(actions,u.button("Abbrechen",false,goBack),0);u.weighted(actions,save,8);u.add(root,actions,10);
        if(previous!=null)u.add(root,u.button("Synonym löschen",false,()->confirmDelete(command,previous)),8);
        Runnable render=()->{
            String value=input.getText().toString();
            List<String> variants=VoicePhrases.variants(command.kind,command.target,value);
            boolean valid=false;String message;
            if(variants.isEmpty())message="Gib eine Formulierung ein.";
            else try{
                VoiceAliases.change(base.get(),store.read(),previous,command,value);
                valid=true;message=variants.size()+" Formulierungen\n\n"+String.join("\n",variants);
            }catch(RuntimeException ex){message="Nicht speicherbar: "+error(ex);}
            preview.setText(message);preview.setTextColor(valid?u.t.text:u.t.muted);
            save.setEnabled(valid);save.setAlpha(valid?1f:.45f);
        };
        watch(input,render);render.run();present(u,root,goBack);
    }
    private void confirmDelete(VoiceCommands.Command command,VoiceAliases.Entry entry){
        LabVoiceUi u=ui();LinearLayout root=u.root();Runnable back=()->showCommand(command);
        u.header(root,"Synonym löschen?",command.label,back,this::dismiss);
        LinearLayout card=u.panel();
        u.add(card,u.text("„"+entry.text+"“ wird entfernt. Der Befehl und die Standardformulierungen bleiben erhalten.",15,u.t.text,false),0);
        LinearLayout actions=u.row();
        u.weighted(actions,u.button("Abbrechen",false,back),0);
        u.weighted(actions,u.button("Löschen",true,()->{
            try{store.write(VoiceAliases.remove(store.read(),entry));toast("Synonym gelöscht.");}
            catch(RuntimeException ex){toast(error(ex));}
            showCommand(command);
        }),8);
        u.add(card,actions,12);u.add(root,card,12);present(u,root,back);
    }
    private static void watch(EditText input,Runnable run){
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){run.run();}
            public void afterTextChanged(Editable s){}
        });
    }
    @Override public void close(){closed=true;dismiss();}
}
