package de.sofacockpit.app;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import de.sofacockpit.app.theme.CockpitTheme;
import de.sofacockpit.app.theme.ThemeArt;
import de.sofacockpit.app.theme.ThemePack;

/** Native voice surfaces use the same palette, typography, corners and art as the cockpit. */
final class LabVoiceUi {
    final Context context;
    final ThemePack pack;
    final CockpitTheme t;
    LabVoiceUi(Context context,ThemePack pack){this.context=context;this.pack=pack;this.t=pack.theme;}
    int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(context);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    LinearLayout root(){
        LinearLayout l=column();int pad=dp(t.paddingDp);
        l.setPadding(pad,pad,pad,pad);l.setBackgroundColor(t.background);
        l.setFocusableInTouchMode(true);return l;
    }
    LinearLayout panel(){
        LinearLayout l=column();int pad=dp(t.paddingDp);
        l.setBackground(ThemeArt.surface(pack,t.panel,t.cornerDp,context.getResources().getDisplayMetrics().density,false,false));
        l.setPadding(pad,pad,pad,pad);return l;
    }
    TextView text(String value,int sp,int color,boolean bold){
        TextView v=new TextView(context);v.setText(value);v.setTextSize(sp*t.fontScale);v.setTextColor(color);
        v.setTypeface(ThemeArt.typeface(t,bold?Typeface.BOLD:Typeface.NORMAL));return v;
    }
    Button button(String title,boolean primary,Runnable run){
        Button b=new Button(context);b.setAllCaps(false);b.setText(title);b.setTextSize(14*t.fontScale);
        b.setTypeface(ThemeArt.typeface(t,Typeface.BOLD));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
        b.setMinWidth(dp(48));b.setMinimumWidth(dp(48));b.setPadding(dp(10),dp(8),dp(10),dp(8));
        style(b,primary);b.setOnClickListener(v->run.run());return b;
    }
    void style(Button b,boolean primary){
        b.setTextColor(primary?t.accent:t.text);
        b.setBackground(ThemeArt.surface(pack,primary?t.selected:t.control,t.cornerDp,
            context.getResources().getDisplayMetrics().density,primary,true));
    }
    EditText input(String hint,int max){
        EditText e=new EditText(context);e.setSingleLine(true);e.setTextSize(15*t.fontScale);
        e.setTypeface(ThemeArt.typeface(t,Typeface.NORMAL));e.setTextColor(t.text);e.setHintTextColor(t.muted);
        e.setHint(hint);e.setSaveEnabled(false);e.setMinHeight(dp(52));
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        e.setBackground(ThemeArt.surface(pack,t.control,t.cornerDp,
            context.getResources().getDisplayMetrics().density,false,false));
        e.setBackgroundTintList(null);e.setPadding(dp(12),dp(8),dp(12),dp(8));return e;
    }
    void add(LinearLayout parent,View child,int margin){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(margin);
        parent.addView(child,lp);
    }
    void weighted(LinearLayout row,View v,int leftMargin){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);lp.leftMargin=dp(leftMargin);row.addView(v,lp);
    }
    void header(LinearLayout root,String title,String sub,Runnable back,Runnable close){
        LinearLayout r=row();
        if(back!=null){Button b=button("‹",false,back);b.setTextSize(23*t.fontScale);b.setContentDescription("Zurück");
            r.addView(b,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        LinearLayout titles=column();titles.addView(text(title,20,t.text,true));
        if(sub!=null&&!sub.isEmpty())titles.addView(text(sub,12,t.muted,false));
        weighted(r,titles,back==null?0:10);
        Button x=button("×",false,close);x.setTextSize(22*t.fontScale);x.setContentDescription("Schließen");
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(48),dp(48));lp.leftMargin=dp(8);r.addView(x,lp);
        add(root,r,0);
    }
    ScrollView scroll(LinearLayout content){
        ScrollView s=new ScrollView(context);s.setFillViewport(false);s.setClipToPadding(false);
        s.setVerticalScrollBarEnabled(true);s.addView(content);return s;
    }
    void fill(LinearLayout root,ScrollView scroll){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1f);lp.topMargin=dp(10);root.addView(scroll,lp);
    }
    void configure(Dialog d,boolean fullScreen,LinearLayout root){
        Window w=d.getWindow();if(w==null)return;
        w.setBackgroundDrawable(new ColorDrawable(fullScreen?t.background:android.graphics.Color.TRANSPARENT));
        w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(fullScreen){SystemBarInsets.install(w,root);w.setLayout(-1,-1);}
        else{
            int width=Math.min(context.getResources().getDisplayMetrics().widthPixels-dp(24),dp(520));
            w.setLayout(Math.max(dp(200),width),-2);
        }
    }
}
