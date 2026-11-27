package de.sofacockpit.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.HapticGenerator;
import android.os.Build;
import android.os.SystemClock;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;

/** A single, finite AudioTrack per touch. All I/O and resource cleanup take
 * place on the lab's serial worker. No microphone, network, global audio
 * settings, hidden method invocation, or ordinary Vibrator effects. */
final class HapticAudioSession implements Runnable {
    enum Mode { DIRECT, GENERATOR }
    private static final int RATE=HapticStreamSignal.RATE;
    private static final int FRAMES=240;
    private static final long LIMIT_MS=5000, ROUTE_WAIT_MS=600;
    // AOSP AudioFormat CHANNEL_OUT_HAPTIC_A is @hide. This is an experimental
    // channel configuration passed to public AudioTrack.Builder, not a public
    // SDK contract. Reject the test if the exact format is not accepted.
    private static final int HAPTIC_A=0x20000000;
    private final AudioManager audio;
    private final Mode mode;
    private final HapticStreamSignal signal;
    private final Consumer<String> status;
    private final Runnable finished;
    private final long deadline=SystemClock.elapsedRealtime()+LIMIT_MS;
    private volatile boolean cancelled;

    HapticAudioSession(Context context,Mode mode,int strength,Consumer<String> status,Runnable finished){
        audio=context.getSystemService(AudioManager.class);
        this.mode=mode;signal=new HapticStreamSignal(strength);
        this.status=status;this.finished=finished;
    }
    static boolean directSupported(){
        try{return Build.VERSION.SDK_INT>=29&&AudioManager.isHapticPlaybackSupported();}
        catch(RuntimeException|LinkageError e){return false;}
    }
    static boolean generatorSupported(){
        try{return Build.VERSION.SDK_INT>=31&&Generator.available();}
        catch(RuntimeException|LinkageError e){return false;}
    }
    void position(float fraction){signal.position(fraction);}
    boolean latched(){return signal.latched();}
    void cancel(){cancelled=true;}
    private boolean running(){return !cancelled&&SystemClock.elapsedRealtime()<deadline;}
    private AudioDeviceInfo speaker(){
        if(audio==null)return null;
        for(AudioDeviceInfo device:audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
            if(device.getType()==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)return device;
        return null;
    }
    private static boolean speakerRoute(AudioTrack track){
        AudioDeviceInfo routed=track.getRoutedDevice();
        return routed!=null&&routed.getType()==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
    }
    private static void dispose(AudioTrack track){
        if(track==null)return;
        // stop() alone would drain queued PCM. Pause and flush immediately.
        try{track.pause();}catch(RuntimeException ignored){}
        try{track.flush();}catch(RuntimeException ignored){}
        try{track.release();}catch(RuntimeException ignored){}
    }
    private AudioTrack open(boolean direct,int encoding,AudioDeviceInfo speaker){
        int mask=AudioFormat.CHANNEL_OUT_STEREO|(direct?HAPTIC_A:0);
        int channels=direct?3:2;
        int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
        int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_STEREO,encoding);
        if(min<=0)throw new IllegalStateException("PCM-Format nicht verfügbar");
        int frames=Math.max(4*FRAMES,(min+2*bytes-1)/(2*bytes));
        AudioTrack track=null;
        try{
            track=new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setHapticChannelsMuted(false).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                    .setEncoding(encoding).setChannelMask(mask).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(frames*channels*bytes).build();
            if(track.getState()!=AudioTrack.STATE_INITIALIZED||
                track.getChannelConfiguration()!=mask||track.getChannelCount()!=channels||
                track.getSampleRate()!=RATE||track.getAudioFormat()!=encoding)
                throw new IllegalStateException("Kanalformat nicht akzeptiert");
            if(!track.setPreferredDevice(speaker))
                throw new IllegalStateException("Interner Lautsprecher nicht wählbar");
            // No changes to global media volume. Direct L/R PCM is zero;
            // the generator carrier may still be audible, even at low gain.
            if(track.setVolume(direct?1f:.18f)!=AudioTrack.SUCCESS)
                throw new IllegalStateException("Spurpegel nicht setzbar");
            track.setBufferSizeInFrames(Math.max(4*FRAMES,frames));
            return track;
        }catch(RuntimeException|LinkageError e){dispose(track);throw e;}
    }

    @Override public void run(){
        AudioTrack track=null;
        Generator generator=null;
        String result="Test beendet";
        try{
            if(!running())return;
            boolean direct=mode==Mode.DIRECT;
            if(!(direct?directSupported():generatorSupported())){
                result="Android meldet keine Unterstützung";return;
            }
            AudioDeviceInfo speaker=speaker();
            if(speaker==null){result="Kein interner Lautsprecher verfügbar";return;}
            int encoding=AudioFormat.ENCODING_PCM_16BIT;
            try{track=open(direct,encoding,speaker);}
            catch(RuntimeException|LinkageError first){
                if(!running())return;
                encoding=AudioFormat.ENCODING_PCM_FLOAT;
                track=open(direct,encoding,speaker);
            }
            if(!running())return;
            if(!direct)generator=new Generator(track.getAudioSessionId());
            if(!running())return;
            int channels=direct?3:2;
            boolean floating=encoding==AudioFormat.ENCODING_PCM_FLOAT;
            float[] samples=new float[FRAMES*channels];
            ByteBuffer pcm=ByteBuffer.allocateDirect(samples.length*(floating?4:2))
                .order(ByteOrder.nativeOrder());
            pcm.limit(0);
            track.play();
            long routeDeadline=SystemClock.elapsedRealtime()+ROUTE_WAIT_MS;
            boolean routeVerified=false;
            int initialUnderruns=track.getUnderrunCount();
            int bufferFrames=track.getBufferSizeInFrames();
            while(running()){
                boolean correctRoute=speakerRoute(track);
                if(!correctRoute){
                    if(routeVerified||SystemClock.elapsedRealtime()>=routeDeadline){
                        result="Ausgabeweg nicht interner Lautsprecher – gestoppt";return;
                    }
                }else if(!routeVerified){
                    routeVerified=true;
                    status.accept("Stream aktiv · "+(floating?"Float":"PCM16")+" · Puffer "+Math.round(1000f*bufferFrames/RATE)+" ms");
                }
                if(!pcm.hasRemaining()){
                    signal.render(samples,direct,routeVerified);
                    pcm.clear();
                    for(float sample:samples){
                        if(floating)pcm.putFloat(sample);
                        else pcm.putShort((short)Math.round(sample*32767));
                    }
                    pcm.flip();
                }
                int written=track.write(pcm,pcm.remaining(),AudioTrack.WRITE_NON_BLOCKING);
                if(written<0){result="AudioTrack-Schreibfehler "+written;return;}
                if(written==0)SystemClock.sleep(2);
            }
            if(!cancelled){
                result="5 Sekunden erreicht · Pufferunterläufe "+Math.max(0,track.getUnderrunCount()-initialUnderruns);
            }
        }catch(RuntimeException|LinkageError e){
            result="Stream nicht verfügbar: "+e.getClass().getSimpleName()+
                (e instanceof IllegalStateException&&e.getMessage()!=null?" · "+e.getMessage():"");
        }finally{
            if(track!=null){
                try{track.pause();}catch(RuntimeException ignored){}
                try{track.flush();}catch(RuntimeException ignored){}
            }
            if(generator!=null)generator.close();
            dispose(track);
            try{if(!cancelled)status.accept(result);}
            finally{finished.run();}
        }
    }
    /** Keep API-31-only references off the older execution path. */
    private static final class Generator implements AutoCloseable {
        final HapticGenerator effect;
        static boolean available(){return HapticGenerator.isAvailable();}
        Generator(int session){
            HapticGenerator candidate=HapticGenerator.create(session);
            if(candidate==null)throw new IllegalStateException("HapticGenerator nicht erzeugbar");
            try{
                if(candidate.setEnabled(true)!=android.media.audiofx.AudioEffect.SUCCESS)
                    throw new IllegalStateException("HapticGenerator nicht aktivierbar");
                effect=candidate;
            }catch(RuntimeException|LinkageError e){candidate.release();throw e;}
        }
        @Override public void close(){
            try{effect.setEnabled(false);}catch(RuntimeException ignored){}
            try{effect.release();}catch(RuntimeException ignored){}
        }
    }
}
