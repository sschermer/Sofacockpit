package de.sofacockpit.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/** Optional audio-haptic orientation. The UI never waits for audio and
 * unsupported hardware or rejected formats are silent, not error dialogs. */
final class RemoteHaptics implements AutoCloseable {
    static final int STRENGTH=60;
    private final Context context;
    private final BooleanSupplier allowed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable guard=this::guard;
    private boolean supported=HapticAudioSession.directSupported();
    private boolean attempted,closed;
    private long deadline;
    private ExecutorService worker;
    private HapticAudioSession session;

    RemoteHaptics(Context context,BooleanSupplier allowed){
        this.context=context.getApplicationContext();this.allowed=allowed;
    }
    void begin(){
        stop();deadline=SystemClock.elapsedRealtime()+5000;
    }
    boolean canUpdate(){
        if(closed||!supported||deadline<=SystemClock.elapsedRealtime()||!allowed.getAsBoolean()){
            stop();return false;
        }
        return session!=null||!attempted;
    }
    void update(RemoteMagnetField.Reading reading){
        if(!canUpdate())return;
        if(session!=null){session.field(reading.gain,reading.snap);return;}
        if(reading.gain<=0&&!reading.snap)return;
        long remaining=deadline-SystemClock.elapsedRealtime();
        if(remaining<=0){stop();return;}
        attempted=true;
        try{
            if(worker==null)worker=Executors.newSingleThreadExecutor();
            final HapticAudioSession[] identity=new HapticAudioSession[1];
            HapticAudioSession next=new HapticAudioSession(context,HapticAudioSession.Mode.DIRECT,
                STRENGTH,remaining,ignored->{},()->main.post(()->{
                    HapticAudioSession done=identity[0];
                    // A device can advertise support yet reject the actual PCM
                    // channel format. Do not try that format on every swipe.
                    if(done!=null&&done.formatRejected())supported=false;
                    if(session==done){session=null;main.removeCallbacks(guard);}
                }));
            identity[0]=next;session=next;
            next.field(reading.gain,reading.snap);
            worker.execute(next);
            main.postDelayed(guard,50);
        }catch(RuntimeException|LinkageError e){
            if(session!=null)session.cancel();
            session=null;supported=false;main.removeCallbacks(guard);
        }
    }
    private void guard(){
        if(session==null)return;
        if(!canUpdate())return;
        main.postDelayed(guard,50);
    }
    void stop(){
        deadline=0;attempted=false;
        main.removeCallbacks(guard);
        if(session!=null){session.cancel();session=null;}
    }
    @Override public void close(){
        stop();closed=true;
        if(worker!=null){worker.shutdownNow();worker=null;}
    }
}
