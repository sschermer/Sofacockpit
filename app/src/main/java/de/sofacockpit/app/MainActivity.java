package de.sofacockpit.app;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;
import de.tonmaschine.android.MainViewModel;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends ComponentActivity {
    private static final int BG = Color.rgb(11, 14, 19);
    private static final int SURFACE = Color.rgb(23, 28, 36);
    private static final int SURFACE_ALT = Color.rgb(32, 39, 49);
    private static final int TEXT = Color.rgb(242, 245, 249);
    private static final int MUTED = Color.rgb(151, 163, 179);
    private static final int ACCENT = Color.rgb(112, 190, 255);
    private Pager pager;
    private TextView[] dots;
    private int pageWidth;
    private MainViewModel soundbarModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        soundbarModel = new ViewModelProvider(this).get(MainViewModel.class);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);

        pager = new Pager();
        pager.setFillViewport(true);
        pager.setHorizontalScrollBarEnabled(false);
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        shell.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(4), 0, dp(18));
        dots = new TextView[]{label("●", 12, MUTED), label("●", 12, ACCENT), label("●", 12, MUTED)};
        for (TextView dot : dots) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(24));
            dot.setGravity(Gravity.CENTER);
            footer.addView(dot, lp);
        }
        shell.addView(footer, new LinearLayout.LayoutParams(-1, dp(34)));
        setContentView(shell);

        pager.addPage(SofacockpitComposeViewsKt.createTonmaschineComposePage(this, soundbarModel));
        pager.addPage(homePage(soundbarModel));
        pager.addPage(page("TV-Steuerkreuz", "Fernbedienung", "Die TV-Kopplung und Navigation sind noch nicht eingerichtet."));
        pager.setOnPageChanged(index -> updateDots(index));
        pager.post(() -> {
            pageWidth = pager.getWidth();
            pager.scrollTo(pageWidth, 0);
        });
    }

    private LinearLayout.LayoutParams pageParams() {
        return new LinearLayout.LayoutParams(-1, -1);
    }

    private View homePage(MainViewModel vm) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(20), dp(20), dp(20), dp(18));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Sofacockpit", 28, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(titleRow);
        LinearLayout.LayoutParams titleLp = (LinearLayout.LayoutParams) titleRow.getLayoutParams();
        titleLp.bottomMargin = dp(12);
        titleRow.setLayoutParams(titleLp);

        deviceHeader(content, "FERNSEHER", "Nicht verbunden");

        LinearLayout streaming = section(content, "STREAMING");
        HorizontalScrollView streamScroll = new HorizontalScrollView(this);
        streamScroll.setHorizontalScrollBarEnabled(false);
        streamScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout streams = new LinearLayout(this);
        streams.setOrientation(LinearLayout.HORIZONTAL);
        String[] services = {"Netflix", "YouTube", "Prime", "Waipu"};
        for (String service : services) {
            TextView tile = actionButton(service, "Startet nicht");
            LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(dp(112), dp(58));
            tileLp.rightMargin = dp(10);
            streams.addView(tile, tileLp);
        }
        streamScroll.addView(streams);
        streaming.addView(streamScroll);

        LinearLayout volume = section(content, "LAUTSTÄRKE");
        LinearLayout volumeRow = new LinearLayout(this);
        volumeRow.setGravity(Gravity.CENTER_VERTICAL);
        volumeRow.addView(actionButton("−", "Nicht verbunden"), new LinearLayout.LayoutParams(dp(54), dp(52)));
        SeekBar seek = new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(35);
        seek.setEnabled(false);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(0, dp(52), 1);
        seekLp.leftMargin = dp(8);
        seekLp.rightMargin = dp(8);
        volumeRow.addView(seek, seekLp);
        volumeRow.addView(actionButton("+", "Nicht verbunden"), new LinearLayout.LayoutParams(dp(54), dp(52)));
        volume.addView(volumeRow);
        LinearLayout.LayoutParams soundbarLp = new LinearLayout.LayoutParams(-1, -2);
        soundbarLp.topMargin = dp(2);
        soundbarLp.bottomMargin = dp(12);
        content.addView(SofacockpitComposeViewsKt.createSoundbarHomeComposeView(this, vm), soundbarLp);
        return scroll;
    }

    private View page(String heading, String eyebrow, String description) {
        LinearLayout outer = new LinearLayout(this);
        outer.setPadding(dp(20), dp(22), dp(20), dp(20));
        outer.setGravity(Gravity.CENTER);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(24), dp(30), dp(24), dp(30));
        TextView tag = label(eyebrow.toUpperCase(), 12, ACCENT);
        tag.setGravity(Gravity.CENTER);
        card.addView(tag);
        TextView title = label(heading, 26, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(10);
        card.addView(title, titleLp);
        TextView note = label(description, 15, MUTED);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(12);
        card.addView(note, noteLp);
        if (heading.equals("TV-Steuerkreuz")) addDpad(card);
        outer.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return outer;
    }

    private void addDpad(LinearLayout card) {
        int dialSize = dp(284);
        FrameLayout dial = new FrameLayout(this);
        GradientDrawable dialBg = shape(SURFACE_ALT, 200);
        dialBg.setShape(GradientDrawable.OVAL);
        dialBg.setStroke(dp(1), Color.rgb(65, 79, 99));
        dial.setBackground(dialBg);
        LinearLayout.LayoutParams dialLp = new LinearLayout.LayoutParams(dialSize, dialSize);
        dialLp.gravity = Gravity.CENTER_HORIZONTAL;
        dialLp.topMargin = dp(16);
        card.addView(dial, dialLp);
        int arrowSize = dp(82);
        addDialButton(dial, "↑", "oben", arrowSize, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        addDialButton(dial, "↓", "unten", arrowSize, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        addDialButton(dial, "←", "links", arrowSize, Gravity.CENTER_VERTICAL | Gravity.LEFT);
        addDialButton(dial, "→", "rechts", arrowSize, Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        addDialButton(dial, "OK", "OK", dp(88), Gravity.CENTER);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, -2);
        actionLp.topMargin = dp(12);
        card.addView(actions, actionLp);
        actions.addView(actionButton("Zurück", "TV-Verbindung fehlt"), new LinearLayout.LayoutParams(0, dp(58), 1));
        View gap = new View(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(10), dp(1)));
        actions.addView(actionButton("Home", "TV-Verbindung fehlt"), new LinearLayout.LayoutParams(0, dp(58), 1));
        TextView hint = label("TV-Kopplung noch nicht eingerichtet", 12, MUTED);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.topMargin = dp(10);
        card.addView(hint, hintLp);
    }

    private void addDialButton(FrameLayout dial, String text, String description, int size, int gravity) {
        TextView button = actionButton(text, "TV-Verbindung fehlt");
        GradientDrawable circle = shape(Color.rgb(48, 58, 72), 100);
        circle.setShape(GradientDrawable.OVAL);
        circle.setStroke(dp(1), Color.rgb(79, 96, 118));
        button.setBackground(circle);
        button.setTextSize(text.equals("OK") ? 15 : 26);
        button.setContentDescription(description);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, gravity);
        int inset = dp(10);
        lp.setMargins(inset, inset, inset, inset);
        dial.addView(button, lp);
    }

    private void deviceHeader(LinearLayout parent, String name, String status) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(12);
        rowLp.bottomMargin = dp(10);
        parent.addView(row, rowLp);
        TextView title = label(name, 12, MUTED);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(label(status, 13, MUTED));
    }

    private LinearLayout section(LinearLayout parent, String name) {
        LinearLayout card = card();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        parent.addView(card, lp);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(name, 12, MUTED);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(heading);
        card.addView(header);
        LinearLayout.LayoutParams innerLp = new LinearLayout.LayoutParams(-1, -2);
        innerLp.topMargin = dp(12);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        card.addView(inner, innerLp);
        card.setTag(inner);
        return new SectionCard(card).content;
    }

    private static class SectionCard { final LinearLayout content; SectionCard(LinearLayout card) { content = (LinearLayout) card.getTag(); } }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(shape(SURFACE, 18));
        return card;
    }

    private TextView actionButton(String text, String message) {
        TextView button = label(text, 14, TEXT);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(shape(SURFACE_ALT, 14));
        button.setOnClickListener(v -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
        return button;
    }

    private TextView pill(String text) {
        TextView view = label(text, 10, MUTED);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), dp(7), dp(9), dp(7));
        view.setBackground(shape(SURFACE_ALT, 30));
        return view;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius * getResources().getDisplayMetrics().density);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateDots(int active) {
        for (int i = 0; i < dots.length; i++) dots[i].setTextColor(i == active ? ACCENT : MUTED);
    }

    private interface PageChanged { void changed(int index); }

    private class Pager extends HorizontalScrollView {
        private float downX, downY;
        private PageChanged pageChanged;
        private final LinearLayout strip;
        Pager() {
            super(MainActivity.this);
            strip = new LinearLayout(MainActivity.this);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            super.addView(strip, new HorizontalScrollView.LayoutParams(-2, -1));
        }
        void addPage(View view) { strip.addView(view, pageParams()); }
        void setOnPageChanged(PageChanged callback) { pageChanged = callback; }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            strip.getLayoutParams().width = width * 3;
            for (int i = 0; i < strip.getChildCount(); i++) {
                strip.getChildAt(i).getLayoutParams().width = width;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = Math.abs(event.getX() - downX);
                float dy = Math.abs(event.getY() - downY);
                if (dx > dy && dx > 12 && inProtectedControl(downX + getScrollX(), downY)) return false;
            }
            return super.onInterceptTouchEvent(event);
        }

        private boolean inProtectedControl(float x, float y) {
            if (getChildCount() == 0 || !(getChildAt(0) instanceof ViewGroup)) return false;
            ViewGroup strip = (ViewGroup) getChildAt(0);
            View home = strip.getChildAt(1);
            if (!(home instanceof ViewGroup)) return false;
            return findProtected((ViewGroup) home, x - pageWidth, y);
        }

        private boolean findProtected(ViewGroup group, float x, float y) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                float left = child.getLeft(), top = child.getTop();
                if (x >= left && x <= child.getRight() && y >= top && y <= child.getBottom()) {
                    if (child instanceof HorizontalScrollView || child instanceof SeekBar) return true;
                    if (child instanceof ViewGroup && findProtected((ViewGroup) child, x - left, y - top)) return true;
                }
            }
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            boolean handled = super.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                post(this::snapToPage);
            }
            return handled;
        }

        private void snapToPage() {
            if (pageWidth <= 0) return;
            int index = Math.max(0, Math.min(2, Math.round((float) getScrollX() / pageWidth)));
            smoothScrollTo(index * pageWidth, 0);
            if (pageChanged != null) pageChanged.changed(index);
        }
    }
}
