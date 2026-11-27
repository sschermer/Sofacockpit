package de.sofacockpit.app;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import java.text.Collator;
import java.util.*;
import java.util.function.Supplier;

/** Editor for local phrases only. Never sends device commands. */
final class LabVoiceLibraryDialog implements AutoCloseable {
    private final ComponentActivity activity;
    private final Supplier<VoiceCommands.Library> base;
    private final VoiceAliasStore store;
    private AlertDialog active;
    private boolean closed;

    LabVoiceLibraryDialog(ComponentActivity activity,Supplier<VoiceCommands.Library> base,VoiceAliasStore store){
        this.activity=activity;this.base=base;this.store=store;
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private boolean available(){return !closed&&!activity.isFinishing()&&!activity.isDestroyed();}
    private void present(AlertDialog dialog){
        if(!available())return;
        AlertDialog previous=active;active=dialog;
        if(previous!=null)previous.dismiss();
        dialog.setOnDismissListener(d->{if(active==dialog)active=null;});
        dialog.show();
        if(dialog.getWindow()!=null){
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }
    private void toast(String message){Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
    private String error(RuntimeException ex){
        return ex.getMessage()==null?"Bibliothek konnte nicht gespeichert oder gelesen werden.":ex.getMessage();
    }
    private List<VoiceAliases.Entry> read(){
        try{return store.read();}
        catch(RuntimeException ex){
            new AlertDialog.Builder(activity).setTitle("Sprachbibliothek")
                .setMessage("Gespeicherte Synonyme konnten nicht gelesen werden. Daten bleiben unverändert.")
                .setPositiveButton("Schließen",null).show();
            return null;
        }
    }
    private List<VoiceAliases.Entry> forCommand(List<VoiceAliases.Entry> entries,VoiceCommands.Command c){
        List<VoiceAliases.Entry> result=new ArrayList<>();
        for(VoiceAliases.Entry e:entries)if(e.pointsTo(c))result.add(e);
        return result;
    }
    void show(){
        if(!available())return;
        List<VoiceAliases.Entry> entries=read();if(entries==null)return;
        VoiceCommands.Library library=base.get();
        List<VoiceCommands.Command> commands=library.commands();
        Collator collator=Collator.getInstance(Locale.GERMAN);
        commands.sort((a,b)->collator.compare(a.label,b.label));
        // Keep missing targets visible so their personal entries can be deleted.
        LinkedHashSet<VoiceCommands.Command> all=new LinkedHashSet<>(commands);
        for(VoiceAliases.Entry e:entries)if(library.find(e.kind,e.target)==null)
            all.add(new VoiceCommands.Command(e.kind,e.target,e.label));
        List<VoiceCommands.Command> targets=new ArrayList<>(all);
        String[] labels=new String[targets.size()];
        for(int i=0;i<targets.size();i++){
            VoiceCommands.Command c=targets.get(i);
            int count=forCommand(entries,c).size();
            labels[i]=c.label+" · "+count+" eigene"+(library.find(c.kind,c.target)==null?" · derzeit nicht verfügbar":"");
        }
        present(new AlertDialog.Builder(activity).setTitle("Sprachbibliothek")
            .setItems(labels,(d,i)->showCommand(targets.get(i)))
            .setNegativeButton("Schließen",null).create());
    }
    private LinearLayout body(){
        LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20),dp(12),dp(20),dp(12));return layout;
    }
    private TextView text(LinearLayout body,String value){
        TextView t=new TextView(activity);t.setText(value);t.setTextSize(15);
        t.setPadding(0,dp(6),0,dp(6));body.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;
    }
    private Button button(LinearLayout body,String label,Runnable click){
        Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setMinHeight(dp(48));
        b.setOnClickListener(v->click.run());body.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;
    }
    private void showCommand(VoiceCommands.Command selected){
        if(!available())return;
        List<VoiceAliases.Entry> entries=read();if(entries==null)return;
        VoiceCommands.Library library=base.get();
        VoiceCommands.Command live=library.find(selected.kind,selected.target);
        VoiceCommands.Command command=live==null?selected:live;
        LinearLayout content=body();
        text(content,live==null?"Ziel derzeit nicht verfügbar. Gespeicherte Synonyme werden nicht ausgeführt. Einträge können gelöscht werden.":
            "Eigene Formulierungen ergänzen die Standardbefehle. Kurze Namen erhalten passende Satzvarianten; vollständige Sätze bleiben exakt.");
        List<String> standard=library.standardPhrases(command);
        if(!standard.isEmpty()){
            final TextView[] standardText=new TextView[1];
            final Button[] toggle=new Button[1];
            toggle[0]=button(content,"Standardformulierungen anzeigen ("+standard.size()+")",()->{
                boolean show=standardText[0].getVisibility()!=View.VISIBLE;
                standardText[0].setVisibility(show?View.VISIBLE:View.GONE);
                toggle[0].setText("Standardformulierungen "+(show?"ausblenden":"anzeigen")+" ("+standard.size()+")");
            });
            standardText[0]=text(content,String.join("\n",standard));
            standardText[0].setTextIsSelectable(true);
            standardText[0].setVisibility(View.GONE);
        }
        text(content,"Eigene Synonyme");
        List<VoiceAliases.Entry> own=forCommand(entries,command);
        if(own.isEmpty())text(content,"Noch keine eigenen Formulierungen.");
        for(VoiceAliases.Entry e:own){
            button(content,e.text+(live==null?"  · Löschen":"  · Bearbeiten"),()->edit(command,e));
            if(live!=null){
                List<String> variants=VoicePhrases.variants(e.kind,e.target,e.text);
                if(variants.size()>1)text(content,"Auch erkannt: "+String.join(" · ",variants.subList(1,Math.min(4,variants.size())))+
                    (variants.size()>4?" … ("+variants.size()+" Formulierungen)":""));
            }
        }
        if(live!=null)button(content,"+ Synonym hinzufügen",()->edit(command,null));
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        present(new AlertDialog.Builder(activity).setTitle(command.label).setView(scroll)
            .setNeutralButton("Zur Bibliothek",(d,w)->show())
            .setNegativeButton("Schließen",null).create());
    }
    private void edit(VoiceCommands.Command command,VoiceAliases.Entry previous){
        if(!available())return;
        if(base.get().find(command.kind,command.target)==null){
            if(previous==null){toast("Befehlsziel derzeit nicht verfügbar.");showCommand(command);return;}
            confirmDelete(command,previous);return;
        }
        LinearLayout content=body();
        text(content,"Zuordnung: "+command.label);
        text(content,"Gib einen kurzen Zielnamen oder einen vollständigen eigenen Satz ein. Die Vorschau zeigt, welche Formulierungen daraus entstehen.");
        EditText input=new EditText(activity);input.setSingleLine(true);input.setSaveEnabled(false);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(VoiceAliases.MAX_TEXT)});
        input.setHint("z. B. Basketball");if(previous!=null)input.setText(previous.text);
        content.addView(input,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView preview=text(content,"");
        Runnable showPatterns=()->{
            String value=input.getText().toString();
            List<String> variants=VoicePhrases.variants(command.kind,command.target,value);
            if(variants.isEmpty()){preview.setText("Vorschau: noch keine Formulierung.");return;}
            try{
                VoiceAliases.change(base.get(),store.read(),previous,command,value);
                preview.setText("Erkannte Formulierungen ("+variants.size()+")\n"+String.join("\n",variants)+
                    "\n\nAusführung weiterhin erst nach Bestätigung im Sprachdialog.");
            }catch(RuntimeException ex){preview.setText("Noch nicht speicherbar: "+error(ex));}
        };
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){showPatterns.run();}
            public void afterTextChanged(Editable s){}
        });
        showPatterns.run();
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        AlertDialog.Builder b=new AlertDialog.Builder(activity)
            .setTitle(previous==null?"Synonym hinzufügen":"Synonym bearbeiten").setView(scroll)
            .setNegativeButton("Abbrechen",(d,w)->showCommand(command))
            .setPositiveButton("Speichern",null);
        if(previous!=null)b.setNeutralButton("Löschen",(d,w)->confirmDelete(command,previous));
        AlertDialog dialog=b.create();present(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                List<VoiceAliases.Entry> next=VoiceAliases.change(base.get(),store.read(),previous,command,input.getText().toString());
                store.write(next);showCommand(command);toast("Synonym gespeichert.");
            }catch(RuntimeException ex){input.setError(error(ex));}
        });
    }
    private void confirmDelete(VoiceCommands.Command command,VoiceAliases.Entry entry){
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Synonym löschen?")
            .setMessage("„"+entry.text+"“ wird entfernt. Der eigentliche Befehl und seine Standardformulierungen bleiben erhalten.")
            .setNegativeButton("Abbrechen",(d,w)->showCommand(command))
            .setPositiveButton("Löschen",(d,w)->{
                try{store.write(VoiceAliases.remove(store.read(),entry));toast("Synonym gelöscht.");}
                catch(RuntimeException ex){toast(error(ex));}
                showCommand(command);
            }).create();present(dialog);
    }
    @Override public void close(){closed=true;if(active!=null){active.dismiss();active=null;}}
}
