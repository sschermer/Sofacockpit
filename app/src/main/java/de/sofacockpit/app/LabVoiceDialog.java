package de.sofacockpit.app;

import android.Manifest;
import android.app.AlertDialog;
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
    private final ActivityResultLauncher<String> permission;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private AlertDialog dialog;
    private TextView status,preview;
    private EditText input;
    private Button microphone;
    private SpeechRecognizer recognizer;
    private boolean listening,closed,permissionPending;
    private int generation;
    private VoiceCommands.Command previewed;
    private final Runnable timeout=()->{
        if(listening){stop();status("Zeitlimit erreicht. Erneut versuchen oder Text eingeben.");}
    };

    LabVoiceDialog(ComponentActivity activity,Supplier<VoiceCommands.Library> library,
                   Function<VoiceCommands.Command,String> execute){
        this.activity=activity;this.library=library;this.execute=execute;
        permission=activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{
            permissionPending=false;
            if(!visible())return;
            status(Boolean.TRUE.equals(granted)?"Mikrofon freigegeben. Tippe auf Sprechen.":
                "Mikrofon nicht erlaubt. Textbefehle bleiben verfügbar.");
            microphone.setEnabled(available());
        });
    }
    private boolean visible(){return !closed && !activity.isFinishing() && !activity.isDestroyed() && dialog!=null && dialog.isShowing();}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private boolean available(){
        if(Build.VERSION.SDK_INT<31)return false;
        try{return SpeechRecognizer.isOnDeviceRecognitionAvailable(activity);}
        catch(RuntimeException ignored){return false;}
    }
    void show(){
        if(closed||visible())return;
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20),dp(8),dp(20),dp(8));
        TextView info=new TextView(activity);
        info.setText("Sprich einen kurzen Befehl oder gib ihn ein. Prüfe die Vorschau und bestätige mit Ausführen. Erkennung nur auf dem Smartphone.");
        body.addView(info);
        microphone=new Button(activity);microphone.setText("Sprechen");
        microphone.setMinimumHeight(dp(48));microphone.setAllCaps(false);
        microphone.setEnabled(available());microphone.setOnClickListener(v->{if(listening){stop();status("Aufnahme abgebrochen.");}else requestMicrophone();});
        body.addView(microphone,new LinearLayout.LayoutParams(-1,dp(52)));
        status=new TextView(activity);
        status.setText(available()?"Bereit – Mikrofon ist aus.":
            "Lokale Spracherkennung nicht verfügbar. Textbefehle sind möglich (ab Android 12 mit lokalem Sprachdienst auch Sprache).");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(status);
        input=new EditText(activity);input.setHint("z. B. Mach bitte leiser");
        input.setSingleLine(true);input.setSaveEnabled(false);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});
        body.addView(input,new LinearLayout.LayoutParams(-1,dp(56)));
        preview=new TextView(activity);preview.setTextSize(16);preview.setPadding(0,dp(10),0,dp(10));
        preview.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(preview);
        TextView examples=new TextView(activity);
        examples.setText("Beispiele\nLauter · Leiser · Stumm · Ton an\nStarte Netflix · HDMI zwei · PS5 spielen\nNight Time an · DRC aus\nKlangprofil Kino · Klangmodus Cinema\nFernseher aus · Soundbar an\nZurück · Home · OK · Wiedergabe umschalten\n\nApp-, HDMI-, Profil- und Klangmodusnamen werden aus der aktuellen Konfiguration übernommen. Bei TV-Befehlen gelten die vorhandenen Verbindungs- und Statusprüfungen.");
        body.addView(examples);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.addView(body);
        body.setFocusableInTouchMode(true);body.requestFocus();
        AlertDialog created=new AlertDialog.Builder(activity).setTitle("Stimme").setView(scroll)
            .setNegativeButton("Schließen",null).setPositiveButton("Ausführen",null).create();
        dialog=created;
        created.setOnDismissListener(d->{stop();if(dialog==created){dialog=null;input=null;status=null;preview=null;microphone=null;previewed=null;}});
        created.show();
        if(created.getWindow()!=null){
            created.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            created.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        created.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->confirm());
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){if(visible())renderPreview();}
            public void afterTextChanged(Editable s){}
        });
        renderPreview();
    }
    private void renderPreview(){
        if(!visible())return;
        VoiceCommands.Match match=library.get().resolve(input.getText().toString());
        previewed=match.command;
        preview.setText(match.command==null?match.message:"Befehl: "+match.command.label);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!listening && match.command!=null);
    }
    private void confirm(){
        if(!visible()||listening||previewed==null)return;
        // Re-resolve against the live inventory; do not execute stale or changed aliases.
        VoiceCommands.Match latest=library.get().resolve(input.getText().toString());
        if(!previewed.equals(latest.command)||!previewed.label.equals(latest.command.label)){
            renderPreview();status("Befehlszuordnung geändert. Bitte erneut prüfen.");return;
        }
        VoiceCommands.Command command=latest.command;
        dialog.dismiss();
        String message=execute.apply(command);
        if(message!=null&&!message.isEmpty())Toast.makeText(activity,message,Toast.LENGTH_LONG).show();
    }
    private void status(String message){if(visible())status.setText(message);}
    private void requestMicrophone(){
        if(!visible()||permissionPending)return;
        if(!available()){status("Lokaler Sprachdienst nicht verfügbar.");return;}
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            permissionPending=true;microphone.setEnabled(false);
            try{permission.launch(Manifest.permission.RECORD_AUDIO);}
            catch(RuntimeException e){permissionPending=false;microphone.setEnabled(true);status("Mikrofonberechtigung nicht verfügbar.");}
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
                    if(results==null||results.isEmpty()){status("Kein Befehl erkannt.");return;}
                    input.setText(results.get(0));input.setSelection(input.length());
                    status("Erkannt – bitte Vorschau prüfen.");
                }
                public void onPartialResults(Bundle b){}
                public void onEvent(int type,Bundle b){}
            });
            listening=true;
            input.setText("");microphone.setText("Abbrechen");input.setEnabled(false);
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
