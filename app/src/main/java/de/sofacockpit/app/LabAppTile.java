package de.sofacockpit.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One accessible button with independent, centered image and label areas.
 * No compound drawable, artificial zero-size text or shared baseline. */
final class LabAppTile extends FrameLayout {
    LabAppTile(Context context,CharSequence caption,AppPresentation.Mode requested,Bitmap bitmap,
               int color,Typeface typeface,float textSp,Drawable background){
        super(context);
        setBackground(background);
        setPadding(dp(4),dp(3),dp(4),dp(3));
        setFocusable(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        AppPresentation.Mode mode=AppTileLayout.displayed(requested,bitmap!=null);
        if(mode==AppPresentation.Mode.TEXT){
            TextView text=text(caption,color,typeface,textSp,3);
            addView(text,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        }else if(mode==AppPresentation.Mode.ICON){
            addView(image(bitmap),new FrameLayout.LayoutParams(dp(AppTileLayout.ICON_WIDTH_DP),
                dp(AppTileLayout.ICON_ONLY_HEIGHT_DP),Gravity.CENTER));
        }else{
            LinearLayout group=new LinearLayout(context);
            group.setOrientation(LinearLayout.VERTICAL);
            group.setGravity(Gravity.CENTER);
            group.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            group.addView(image(bitmap),new LinearLayout.LayoutParams(dp(AppTileLayout.ICON_WIDTH_DP),
                dp(AppTileLayout.COMBINED_ICON_HEIGHT_DP)));
            TextView text=text(caption,color,typeface,textSp,2);
            LinearLayout.LayoutParams textLp=new LinearLayout.LayoutParams(-1,dp(34));
            textLp.topMargin=dp(2);
            group.addView(text,textLp);
            addView(group,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        }
    }
    private ImageView image(Bitmap bitmap){
        ImageView view=new ImageView(getContext());
        view.setImageBitmap(bitmap);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }
    private TextView text(CharSequence caption,int color,Typeface typeface,float textSp,int lines){
        TextView view=new TextView(getContext());
        view.setText(caption);
        view.setTextColor(color);
        view.setTypeface(typeface);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setMaxLines(lines);
        view.setEllipsize(TextUtils.TruncateAt.END);
        int max=Math.max(9,Math.round(textSp));
        view.setAutoSizeTextTypeUniformWithConfiguration(Math.min(9,max),max,1,TypedValue.COMPLEX_UNIT_SP);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public CharSequence getAccessibilityClassName(){return android.widget.Button.class.getName();}
}
