package de.sofacockpit.app;

/** Single-use dispatch gate per voice dialog. Only a current, unchanged command can be claimed. */
final class VoiceDispatch {
    private boolean claimed;
    boolean claimed(){return claimed;}

    // Validate the ORIGINAL final transcript, before any UI length filter can truncate it.
    static boolean acceptableTranscript(String text){
        return text!=null&&text.length()<=240&&!VoiceCommands.phrase(text).isEmpty();
    }

    VoiceCommands.Command claim(VoiceCommands.Command expected,VoiceCommands.Match latest){
        if(claimed||expected==null||latest==null||latest.command==null||
            !expected.equals(latest.command)||!expected.label.equals(latest.command.label))return null;
        claimed=true;
        return latest.command;
    }
}
