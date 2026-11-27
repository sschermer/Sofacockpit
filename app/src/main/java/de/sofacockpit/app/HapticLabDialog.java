package de.sofacockpit.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Two isolated audio-haptic experiments. No TV or soundbar commands. */
final class HapticLabDialog implements AutoCloseable {
    private final Activity activity;
    private final int bg,panel,text,muted,accent;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final boolean direct=HapticAudioSession.directSupported();
    private final boolean generator=HapticAudioSession.generatorSupported();
    private Dialog dialog;
    private TextView status,levelLabel;
    private Pad first,second,activePad;
    private ExecutorService worker;
    private HapticAudioSession session;
    private int strength=60;
    private final Runnable guard=this::checkGuard;

    HapticLabDialog(Activity activity,int bg,int panel,int text,int muted,int accent){
        this.activity=activity;this.bg=bg;this.panel=panel;this.text=text;this.muted=muted;this.accent=accent;
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
        worker=Executors.newSingleThreadExecutor();
        LinearLayout outer=column();outer.setBackgroundColor(bg);
        outer.setPadding(dp(12),dp(12),dp(12),dp(8));
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Haptik-Labor",22,text);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(button("Schließen",this::close),new LinearLayout.LayoutParams(-2,dp(48)));
        outer.addView(header);
        ScrollView scroll=new ScrollView(activity);LinearLayout body=column();
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        levelLabel=label("Maximale Intensität: "+strength+" %",16,text);body.addView(levelLabel);
        SeekBar level=new SeekBar(activity);level.setMax(100);level.setProgress(strength);
        level.setContentDescription("Maximale Intensität");
        level.setProgressTintList(ColorStateList.valueOf(accent));
        level.setThumbTintList(ColorStateList.valueOf(accent));
        body.addView(level,new LinearLayout.LayoutParams(-1,dp(48)));
        level.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onStartTrackingTouch(SeekBar bar){stop();}
            @Override public void onStopTrackingTouch(SeekBar bar){}
            @Override public void onProgressChanged(SeekBar bar,int value,boolean user){
                strength=value;levelLabel.setText("Maximale Intensität: "+value+" %");
                if(value==0)stop();
            }
        });
        body.addView(label("Von links nach rechts streichen, innehalten, zurückstreichen. Rechts 80 → 100 % Einrasten, danach Ruhe.",13,muted));
        status=label("Bereit · maximal 5 Sekunden je Berührung",13,accent);body.addView(status);
        heading(body,"1 · Direkter Haptikkanal");
        body.addView(label(direct?"Android meldet Audio-Haptik: ja · experimenteller Kanal, kein Audiosignal links/rechts.":
            "Android meldet Audio-Haptik: nein oder Abfrage fehlgeschlagen.",12,muted));
        first=new Pad(activity,HapticAudioSession.Mode.DIRECT,direct);
        body.addView(first,new LinearLayout.LayoutParams(-1,dp(144)));
        heading(body,"2 · HapticGenerator");
        body.addView(label(generator?"Android meldet HapticGenerator: ja · ein leiser Ton kann hörbar sein.":
            "Android meldet HapticGenerator: nein oder Abfrage fehlgeschlagen.",12,muted));
        second=new Pad(activity,HapticAudioSession.Mode.GENERATOR,generator);
        body.addView(second,new LinearLayout.LayoutParams(-1,dp(144)));
        body.addView(label("Nur interner Lautsprecher. Kein Mikrofon, keine Systemlautstärkeänderung. 100 % ist der gewählte Signalpegel, nicht eine gemessene Motorstärke.",12,muted));
        outer.addView(button("Test stoppen",this::stop),new LinearLayout.LayoutParams(-1,dp(48)));
        d.setContentView(outer);
        d.setOnDismissListener(ignored->{
            stop();
            if(worker!=null){worker.shutdownNow();worker=null;}
            if(dialog==d){dialog=null;first=null;second=null;status=null;}
        });
        d.show();
        if(d.getWindow()!=null)d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private int dp(float v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private TextView label(String value,int sp,int color){
        TextView v=new TextView(activity);v.setText(value);v.setTextSize(sp);v.setTextColor(color);
        v.setPadding(dp(4),dp(4),dp(4),dp(4));return v;
    }
    private void heading(LinearLayout body,String value){
        TextView v=label(value,16,text);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setPadding(dp(4),dp(12),0,dp(2));body.addView(v);
    }
    private Button button(String value,Runnable action){
        Button v=new Button(activity);v.setText(value);v.setAllCaps(false);v.setTextSize(13);v.setTextColor(text);
        v.setMinHeight(0);v.setMinimumHeight(0);v.setHapticFeedbackEnabled(false);
        GradientDrawable shape=new GradientDrawable();shape.setColor(panel);shape.setCornerRadius(dp(8));
        v.setBackground(shape);v.setOnClickListener(ignored->action.run());return v;
    }
    private void report(String value){if(status!=null)status.setText(value);}
    private boolean available(){
        return dialog!=null&&dialog.isShowing()&&dialog.getWindow()!=null&&
            dialog.getWindow().getDecorView().hasWindowFocus()&&strength>0&&
            Settings.System.getInt(activity.getContentResolver(),Settings.System.HAPTIC_FEEDBACK_ENABLED,1)!=0;
    }
    private void begin(Pad pad,float position){
        stop();
        if(!pad.supported){report("Diese Technik wird von Android nicht als verfügbar gemeldet");return;}
        if(!available()||worker==null){report("Haptik ausgeschaltet oder Intensität 0 %");return;}
        pad.down=true;activePad=pad;
        final HapticAudioSession[] identity=new HapticAudioSession[1];
        HapticAudioSession next=new HapticAudioSession(activity,pad.mode,strength,
            message->main.post(()->{if(session==identity[0])report(message);}),
            ()->main.post(()->{if(session==identity[0]){session=null;activePad=null;pad.reset();main.removeCallbacks(guard);}}));
        identity[0]=next;session=next;next.position(position);
        report((pad.mode==HapticAudioSession.Mode.DIRECT?"Direkter Kanal":"HapticGenerator")+" · Stream wird geöffnet");
        try{worker.execute(next);}
        catch(RuntimeException e){stop();report("Test konnte nicht gestartet werden");return;}
        main.postDelayed(guard,50);
    }
    private void checkGuard(){
        if(session==null)return;
        if(!available()||activePad==null||!activePad.isShown()||!activePad.hasWindowFocus()){
            stop();return;
        }
        main.postDelayed(guard,50);
    }
    private void stop(){
        main.removeCallbacks(guard);
        if(session!=null){session.cancel();session=null;}
        activePad=null;
        if(first!=null)first.reset();
        if(second!=null)second.reset();
    }
    @Override public void close(){stop();Dialog d=dialog;if(d!=null)d.dismiss();}
    private final class Pad extends View {
        final HapticAudioSession.Mode mode;final boolean supported;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean down;float x,y;
        Pad(Context context,HapticAudioSession.Mode mode,boolean supported){
            super(context);this.mode=mode;this.supported=supported;
            setHapticFeedbackEnabled(false);setFocusable(true);
            setContentDescription((mode==HapticAudioSession.Mode.DIRECT?"Direkter Haptikkanal":"HapticGenerator")+
                ": horizontale Testbahn mit OK-Ziel rechts");
        }
        void reset(){down=false;invalidate();}
        @Override public boolean onTouchEvent(MotionEvent event){
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    if(activePad!=null&&activePad!=this){stop();return true;}
                    x=event.getX();y=event.getY();
                    if(getWidth()<=0)return true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    begin(this,x/getWidth());invalidate();return true;
                case MotionEvent.ACTION_MOVE:
                    if(down&&activePad==this&&session!=null&&event.getPointerCount()==1){
                        x=event.getX();y=event.getY();
                        if(x<0||x>=getWidth()||y<0||y>=getHeight()){stop();return true;}
                        session.position(x/getWidth());invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if(activePad==this)stop();
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if(event.getActionMasked()==MotionEvent.ACTION_UP)performClick();
                    return true;
                default:return true;
            }
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override protected void onDetachedFromWindow(){
            if(activePad==this)stop();else reset();
            super.onDetachedFromWindow();
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float w=getWidth(),h=getHeight();
            if(w<=0||h<=0)return;
            float target=w*HapticStreamSignal.TARGET;
            paint.setStyle(Paint.Style.FILL);paint.setColor(panel);
            canvas.drawRoundRect(0,0,w,h,dp(8),dp(8),paint);
            int color=accent&0x00ffffff;
            paint.setShader(new LinearGradient(0,0,w,0,
                new int[]{color,color,0x70000000|color,0x70000000|color},
                new float[]{0,HapticStreamSignal.START,HapticStreamSignal.PLATEAU,1},Shader.TileMode.CLAMP));
            paint.setAlpha(supported?255:75);canvas.drawRect(0,0,target,h,paint);
            paint.setShader(null);paint.setAlpha(255);
            boolean latched=down&&activePad==this&&session!=null&&session.latched();
            paint.setColor(latched?accent:panel);canvas.drawRect(target,0,w,h,paint);
            paint.setColor(accent);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));
            canvas.drawRect(target+dp(1),dp(1),w-dp(1),h-dp(1),paint);
            paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.min(dp(18),(w-target)*.55f));paint.setColor(latched?bg:text);
            canvas.drawText("OK",(target+w)/2,h/2-(paint.ascent()+paint.descent())/2,paint);
            if(!supported){
                paint.setTextSize(dp(13));paint.setColor(muted);
                canvas.drawText("Nicht unterstützt",target/2,h/2,paint);
            }
            if(down){
                paint.setColor(text);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));
                canvas.drawCircle(x,y,dp(12),paint);paint.setStyle(Paint.Style.FILL);
            }
        }
    }
}
