package de.sofacockpit.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Isolated local experiment: no TV, soundbar, network or production feedback references. */
final class HapticLabDialog implements AutoCloseable {
    private final Activity activity;
    private final int bg,panel,text,muted,accent;
    private final Vibrator motor;
    private final boolean amplitude,hasMotor,envelope;
    private final int motorCount;
    private Dialog dialog;
    private Pad pad;
    private TextView status,levelLabel,description;
    private SeekBar level;
    private int strength=90,sharpness=85;
    private HapticLabPatterns.Surface surface=HapticLabPatterns.Surface.MAGNET;
    private long lastStart=-1000;
    private long busyUntil=0;
    // One bounded vibration for a stationary finger; no periodic restart.
    private final MagnetOutput magnetOutput=new MagnetOutput();

    HapticLabDialog(Activity activity,int bg,int panel,int text,int muted,int accent){
        this.activity=activity;this.bg=bg;this.panel=panel;this.text=text;this.muted=muted;this.accent=accent;
        Vibrator v=null;int count=0;
        try{
            if(Build.VERSION.SDK_INT>=31){
                VibratorManager manager=activity.getSystemService(VibratorManager.class);
                if(manager!=null){v=manager.getDefaultVibrator();count=manager.getVibratorIds().length;}
            }else v=activity.getSystemService(Vibrator.class);
        }catch(RuntimeException ignored){}
        motor=v;hasMotor=v!=null&&v.hasVibrator();
        motorCount=Build.VERSION.SDK_INT>=31?count:hasMotor?1:0;
        amplitude=hasMotor&&v.hasAmplitudeControl();
        envelope=hasMotor&&Build.VERSION.SDK_INT>=36&&Envelope.supported(v);
    }

    void show(){
        if(activity.isFinishing()||activity.isDestroyed()||(dialog!=null&&dialog.isShowing()))return;
        Dialog d=new Dialog(activity,android.R.style.Theme_Material_NoActionBar){
            @Override public void onWindowFocusChanged(boolean focused){
                super.onWindowFocusChanged(focused);
                if(!focused)stop();
            }
        };
        dialog=d;
        LinearLayout outer=new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);outer.setBackgroundColor(bg);
        outer.setPadding(dp(12),dp(12),dp(12),dp(8));
        LinearLayout header=row();
        TextView title=label("Haptik-Labor",22,text);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button close=button("Schließen",this::close);
        header.addView(close,new LinearLayout.LayoutParams(-2,dp(48)));
        outer.addView(header);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(label("Nur Testfläche. Keine Befehle an TV oder Soundbar.",13,muted));
        body.addView(label("Motoren laut Android: "+(Build.VERSION.SDK_INT>=31?String.valueOf(motorCount):"nicht einzeln abfragbar")
            +" · Amplitudenregelung: "+(amplitude?"ja":"nein")
            +" · Hüllkurven/Schärfe: "+(envelope?"ja":"nein"),12,muted));
        status=label("Bereit",12,accent);body.addView(status);
        levelLabel=label("Stärke: "+strength+" %",16,text);body.addView(levelLabel);
        level=new SeekBar(activity);
        level.setMax(100);level.setProgress(strength);
        level.setContentDescription("Vibrationsstärke von 0 bis 100 Prozent");
        level.setProgressTintList(ColorStateList.valueOf(accent));level.setThumbTintList(ColorStateList.valueOf(accent));
        body.addView(level,new LinearLayout.LayoutParams(-1,dp(40)));
        level.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onStartTrackingTouch(SeekBar bar){stop();}
            @Override public void onStopTrackingTouch(SeekBar bar){play(HapticLabPatterns.Cue.LEVEL,100,false);}
            @Override public void onProgressChanged(SeekBar bar,int value,boolean user){
                strength=value;levelLabel.setText("Stärke: "+strength+" %");
                if(strength==0)stop();
            }
        });
        LinearLayout presets=row();
        for(int percent:new int[]{25,60,80,90,100}){
            presets.addView(button(percent+" %",()->{
                stop();level.setProgress(percent);play(HapticLabPatterns.Cue.LEVEL,100,false);
            }),new LinearLayout.LayoutParams(0,dp(48),1));
        }
        body.addView(presets);
        heading(body,"Oberflächen");
        LinearLayout modes=row();
        for(HapticLabPatterns.Surface option:HapticLabPatterns.Surface.values()){
            if(option==HapticLabPatterns.Surface.MAGNET)continue;
            Button b=button(option.label,()->chooseSurface(option));
            modes.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        body.addView(modes);
        body.addView(button("Magnetismus · Annäherung und Einrasten",()->chooseSurface(HapticLabPatterns.Surface.MAGNET)),
            new LinearLayout.LayoutParams(-1,dp(48)));
        description=label(surfaceDescription(),13,muted);body.addView(description);
        pad=new Pad(activity);
        body.addView(pad,new LinearLayout.LayoutParams(-1,dp(224)));
        heading(body,"Einzelimpulse");
        HapticLabPatterns.Cue[] patterns={
            HapticLabPatterns.Cue.TICK,HapticLabPatterns.Cue.CLICK,
            HapticLabPatterns.Cue.DOUBLE,HapticLabPatterns.Cue.THUD,
            HapticLabPatterns.Cue.RISE,HapticLabPatterns.Cue.FALL,
            HapticLabPatterns.Cue.WAVE,HapticLabPatterns.Cue.RATCHET,
            HapticLabPatterns.Cue.CENTER,HapticLabPatterns.Cue.LEVEL};
        for(int i=0;i<patterns.length;i+=2){
            LinearLayout r=row();
            for(int j=i;j<Math.min(i+2,patterns.length);j++){
                HapticLabPatterns.Cue cue=patterns[j];
                r.addView(button(cue.label,()->play(cue,100,false)),new LinearLayout.LayoutParams(0,dp(48),1));
            }
            body.addView(r);
        }
        if(Build.VERSION.SDK_INT>=30){
            heading(body,"Native Motorimpulse");
            addPrimitives(body);
        }
        heading(body,"Hüllkurven: weich bis scharf");
        if(envelope){
            TextView sharpLabel=label("Schärfe: "+sharpness+" %",14,text);body.addView(sharpLabel);
            SeekBar sharp=new SeekBar(activity);sharp.setMax(100);sharp.setProgress(sharpness);
            sharp.setContentDescription("Haptische Schärfe von 0 bis 100 Prozent");
            sharp.setProgressTintList(ColorStateList.valueOf(accent));sharp.setThumbTintList(ColorStateList.valueOf(accent));
            body.addView(sharp,new LinearLayout.LayoutParams(-1,dp(40)));
            sharp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onStartTrackingTouch(SeekBar bar){stop();}
                @Override public void onStopTrackingTouch(SeekBar bar){playEnvelope(sharpness,sharpness);}
                @Override public void onProgressChanged(SeekBar bar,int value,boolean user){
                    sharpness=value;sharpLabel.setText("Schärfe: "+sharpness+" %");
                }
            });
            LinearLayout r=row();
            r.addView(button("Weich",()->playEnvelope(0,0)),new LinearLayout.LayoutParams(0,dp(48),1));
            r.addView(button("Scharf",()->playEnvelope(100,100)),new LinearLayout.LayoutParams(0,dp(48),1));
            r.addView(button("Verlauf",()->playEnvelope(0,100)),new LinearLayout.LayoutParams(0,dp(48),1));
            body.addView(r);
        }else body.addView(label("Android meldet keine Hüllkurvenunterstützung. Die Amplitudenmuster oben bleiben verfügbar.",13,muted));
        body.addView(label("100 % = maximaler angeforderter API-Wert; Android-Einstellungen und Motor begrenzen die reale Stärke. Bei ausgeschalteter Systemhaptik bleibt der Test stumm. Relief, Texturen und Rastleiste geben Bewegungsimpulse ab; Magnetismus: eine gleichbleibende Vibration bei ruhendem Finger; Änderung nur bei merklichem Abstandswandel. Alle Testflächen enden nach 5 Sekunden pro Berührung; danach erneut ansetzen.",12,muted));
        Button stop=button("Vibration stoppen",this::stop);
        outer.addView(stop,new LinearLayout.LayoutParams(-1,dp(48)));
        d.setContentView(outer);
        d.setOnDismissListener(ignored->{
            stop();
            if(dialog==d){dialog=null;pad=null;status=null;}
        });
        d.show();
        if(d.getWindow()!=null)d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        report(hasMotor?"Bereit – Testfläche oder Impuls antippen":"Kein Vibrationsmotor gemeldet");
    }

    private String surfaceDescription(){
        return surface.description+(surface==HapticLabPatterns.Surface.MAGNET&&!amplitude?
            "\nOhne Amplitudenregelung: gleichmäßige Vibration mit System-Standardstärke; Abstandsregelung nicht verfügbar.":"");
    }
    private void chooseSurface(HapticLabPatterns.Surface option){
        stop();surface=option;description.setText(surfaceDescription());pad.invalidate();
        report("Testfläche: "+option.label);
    }
    private void heading(LinearLayout body,String text){
        TextView title=label(text,16,this.text);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setPadding(dp(4),dp(12),0,dp(3));body.addView(title);
    }
    private LinearLayout row(){
        LinearLayout r=new LinearLayout(activity);r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBaselineAligned(false);return r;
    }
    private TextView label(String value,int sp,int color){
        TextView v=new TextView(activity);v.setText(value);v.setTextSize(sp);v.setTextColor(color);
        v.setPadding(dp(4),dp(4),dp(4),dp(4));return v;
    }
    private Button button(String value,Runnable action){
        Button b=new Button(activity);b.setText(value);b.setAllCaps(false);b.setTextSize(12);
        b.setTextColor(text);b.setGravity(Gravity.CENTER);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(3),0,dp(3),0);
        GradientDrawable shape=new GradientDrawable();shape.setColor(panel);shape.setCornerRadius(dp(8));
        shape.setStroke(dp(1),bg);b.setBackground(shape);
        b.setHapticFeedbackEnabled(false);
        b.setOnClickListener(v->action.run());return b;
    }
    private void report(String message){if(status!=null)status.setText(message);}
    private boolean available(){
        if(dialog==null||!dialog.isShowing()||dialog.getWindow()==null||
            !dialog.getWindow().getDecorView().hasWindowFocus())return false;
        if(!hasMotor){report("Kein Motor verfügbar");return false;}
        if(Settings.System.getInt(activity.getContentResolver(),Settings.System.HAPTIC_FEEDBACK_ENABLED,1)==0){
            stop();report("Systemhaptik ausgeschaltet");return false;
        }
        if(strength==0){stop();report("Stärke 0 % – stumm");return false;}
        return true;
    }
    private boolean emit(VibrationEffect effect,long duration,String label,boolean motion){
        if(!available())return false;
        long now=android.os.SystemClock.uptimeMillis();
        if(now-lastStart<45||(motion&&now<busyUntil))return false;
        try{
            motor.cancel();
            vibrate(effect);
            lastStart=now;busyUntil=now+duration;
            report(label+" · angefordert "+strength+" %");
            return true;
        }catch(RuntimeException e){report("Effekt konnte nicht gestartet werden");return false;}
    }
    private void vibrate(VibrationEffect effect){
        if(Build.VERSION.SDK_INT>=33){
            motor.vibrate(effect,new android.os.VibrationAttributes.Builder()
                .setUsage(android.os.VibrationAttributes.USAGE_TOUCH).build());
        }else{
            motor.vibrate(effect,new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build());
        }
    }
    /** No timer-driven restart while the requested level is steady. The tick
     * only checks touch safety and delivers a pending change after rate limiting. */
    private void playMagnet(MagnetField.Reading reading,long now,long remaining){
        if(remaining<=0||!available()){stop();return;}
        try{
            MagnetOutput.Plan plan=magnetOutput.update(reading,strength,amplitude,now,remaining);
            switch(plan.kind){
                case NONE:return;
                case STOP:
                    motor.cancel();report("Außerhalb des Annäherungsrings");return;
                case SNAP:
                    motor.cancel();
                    vibrate(VibrationEffect.createOneShot(plan.duration,plan.amplitude));
                    report("Eingerastet · "+strength+" % · danach Ruhe");return;
                case HUM:
                    // Avoid explicit cancel between nonzero amplitude levels.
                    // One finite effect covers the remainder of the touch.
                    VibrationEffect effect=plan.hasRamp()?
                        VibrationEffect.createWaveform(plan.times,plan.amplitudes,-1):
                        VibrationEffect.createOneShot(plan.duration,plan.amplitude);
                    vibrate(effect);
                    report(amplitude?"Annäherung · "+Math.round(plan.amplitude*100f/255f)+" % · Dauervibration":
                        "Annäherung · konstante Systemstärke");
                    return;
            }
        }catch(RuntimeException e){stop();report("Magneteffekt konnte nicht gestartet werden");}
    }
    private void play(HapticLabPatterns.Cue cue,int scale,boolean motion){
        if(!available())return;
        int value=Math.round(strength*Math.max(0,Math.min(100,scale))/100f);
        int[] amplitudes=cue.scaled(value,amplitude);
        boolean on=false;for(int a:amplitudes)if(a!=0){on=true;break;}
        if(!on)return;
        emit(VibrationEffect.createWaveform(cue.times,amplitudes,-1),cue.duration(),
            cue.label+" ("+value+" %)",motion);
    }
    private void addPrimitives(LinearLayout body){
        int[] ids={VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            VibrationEffect.Composition.PRIMITIVE_THUD,VibrationEffect.Composition.PRIMITIVE_SPIN,
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL};
        String[] names={"Klick","Tick","Tiefer Tick","Dumpfer Stoß","Spin","Schneller Anstieg","Langsamer Anstieg","Schneller Abfall"};
        boolean[] supported=hasMotor?motor.arePrimitivesSupported(ids):new boolean[ids.length];
        LinearLayout r=null;int shown=0;
        for(int i=0;i<ids.length;i++){
            if(!supported[i])continue;
            final int id=ids[i];final String name=names[i];
            if(shown%2==0){r=row();body.addView(r);}
            r.addView(button(name,()->{
                if(!available())return;
                try{
                    VibrationEffect effect=VibrationEffect.startComposition()
                        .addPrimitive(id,strength/100f).compose();
                    long duration=Build.VERSION.SDK_INT>=31?motor.getPrimitiveDurations(id)[0]:180;
                    emit(effect,Math.max(40,duration),name+" (nativ)",false);
                }catch(RuntimeException e){report("Nativer Effekt nicht verfügbar");}
            }),new LinearLayout.LayoutParams(0,dp(48),1));
            shown++;
        }
        if(shown==0)body.addView(label("Keine nativen Einzelimpulse gemeldet.",13,muted));
    }
    private void playEnvelope(int from,int to){
        if(!available()||!envelope)return;
        try{
            VibrationEffect effect=Envelope.create(motor,strength/100f,from/100f,to/100f);
            emit(effect,240,"Hüllkurve "+from+" → "+to+" % Schärfe",false);
        }catch(RuntimeException e){report("Hüllkurve nicht verfügbar – Einzelimpulse verwenden");}
    }
    /** Keep Android 16-only classes out of the older device paths. */
    private static final class Envelope{
        static boolean supported(Vibrator v){
            try{
                if(!v.areEnvelopeEffectsSupported())return false;
                android.os.vibrator.VibratorEnvelopeEffectInfo info=v.getEnvelopeEffectInfo();
                return info!=null&&info.getMaxSize()>=3&&info.getMinControlPointDurationMillis()<=60
                    &&info.getMaxControlPointDurationMillis()>=60&&info.getMaxDurationMillis()>=240;
            }catch(RuntimeException e){return false;}
        }
        static VibrationEffect create(Vibrator v,float intensity,float from,float to){
            long minimum=v.getEnvelopeEffectInfo().getMinControlPointDurationMillis();
            long rise=Math.max(40,minimum),fall=Math.max(40,minimum);
            return new VibrationEffect.BasicEnvelopeBuilder().setInitialSharpness(from)
                .addControlPoint(intensity,from,rise)
                .addControlPoint(intensity,to,60)
                .addControlPoint(0f,to,fall).build();
        }
    }

    private void stop(){
        if(pad!=null)pad.reset();
        if(hasMotor)try{motor.cancel();}catch(RuntimeException ignored){}
        busyUntil=0;magnetOutput.reset();
    }
    @Override public void close(){
        stop();Dialog d=dialog;if(d!=null)d.dismiss();
    }
    private int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}

    private final class Pad extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final HapticLabPatterns.Gesture gesture=new HapticLabPatterns.Gesture();
        private final MagnetField magnet=new MagnetField();
        private final Runnable magnetTick=this::tickMagnet;
        private final float density=getResources().getDisplayMetrics().density;
        private boolean down;
        private float x,y;
        private MagnetField.Reading magnetReading;
        Pad(Context context){
            super(context);setFocusable(true);setHapticFeedbackEnabled(false);
            setContentDescription("Haptische Testfläche. Mit einem Finger streichen. Einzelimpulse sind darunter als Tasten verfügbar.");
        }
        private void signal(HapticLabPatterns.Signal s){if(s!=null)play(s.cue,s.scale,true);}
        private void updateMagnet(){
            if(!down||surface!=HapticLabPatterns.Surface.MAGNET)return;
            long now=android.os.SystemClock.uptimeMillis();
            if(!isShown()||!hasWindowFocus()){stop();return;}
            long remaining=magnet.remaining(now);
            if(remaining<=0){stop();report("5 Sekunden erreicht – Finger neu ansetzen");return;}
            magnetReading=magnet.sample(x/density,y/density,getWidth()/density,getHeight()/density,now);
            playMagnet(magnetReading,now,remaining);
            invalidate();
        }
        private void tickMagnet(){
            updateMagnet();
            if(down&&surface==HapticLabPatterns.Surface.MAGNET)postDelayed(magnetTick,MagnetOutput.TICK_MS);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            switch(action){
                case MotionEvent.ACTION_DOWN:
                    stop();down=true;x=event.getX();y=event.getY();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    if(surface==HapticLabPatterns.Surface.MAGNET){
                        magnet.begin(android.os.SystemClock.uptimeMillis());
                        tickMagnet();
                    }else signal(gesture.begin(surface,x/density,y/density,getWidth()/density,getHeight()/density,event.getEventTime()));
                    invalidate();return true;
                case MotionEvent.ACTION_MOVE:
                    if(down&&event.getPointerCount()==1){
                        x=event.getX();y=event.getY();
                        if(surface==HapticLabPatterns.Surface.MAGNET)updateMagnet();
                        else signal(gesture.move(x/density,y/density,getWidth()/density,getHeight()/density,event.getEventTime()));
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stop();getParent().requestDisallowInterceptTouchEvent(false);
                    if(action==MotionEvent.ACTION_UP)performClick();
                    return true;
                default:return true;
            }
        }
        @Override public boolean performClick(){super.performClick();return true;}
        void reset(){
            down=false;gesture.stop();magnet.stop();magnetReading=null;
            removeCallbacks(magnetTick);invalidate();
        }
        @Override protected void onDetachedFromWindow(){
            if(pad==this)stop();else reset();
            super.onDetachedFromWindow();
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight();
            paint.setStyle(Paint.Style.FILL);paint.setColor(panel);
            c.drawRoundRect(0,0,w,h,dp(10),dp(10),paint);
            if(surface==HapticLabPatterns.Surface.MAGNET){
                float cx=w/2f,cy=h/2f;
                float inner=MagnetField.targetRadius(w/density,h/density)*density;
                float outer=MagnetField.haloRadius(w/density,h/density)*density;
                paint.setColor(accent);
                paint.setShader(new android.graphics.RadialGradient(cx,cy,outer,
                    new int[]{accent,accent,android.graphics.Color.TRANSPARENT},
                    new float[]{0f,inner/outer,1f},android.graphics.Shader.TileMode.CLAMP));
                paint.setAlpha(100);c.drawCircle(cx,cy,outer,paint);paint.setShader(null);paint.setAlpha(255);
                paint.setStyle(Paint.Style.STROKE);paint.setColor(accent);paint.setAlpha(85);paint.setStrokeWidth(dp(1));
                c.drawCircle(cx,cy,outer,paint);
                for(int i=1;i<4;i++)c.drawCircle(cx,cy,inner+(outer-inner)*i/4f,paint);
                paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
                paint.setColor(magnetReading!=null&&magnetReading.latched?accent:panel);
                c.drawCircle(cx,cy,inner,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setColor(accent);paint.setStrokeWidth(dp(2));
                c.drawCircle(cx,cy,inner,paint);paint.setStyle(Paint.Style.FILL);
                paint.setColor(magnetReading!=null&&magnetReading.latched?bg:text);
                paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(19));c.drawText("OK",cx,cy+dp(6),paint);
            }else if(surface==HapticLabPatterns.Surface.RELIEF){
                float cx=w/2f,cy=h/2f,r=Math.min(w,h)*.43f;
                int[] alphas={65,105,150,225};float[] rings={1f,.78f,.43f,.29f};
                for(int i=0;i<rings.length;i++){
                    paint.setColor(accent);paint.setAlpha(alphas[i]);
                    c.drawCircle(cx,cy,r*rings[i],paint);
                    paint.setAlpha(255);
                }
                paint.setColor(bg);paint.setTextSize(dp(17));paint.setTextAlign(Paint.Align.CENTER);
                c.drawText("OK",cx,cy+dp(6),paint);
            }else if(surface==HapticLabPatterns.Surface.TEXTURES){
                float third=w/3;
                paint.setColor(accent);paint.setAlpha(110);paint.setStrokeWidth(dp(1));
                for(float a=third+dp(4);a<third*2;a+=dp(5))c.drawLine(a,dp(22),a,h-dp(26),paint);
                paint.setStrokeWidth(dp(3));
                for(float a=third*2+dp(9);a<w;a+=dp(17))c.drawLine(a,dp(22),a,h-dp(26),paint);
                paint.setAlpha(255);paint.setColor(text);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(13));
                c.drawText("Glatt",third*.5f,h-dp(9),paint);
                c.drawText("Fein",third*1.5f,h-dp(9),paint);
                c.drawText("Grob",third*2.5f,h-dp(9),paint);
            }else{
                for(int i=0;i<10;i++){
                    float left=w*i/10f,right=w*(i+1)/10f;
                    paint.setColor(accent);paint.setAlpha(surface==HapticLabPatterns.Surface.RAMP?30+i*24:115);
                    c.drawRect(left+dp(1),dp(10),right-dp(1),h-dp(28),paint);
                    paint.setAlpha(255);paint.setColor(text);paint.setTextSize(dp(12));paint.setTextAlign(Paint.Align.CENTER);
                    c.drawText(String.valueOf(i+1),(left+right)/2,h-dp(9),paint);
                }
            }
            if(down){
                paint.setColor(text);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));
                c.drawCircle(x,y,dp(12),paint);paint.setStyle(Paint.Style.FILL);
            }
        }
    }
}
