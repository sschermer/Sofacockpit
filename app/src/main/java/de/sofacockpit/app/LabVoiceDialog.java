package de.sofacockpit.app;

import android.Manifest;
import android.app.Dialog;
import android.view.Window;
import de.sofacockpit.app.theme.ThemePack;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.function.Function;

/** Foreground, single-utterance, explicitly on-device recognition. No cloud fallback. */
final class LabVoiceDialog implements AutoCloseable {
    private final ComponentActivity activity;
    private final Supplier<VoiceCommands.Library> library;
    private final Function<VoiceCommands.Command,String> execute;
    private final Runnable openLibrary;
    private final Supplier<ThemePack> theme;
    private final ActivityResultLauncher<String> permission;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Dialog dialog,permissionOwner;
    private LabVoiceUi ui;
    private TextView status,preview;
    private EditText input;
    private Button microphone,executeButton;
    private SpeechRecognizer recognizer;
    private boolean listening,closed,permissionPending;
    private int generation;
    private VoiceCommands.Command previewed;
    private VoiceDispatch dispatch;
    private final Runnable timeout=()->{
        if(listening){stop();status("Zeitlimit erreicht. Erneut versuchen oder Text eingeben.");}
    };

    LabVoiceDialog(ComponentActivity activity,Supplier<VoiceCommands.Library> library,
                   Function<VoiceCommands.Command,String> execute,Runnable openLibrary,Supplier<ThemePack> theme){
        this.activity=activity;this.library=library;this.execute=execute;this.openLibrary=openLibrary;this.theme=theme;
        permission=activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{
            Dialog owner=permissionOwner;permissionOwner=null;permissionPending=false;
            if(!visible())return;
            microphone.setEnabled(available());
            if(owner!=dialog)return;
            if(!Boolean.TRUE.equals(granted)){status("Mikrofon nicht erlaubt. Textbefehle bleiben verfügbar.");return;}
            status("Mikrofon freigegeben. Tippe bei Bedarf auf Sprechen.");
            // Start only for this explicit request and this still-visible, focused foreground dialog.
            // If Android delivers the permission result before focus/resume, leave the microphone off.
            microphone.post(()->{
                if(visible()&&dialog==owner&&!listening&&
                    activity.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)&&
                    dialog.getWindow()!=null&&dialog.getWindow().getDecorView().hasWindowFocus())
                    requestMicrophone();
            });
        });
    }
    private boolean visible(){return !closed && !activity.isFinishing() && !activity.isDestroyed() && dialog!=null && dialog.isShowing();}
    private boolean foreground(){
        return visible() && activity.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
            dialog.getWindow()!=null && dialog.getWindow().getDecorView().hasWindowFocus();
    }
    private boolean available(){
        if(Build.VERSION.SDK_INT<31)return false;
        try{return SpeechRecognizer.isOnDeviceRecognitionAvailable(activity);}
        catch(RuntimeException ignored){return false;}
    }
    void show(){
        if(closed||visible())return;
        dispatch=new VoiceDispatch();
        ui=new LabVoiceUi(activity,theme.get());
        LinearLayout body=ui.root();
        ui.header(body,"Stimme","Eindeutige Sprachbefehle direkt ausführen",null,()->{if(dialog!=null)dialog.dismiss();});
        LinearLayout capture=ui.panel();
        microphone=ui.button("Sprechen",true,()->{
            if(listening){stop();status("Aufnahme abgebrochen.");}else requestMicrophone();
        });
        microphone.setEnabled(available()&&!permissionPending);
        ui.add(capture,microphone,0);
        status=ui.text(available()?"Bereit – Mikrofon ist aus.":
            "Lokale Spracherkennung nicht verfügbar. Textbefehle sind möglich.",14,ui.t.muted,false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.add(capture,status,10);ui.add(body,capture,12);

        LinearLayout textCard=ui.panel();
        ui.add(textCard,ui.text("Dein Befehl",14,ui.t.muted,true),0);
        input=ui.input("Befehl sprechen oder eingeben",240);
        ui.add(textCard,input,8);
        preview=ui.text("",16,ui.t.text,true);
        preview.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.add(textCard,preview,10);ui.add(body,textCard,10);

        LinearLayout actions=ui.row();
        ui.weighted(actions,ui.button("Schließen",false,()->{if(dialog!=null)dialog.dismiss();}),0);
        executeButton=ui.button("Ausführen",true,this::confirm);
        ui.weighted(actions,executeButton,8);ui.add(body,actions,12);
        ui.add(body,ui.button("Sprachbibliothek",false,()->{
            if(!visible())return;stop();dialog.dismiss();openLibrary.run();
        }),8);

        ScrollView scroll=ui.scroll(body);
        Dialog created=new Dialog(activity);created.requestWindowFeature(Window.FEATURE_NO_TITLE);
        created.setContentView(scroll);created.setCanceledOnTouchOutside(true);
        if(created.getWindow()!=null)created.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog=created;
        created.setOnDismissListener(v->{
            if(dialog!=created)return;
            stop();dialog=null;input=null;status=null;preview=null;microphone=null;executeButton=null;previewed=null;ui=null;dispatch=null;
        });
        created.show();ui.configure(created,false,body);body.requestFocus();
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){if(visible())renderPreview();}
            public void afterTextChanged(Editable s){}
        });
        renderPreview();
        // The short tap itself is the deliberate recording gesture. No second microphone tap.
        if(!permissionPending&&available())requestMicrophone();
    }
    private void renderPreview(){
        if(!visible())return;
        VoiceCommands.Match match=library.get().resolve(input.getText().toString());
        previewed=match.command;
        preview.setText(match.command==null?match.message:"Befehl: "+match.command.label);
        boolean canExecute=!listening&&match.command!=null&&dispatch!=null&&!dispatch.claimed();
        executeButton.setEnabled(canExecute);executeButton.setAlpha(canExecute?1f:.45f);
    }
    private void confirm(){
        if(!foreground()||listening||previewed==null||dispatch==null||dispatch.claimed())return;
        // Manual text submission and final local speech share the same live target checks.
        VoiceCommands.Match latest=library.get().resolve(input.getText().toString());
        VoiceCommands.Command command=dispatch.claim(previewed,latest);
        if(command==null){
            renderPreview();status("Befehlszuordnung geändert. Bitte erneut prüfen.");return;
        }
        // Claim before dismissing/dispatching, so reentrant or duplicate callbacks cannot repeat it.
        dialog.dismiss();
        String message=execute.apply(command);
        if(message!=null&&!message.isEmpty())Toast.makeText(activity,"Erkannt: "+command.label+"\n"+message,Toast.LENGTH_LONG).show();
    }
    private void status(String message){if(visible())status.setText(message);}
    private void requestMicrophone(){
        if(!visible()||permissionPending)return;
        if(!available()){status("Lokaler Sprachdienst nicht verfügbar.");return;}
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            permissionPending=true;permissionOwner=dialog;microphone.setEnabled(false);
            try{permission.launch(Manifest.permission.RECORD_AUDIO);}
            catch(RuntimeException e){permissionPending=false;permissionOwner=null;microphone.setEnabled(true);status("Mikrofonberechtigung nicht verfügbar.");}
            return;
        }
        start();
    }
    private void start(){
        if(!visible()||Build.VERSION.SDK_INT<31||!available())return;
        stop();
        int session=++generation;
        try{
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
            recognizer.setRecognitionListener(new RecognitionListener(){
                private boolean current(){return visible()&&listening&&session==generation;}
                public void onReadyForSpeech(Bundle b){if(current())status("Höre zu …");}
                public void onBeginningOfSpeech(){if(current())status("Höre zu …");}
                public void onRmsChanged(float rms){}
                public void onBufferReceived(byte[] data){}
                public void onEndOfSpeech(){if(current())status("Werte lokal aus …");}
                public void onError(int error){
                    if(!current())return;stop();
                    String msg;
                    switch(error){
                        case SpeechRecognizer.ERROR_NO_MATCH:
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:msg="Keine eindeutige Sprache erkannt.";break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:msg="Mikrofonberechtigung fehlt.";break;
                        case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
                        case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:msg="Lokales deutsches Sprachmodell fehlt. In den Android-Spracheinstellungen prüfen.";break;
                        default:msg="Lokale Spracherkennung fehlgeschlagen ("+error+").";
                    }
                    status(msg+" Erneut versuchen oder Text eingeben.");
                }
                public void onResults(Bundle b){
                    if(!current())return;
                    ArrayList<String> results=b==null?null:b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    stop();
                    if(!foreground()){status("Aufnahme beendet. Bei Bedarf erneut sprechen.");return;}
                    String transcript=results==null||results.isEmpty()?null:results.get(0);
                    if(!VoiceDispatch.acceptableTranscript(transcript)){
                        status("Kein gültiger vollständiger Befehl erkannt. Erneut sprechen oder Text eingeben.");return;
                    }
                    input.setText(transcript);input.setSelection(input.length());
                    // Only a completed, deliberately started recording can submit automatically.
                    // TextWatcher and partial recognition never send a device command.
                    if(previewed!=null)confirm();
                    else status("Keine eindeutige Zuordnung. Erneut sprechen oder Text eingeben.");
                }
                public void onPartialResults(Bundle b){}
                public void onEvent(int type,Bundle b){}
            });
            listening=true;
            input.setText("");microphone.setText("Aufnahme abbrechen");input.setEnabled(false);
            renderPreview();status("Mikrofon wird geöffnet …");
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"de-DE")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            handler.postDelayed(timeout,15000);
            recognizer.startListening(intent);
        }catch(RuntimeException e){stop();status("Lokaler Sprachdienst nicht verfügbar. Text eingeben.");}
    }
    private void stop(){
        generation++;listening=false;handler.removeCallbacks(timeout);
        SpeechRecognizer old=recognizer;recognizer=null;
        if(old!=null){
            try{old.cancel();}catch(RuntimeException ignored){}
            try{old.destroy();}catch(RuntimeException ignored){}
        }
        if(microphone!=null){microphone.setText("Sprechen");microphone.setEnabled(!permissionPending&&available());}
        if(input!=null)input.setEnabled(true);
        if(visible())renderPreview();
    }
    void pause(){if(listening){stop();status("Aufnahme beendet. Bei Bedarf erneut sprechen.");}}
    @Override public void close(){closed=true;stop();if(dialog!=null)dialog.dismiss();}
}
