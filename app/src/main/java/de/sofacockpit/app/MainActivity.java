package de.sofacockpit.app;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;
import de.tonmaschine.android.MainViewModel;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Build;
import de.tonmaschine.android.model.Connection;
import de.tonmaschine.android.model.DeviceState;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import de.sofacockpit.app.tv.TvRemoteClient;
import de.sofacockpit.app.tv.TvConnectionDialog;
import java.util.ArrayList;
import java.util.List;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ProgressBar;
import android.widget.EditText;
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
    private TvRemoteClient tv;
    private TvConnectionDialog tvDialog;
    private ConnectionBackupManager backups;
    private SharedPreferences connectionOptions;
    private TextView tvConnectionButton, soundConnectionButton;
    private View tvDeviceHeader;
    private TextView tvHomeStatus, tvHomeDetail, tvRemoteStatus, volumeValue;
    private TvIconButton tvPower, tvMute;
    private ProgressBar volumeLevel;
    private LinearLayout streamRow;
    private HorizontalScrollView streamScroll;
    private StreamingShortcuts shortcuts;
    private final List<TextView> tvKeys = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        soundbarModel = new ViewModelProvider(this).get(MainViewModel.class);
        tv = new TvRemoteClient(this, this::renderTvState);
        shortcuts = new StreamingShortcuts(this);
        connectionOptions = getSharedPreferences("connection_options", MODE_PRIVATE);
        backups = new ConnectionBackupManager(this, tv, soundbarModel, shortcuts, connectionOptions, () -> {
            renderStreamingShortcuts();
            if (tvDialog != null) tvDialog.reloadAddresses();
        });
        tvDialog = new TvConnectionDialog(this, tv, soundbarModel, connectionOptions, backups::requestExport, backups::requestImport);
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
        // Android 15+ draws behind system bars for targetSdk >= 35.
        // Apply safe bounds once to the common parent of all three pages.
        SystemBarInsets.install(getWindow(), shell);
        setContentView(shell);

        pager.addPage(SofacockpitComposeViewsKt.createTonmaschineComposePage(this, soundbarModel, tvDialog::open));
        pager.addPage(homePage(soundbarModel));
        pager.addPage(page("TV-Steuerkreuz", "Fernbedienung", "Nicht verbunden"));
        renderTvState(tv.state());
        SofacockpitComposeViewsKt.observeSoundbarConnection(this, soundbarModel, this::renderSoundConnection);
        renderSoundConnection();
        if (savedInstanceState == null && connectionOptions.getBoolean("auto_connect", false)) {
            // Opt-in only. No power command, wake packet, or automatic pairing.
            if (Build.VERSION.SDK_INT < 37 || checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (tv.paired() && !tv.lastAddress().isEmpty()) tv.start(tv.lastAddress(), false);
                if (!soundbarModel.getIp().getValue().isEmpty() &&
                        soundbarModel.getDevice().getValue().getConnection() == Connection.DISCONNECTED) soundbarModel.connect();
            }
        }
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
        TextView title = label("Verbindung", 21, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        tvConnectionButton = connectionButton("TV");
        soundConnectionButton = connectionButton("Sound");
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(dp(66), dp(48));
        tvLp.rightMargin = dp(8);
        titleRow.addView(tvConnectionButton, tvLp);
        titleRow.addView(soundConnectionButton, new LinearLayout.LayoutParams(dp(80), dp(48)));
        content.addView(titleRow);
        LinearLayout.LayoutParams titleLp = (LinearLayout.LayoutParams) titleRow.getLayoutParams();
        titleLp.bottomMargin = dp(12);
        titleRow.setLayoutParams(titleLp);

        tvHomeStatus = deviceHeader(content, "FERNSEHER", "Nicht verbunden");
        LinearLayout tvHeader = (LinearLayout) tvHomeStatus.getParent();
        tvDeviceHeader = tvHeader;
        tvHeader.setOnClickListener(v -> tvDialog.open());
        tvHomeStatus.setTextColor(ACCENT);
        tvHomeStatus.setContentDescription("Fernseher verbinden");
        tvMute = new TvIconButton(false, KeyEvent.KEYCODE_VOLUME_MUTE);
        tvPower = new TvIconButton(true, KeyEvent.KEYCODE_POWER);
        tvHeader.addView(tvMute, new LinearLayout.LayoutParams(dp(48), dp(48)));
        tvHeader.addView(tvPower, new LinearLayout.LayoutParams(dp(48), dp(48)));
        tvHomeDetail = label("", 12, MUTED);
        tvHomeDetail.setVisibility(View.GONE);
        tvHomeDetail.setOnClickListener(v -> tvDialog.open());
        content.addView(tvHomeDetail);

        LinearLayout streaming = section(content, "STREAMING");
        LinearLayout streamHeader = (LinearLayout) ((LinearLayout) streaming.getParent()).getChildAt(0);
        View heading = streamHeader.getChildAt(0);
        heading.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView add = actionButton("+", "");
        add.setContentDescription("Streaming-Dienst hinzufügen");
        add.setOnClickListener(v -> addStreamingShortcut());
        streamHeader.addView(add, new LinearLayout.LayoutParams(dp(44), dp(44)));
        streamScroll = new HorizontalScrollView(this);
        streamScroll.setHorizontalScrollBarEnabled(false);
        streamScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        streamRow = new LinearLayout(this);
        streamRow.setOrientation(LinearLayout.HORIZONTAL);
        streamScroll.addView(streamRow);
        streaming.addView(streamScroll);
        streamScroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if (r-l != or-ol && r>l) sizeStreamingTiles();
        });
        renderStreamingShortcuts();

        LinearLayout volume = section(content, "LAUTSTÄRKE");
        LinearLayout volumeRow = new LinearLayout(this);
        volumeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView quieter = tvButton("−", KeyEvent.KEYCODE_VOLUME_DOWN);
        quieter.setContentDescription("Leiser");
        volumeRow.addView(quieter, new LinearLayout.LayoutParams(dp(54), dp(52)));
        LinearLayout volumeReadout = new LinearLayout(this);
        volumeReadout.setOrientation(LinearLayout.VERTICAL);
        volumeReadout.setGravity(Gravity.CENTER);
        volumeLevel = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        volumeLevel.setIndeterminate(false);
        volumeLevel.setContentDescription("Vom TV gemeldeter Lautstärkepegel");
        volumeReadout.addView(volumeLevel, new LinearLayout.LayoutParams(-1, dp(12)));
        volumeValue = label("Pegel unbekannt", 12, MUTED);
        volumeValue.setGravity(Gravity.CENTER);
        volumeReadout.addView(volumeValue);
        LinearLayout.LayoutParams volumeLp = new LinearLayout.LayoutParams(0, -2, 1);
        volumeLp.leftMargin = dp(8);
        volumeLp.rightMargin = dp(8);
        volumeRow.addView(volumeReadout, volumeLp);
        TextView louder = tvButton("+", KeyEvent.KEYCODE_VOLUME_UP);
        louder.setContentDescription("Lauter");
        volumeRow.addView(louder, new LinearLayout.LayoutParams(dp(54), dp(52)));
        volume.addView(volumeRow);
        LinearLayout.LayoutParams soundbarLp = new LinearLayout.LayoutParams(-1, -2);
        soundbarLp.topMargin = dp(2);
        soundbarLp.bottomMargin = dp(12);
        content.addView(SofacockpitComposeViewsKt.createSoundbarHomeComposeView(this, vm), soundbarLp);
        return scroll;
    }

    private void sizeStreamingTiles() {
        int width = streamScroll.getWidth();
        int tileWidth = width > 0 ? Math.max(dp(60), (width - 3 * dp(8)) / 4) : dp(80);
        for (int i = 0; i < streamRow.getChildCount(); i++) {
            View tile = streamRow.getChildAt(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileWidth, dp(58));
            lp.rightMargin = i + 1 < streamRow.getChildCount() ? dp(8) : 0;
            tile.setLayoutParams(lp);
        }
    }
    private void renderStreamingShortcuts() {
        streamRow.removeAllViews();
        for (StreamingShortcuts.Entry entry : shortcuts.entries) {
            TextView tile = actionButton(entry.name, "");
            tile.setTextSize(12);
            tile.setPadding(dp(3), 0, dp(3), 0);
            tile.setContentDescription(entry.name + " starten; lange drücken zum Bearbeiten");
            tile.setOnClickListener(v -> {
                TvRemoteClient.State state = tv.state();
                if (!state.connected() || !state.apps) {
                    Toast.makeText(this, !state.connected() ? "Zuerst TV verbinden." :
                        "TV meldet keine App-Link-Unterstützung.", Toast.LENGTH_LONG).show();
                    return;
                }
                tv.launchApp(entry.link, entry.name);
                if (entry.link.startsWith("market://"))
                    Toast.makeText(this, "Experimenteller App-Start; ggf. öffnet sich der Play Store.", Toast.LENGTH_LONG).show();
            });
            tile.setOnLongClickListener(v -> { shortcutOptions(entry); return true; });
            streamRow.addView(tile);
        }
        if (shortcuts.entries.isEmpty()) {
            TextView empty = actionButton("+", "");
            empty.setContentDescription("Ersten Dienst hinzufügen");
            empty.setOnClickListener(v -> addStreamingShortcut());
            streamRow.addView(empty);
        }
        sizeStreamingTiles();
    }
    private void saveStreamingShortcuts() {
        shortcuts.save();
        renderStreamingShortcuts();
    }
    private void shortcutOptions(StreamingShortcuts.Entry entry) {
        int index = shortcuts.entries.indexOf(entry);
        if (index < 0) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Name / App-Link bearbeiten"); actions.add(() -> editStreamingShortcut(entry));
        if (index > 0) {
            labels.add("Nach links verschieben");
            actions.add(() -> { java.util.Collections.swap(shortcuts.entries, index, index - 1); saveStreamingShortcuts(); });
        }
        if (index + 1 < shortcuts.entries.size()) {
            labels.add("Nach rechts verschieben");
            actions.add(() -> { java.util.Collections.swap(shortcuts.entries, index, index + 1); saveStreamingShortcuts(); });
        }
        labels.add("Aus Leiste entfernen");
        actions.add(() -> { shortcuts.entries.remove(entry); saveStreamingShortcuts(); });
        new AlertDialog.Builder(this).setTitle(entry.name)
            .setItems(labels.toArray(new String[0]), (d, i) -> actions.get(i).run())
            .setNegativeButton("Schließen", null).show();
    }
    private void addStreamingShortcut() {
        if (shortcuts.entries.size() >= 50) {
            Toast.makeText(this, "Maximal 50 Schnellwahltasten.", Toast.LENGTH_SHORT).show(); return;
        }
        List<StreamingShortcuts.Entry> choices = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (StreamingShortcuts.Entry entry : StreamingShortcuts.CATALOG) {
            if (!shortcuts.contains(entry)) { choices.add(entry); names.add(entry.name); }
        }
        names.add("Eigene TV-App hinzufügen …");
        new AlertDialog.Builder(this).setTitle("Dienst hinzufügen")
            .setItems(names.toArray(new String[0]), (d, i) -> {
                if (i == choices.size()) editStreamingShortcut(null);
                else { shortcuts.entries.add(choices.get(i)); saveStreamingShortcuts(); }
            })
            .setNegativeButton("Schließen", null).show();
    }
    private void editStreamingShortcut(StreamingShortcuts.Entry old) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(12));
        form.addView(label("Name der auf dem TV installierten App", 13, TEXT));
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("z. B. Dyn");
        form.addView(name);
        form.addView(label("App-Link oder Android-Paketname", 13, TEXT));
        EditText link = new EditText(this);
        link.setSingleLine(true);
        link.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        link.setHint("https://… oder com.beispiel.app");
        form.addView(link);
        form.addView(label("Keine automatische App-Liste vom TV verfügbar. Paketnamen können nur den Play Store öffnen. Der TV muss den Link unterstützen.", 12, MUTED));
        if (old != null) { name.setText(old.name); link.setText(old.link); }
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(old == null ? "Eigene TV-App" : "Dienst bearbeiten")
            .setView(form).setNegativeButton("Abbrechen", null).setPositiveButton("Speichern", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                StreamingShortcuts.Entry entry = new StreamingShortcuts.Entry(name.getText().toString(), link.getText().toString());
                for (StreamingShortcuts.Entry other : shortcuts.entries) {
                    if (other != old && (other.name.equalsIgnoreCase(entry.name) || other.link.equals(entry.link)))
                        throw new IllegalArgumentException("Dieser Name oder Link ist bereits vorhanden.");
                }
                if (old == null) shortcuts.entries.add(entry);
                else shortcuts.entries.set(shortcuts.entries.indexOf(old), entry);
                saveStreamingShortcuts(); dialog.dismiss();
            } catch (IllegalArgumentException ex) { link.setError(ex.getMessage()); }
        });
    }

    private final class TvIconButton extends TextView {
        private final boolean power;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        TvIconButton(boolean power, int key) {
            super(MainActivity.this);
            this.power = power;
            setBackground(shape(SURFACE_ALT, 14));
            setTextColor(TEXT);
            setContentDescription(power ? "Fernseher ein oder aus" : "Stummschaltung");
            setOnClickListener(v -> tv.sendKey(key));
            tvKeys.add(this);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.translate(getWidth()/2f, getHeight()/2f);
            float unit = dp(24)/24f;
            canvas.scale(unit, unit);
            paint.setColor(getCurrentTextColor());
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (power) {
                canvas.drawArc(-8, -8, 8, 8, -45, 270, false, paint);
                canvas.drawLine(0, -11, 0, 0, paint);
            } else {
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(-11,-3); path.lineTo(-7,-3); path.lineTo(-2,-8);
                path.lineTo(-2,8); path.lineTo(-7,3); path.lineTo(-11,3); path.close();
                canvas.drawPath(path, paint);
                canvas.drawLine(3,-4,10,4,paint);
                canvas.drawLine(10,-4,3,4,paint);
            }
            canvas.restore();
        }
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
        if (heading.equals("TV-Steuerkreuz")) {
            tvRemoteStatus = note;
            note.setOnClickListener(v -> tvDialog.open());
            note.setContentDescription("TV-Verbindungsstatus und Einstellungen");
            addDpad(card);
        }
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
        addDialButton(dial, "↑", "oben", arrowSize, Gravity.TOP | Gravity.CENTER_HORIZONTAL, KeyEvent.KEYCODE_DPAD_UP);
        addDialButton(dial, "↓", "unten", arrowSize, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, KeyEvent.KEYCODE_DPAD_DOWN);
        addDialButton(dial, "←", "links", arrowSize, Gravity.CENTER_VERTICAL | Gravity.LEFT, KeyEvent.KEYCODE_DPAD_LEFT);
        addDialButton(dial, "→", "rechts", arrowSize, Gravity.CENTER_VERTICAL | Gravity.RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT);
        addDialButton(dial, "OK", "OK", dp(88), Gravity.CENTER, KeyEvent.KEYCODE_DPAD_CENTER);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, -2);
        actionLp.topMargin = dp(12);
        card.addView(actions, actionLp);
        actions.addView(tvButton("Zurück", KeyEvent.KEYCODE_BACK), new LinearLayout.LayoutParams(0, dp(58), 1));
        View gap = new View(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(10), dp(1)));
        actions.addView(tvButton("Home", KeyEvent.KEYCODE_HOME), new LinearLayout.LayoutParams(0, dp(58), 1));
        TextView hint = actionButton("TV-Verbindung", "");
        hint.setOnClickListener(v -> tvDialog.open());
        hint.setMinHeight(dp(46));
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.topMargin = dp(10);
        card.addView(hint, hintLp);
    }

    private void addDialButton(FrameLayout dial, String text, String description, int size, int gravity, int key) {
        TextView button = tvButton(text, key);
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

    private TextView deviceHeader(LinearLayout parent, String name, String status) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(12);
        rowLp.bottomMargin = dp(10);
        parent.addView(row, rowLp);
        TextView title = label(name, 12, MUTED);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView statusView = label(status, 13, MUTED);
        statusView.setPadding(dp(8), dp(8), 0, dp(8));
        row.addView(statusView);
        return statusView;
    }

    private TextView tvButton(String text, int key) {
        TextView button = actionButton(text, "");
        button.setOnClickListener(v -> tv.sendKey(key));
        tvKeys.add(button);
        return button;
    }

    private TextView connectionButton(String name) {
        TextView button = actionButton(name, "");
        button.setTextSize(15);
        button.setFocusable(true);
        button.setOnClickListener(v -> tvDialog.open());
        return button;
    }
    private void paintConnection(TextView button, String name, boolean connected, boolean connecting) {
        if (button == null) return;
        button.setTextColor(connected ? Color.rgb(66,215,146) :
            (connecting ? Color.rgb(240,195,76) : Color.rgb(235,83,83)));
        button.setContentDescription(name + " – " + (connected ? "Verbunden" :
            (connecting ? "Verbindungsaufbau" : "Nicht verbunden")) + "; Verbindungen öffnen");
    }
    private void renderSoundConnection() {
        DeviceState state = soundbarModel.getDevice().getValue();
        paintConnection(soundConnectionButton, "Sound", state.getConnection() == Connection.CONNECTED,
            state.getConnection() == Connection.CONNECTING && state.getError() == null);
        if (tvDialog != null) tvDialog.renderSound();
    }
    private void renderTvState(TvRemoteClient.State state) {
        boolean ready = state.connected() && state.keys;
        paintConnection(tvConnectionButton, "TV", state.connected(), state.phase == TvRemoteClient.Phase.CONNECTING ||
            state.phase == TvRemoteClient.Phase.PAIRING || state.phase == TvRemoteClient.Phase.CODE ||
            state.phase == TvRemoteClient.Phase.VERIFYING);
        if (tvDeviceHeader != null) tvDeviceHeader.setVisibility(state.connected() ? View.VISIBLE : View.GONE);
        if (tvHomeStatus != null) {
            String shortStatus;
            switch (state.phase) {
                case READY: shortStatus = state.powerOn == null ? "Verbunden" :
                    (state.powerOn ? "Ein" : "Standby"); break;
                case CONNECTING: shortStatus = "Verbinde …"; break;
                case PAIRING: shortStatus = "Kopple …"; break;
                case CODE: shortStatus = "Code eingeben"; break;
                case VERIFYING: shortStatus = "Prüfe Code …"; break;
                case ERROR: shortStatus = "Verbindungsfehler"; break;
                default: shortStatus = "Nicht verbunden";
            }
            tvHomeStatus.setText(shortStatus);
            tvHomeStatus.setTextColor(state.phase == TvRemoteClient.Phase.READY ? Color.rgb(66, 215, 146) : ACCENT);
            tvHomeStatus.setContentDescription(shortStatus + " – Fernseher verbinden");
        }
        if (tvHomeDetail != null) {
            boolean showDetail = state.phase == TvRemoteClient.Phase.ERROR ||
                (state.connected() && !"Verbunden".equals(state.detail));
            tvHomeDetail.setText(state.detail);
            tvHomeDetail.setVisibility(showDetail ? View.VISIBLE : View.GONE);
        }
        if (tvRemoteStatus != null) {
            tvRemoteStatus.setText(state.detail);
            tvRemoteStatus.setContentDescription(state.detail + " – TV-Verbindung öffnen");
        }
        for (TextView key : tvKeys) {
            key.setEnabled(ready);
            key.setAlpha(ready ? 1f : .42f);
        }
        if (tvPower != null) {
            tvPower.setVisibility(state.connected() ? View.VISIBLE : View.GONE);
            tvPower.setTextColor(Boolean.TRUE.equals(state.powerOn) ? Color.rgb(66,215,146) : TEXT);
            tvPower.setContentDescription(state.powerOn == null ? "Fernseher ein oder aus, Zustand unbekannt" :
                (state.powerOn ? "Fernseher ausschalten" : "Fernseher einschalten"));
            tvPower.invalidate();
        }
        if (tvMute != null) {
            tvMute.setVisibility(state.connected() ? View.VISIBLE : View.GONE);
            tvMute.setTextColor(Boolean.TRUE.equals(state.muted) ? Color.rgb(66,215,146) : TEXT);
            tvMute.setContentDescription(state.muted == null ? "Stummschaltung, Zustand unbekannt" :
                (state.muted ? "Stummschaltung aufheben" : "Stummschaltung einschalten"));
            tvMute.invalidate();
        }
        if (volumeLevel != null) {
            boolean reported = state.connected() && state.volumeLevel != null && state.volumeMax != null;
            volumeLevel.setVisibility(reported ? View.VISIBLE : View.GONE);
            if (reported) {
                volumeLevel.setMax(state.volumeMax);
                volumeLevel.setProgress(state.volumeLevel);
                volumeValue.setText(state.volumeLevel + " / " + state.volumeMax +
                    (Boolean.TRUE.equals(state.muted) ? " · Stumm" : ""));
            } else volumeValue.setText("Pegel unbekannt");
        }
        if (tvDialog != null) tvDialog.render(state);
    }

    @Override protected void onDestroy() {
        if (tvDialog != null) tvDialog.close();
        if (backups != null) backups.close();
        if (tv != null) tv.close();
        super.onDestroy();
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
