package de.sofacockpit.app;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;
import de.tonmaschine.android.MainViewModel;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.os.Build;
import de.tonmaschine.android.model.Connection;
import de.tonmaschine.android.model.DeviceState;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import androidx.compose.ui.platform.ComposeView;
import de.sofacockpit.app.theme.*;
import de.sofacockpit.app.update.UpdateManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import de.sofacockpit.app.tv.TvRemoteClient;
import de.sofacockpit.app.tv.TvConnectionIndicator;
import de.sofacockpit.app.tv.TvConnectionDialog;
import de.sofacockpit.app.tv.AppLaunchTest;
import de.sofacockpit.app.tv.TclAppLauncher;
import java.util.ArrayList;
import java.util.List;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ProgressBar;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;

public class MainActivity extends ComponentActivity {
    private int BG, SURFACE, SURFACE_ALT, TEXT, MUTED, ACCENT;
    private ThemeStore themes;
    private ThemePack theme;
    private ThemePicker themePicker;
    private UpdateManager updates;
    private ConnectionRefresh connectionRefresh;
    private final List<NativePullRefreshLayout> refreshLayouts = new ArrayList<>();
    private LinearLayout shell;
    private ThemedDial remoteDial;
    private final CockpitNavigation navigation = new CockpitNavigation();
    private final View[] categoryViews = new View[3];
    private final SwipeCategoryPager[] categoryPagers = new SwipeCategoryPager[3];
    private final TextView[] tabs = new TextView[3];
    private final TextView[] dots = new TextView[3];
    private LinearLayout footer;
    private TextView footerPageName;
    private PopupMenu quickMenu;
    private AlertDialog appLaunchTestDialog;
    private MainViewModel soundbarModel;
    private TvRemoteClient tv;
    private TvConnectionDialog tvDialog;
    private ConnectionBackupManager backups;
    private SharedPreferences connectionOptions;
    private TextView tvConnectionButton, soundConnectionButton;
    private View tvDeviceHeader;
    private TextView tvHomeStatus, tvHomeDetail, tvRemoteStatus;
    private TvIconButton tvPower, tvMute;
    private final List<VolumeControl> volumeControls = new ArrayList<>();
    private LinearLayout streamRow;
    private HorizontalScrollView streamScroll;
    private LinearLayout sourcesAppList;
    private TextView sourcesStatus, systemStatus;
    private StreamingShortcuts shortcuts;
    private final TvAppInventory tvAppInventory = new TvAppInventory();
    private boolean includeSystemApps;
    private boolean appInventoryLoading;
    private boolean wasTvInventoryConnected;
    private String appInventoryError = "";
    private TextView appsInventoryStatus;
    private final List<TextView> tvKeys = new ArrayList<>();
    private final List<TextView> hdmiButtons = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        soundbarModel = new ViewModelProvider(this).get(MainViewModel.class);
        tv = new TvRemoteClient(this, this::renderTvState);
        shortcuts = new StreamingShortcuts(this);
        connectionOptions = getSharedPreferences("connection_options", MODE_PRIVATE);
        themes = new ThemeStore(this, connectionOptions);
        theme = themes.selected();
        themePicker = new ThemePicker(this, themes, this::applySelectedTheme);
        backups = new ConnectionBackupManager(this, tv, soundbarModel, shortcuts, connectionOptions, themes, () -> {
            applySelectedTheme();
            renderStreamingShortcuts();
            if (tvDialog != null) tvDialog.reloadAddresses();
        });
        tvDialog = new TvConnectionDialog(this, tv, soundbarModel, connectionOptions, backups::requestExport, backups::requestImport, () -> theme);
        updates = new UpdateManager(this, () -> theme);
        connectionRefresh = new ConnectionRefresh(tv, soundbarModel, updates,
            () -> Build.VERSION.SDK_INT < 37 || checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
            this::renderRefreshing,
            message -> { if (!isFinishing() && !isDestroyed() && hasWindowFocus())
                Toast.makeText(this, message, Toast.LENGTH_LONG).show(); });
        int savedCategory = savedInstanceState == null ? 0 : savedInstanceState.getInt("navigation_category", 0);
        int savedPage = savedInstanceState == null ? 0 : savedInstanceState.getInt("navigation_page", 0);
        if (!CockpitNavigation.valid(savedCategory, savedPage)) { savedCategory = 0; savedPage = 0; }
        buildShell(savedCategory, savedPage);
        SofacockpitComposeViewsKt.observeSoundbarConnection(this, soundbarModel, this::renderSoundConnection);
        if (savedInstanceState == null && connectionOptions.getBoolean("auto_connect", false)) {
            // Opt-in only. No power command, wake packet, or automatic pairing.
            if (Build.VERSION.SDK_INT < 37 || checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (tv.paired() && !tv.lastAddress().isEmpty()) tv.startPassive(tv.lastAddress());
                if (!soundbarModel.getIp().getValue().isEmpty() &&
                        soundbarModel.getDevice().getValue().getConnection() == Connection.DISCONNECTED) soundbarModel.connect();
            }
        }
        if (savedInstanceState == null) updates.startAutomaticCheck();
    }

    private void applySelectedTheme() {
        ThemePack next = themes.selected();
        if (next == theme) return;
        theme = next;
        buildShell(navigation.category(), navigation.page());
    }

    private void disposeCompose(View view) {
        if (view instanceof ComposeView) { ((ComposeView)view).disposeComposition(); return; }
        if (view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) disposeCompose(group.getChildAt(i));
        }
    }

    /** Rebuild presentation only; keep the Activity, TV client and sound ViewModel. */
    private void buildShell(int categoryToShow, int pageToShow) {
        if (quickMenu != null) quickMenu.dismiss();
        if (shell != null) {
            // Detach first so disposed ComposeViews cannot recreate their compositions in layout.
            View old=shell;
            setContentView(new FrameLayout(this));
            disposeCompose(old);
        }
        CockpitTheme t=theme.theme;
        BG=t.background; SURFACE=t.panel; SURFACE_ALT=t.control; TEXT=t.text; MUTED=t.muted; ACCENT=t.accent;
        tvKeys.clear();
        hdmiButtons.clear();
        refreshLayouts.clear();
        volumeControls.clear();
        tvConnectionButton=soundConnectionButton=tvHomeStatus=tvHomeDetail=tvRemoteStatus=sourcesStatus=systemStatus=null;
        tvDeviceHeader=null; tvPower=tvMute=null; remoteDial=null;
        streamRow=sourcesAppList=null; streamScroll=null;
        appsInventoryStatus=null;
        java.util.Arrays.fill(categoryViews,null);
        java.util.Arrays.fill(categoryPagers,null);
        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);

        shell.addView(createTabs(), new LinearLayout.LayoutParams(-1, -2));
        FrameLayout pages = new FrameLayout(this);
        shell.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        categoryViews[0] = refreshPage(homePage(soundbarModel), CockpitNavigation.START);
        for (int category = 1; category <= 2; category++) {
            final int group = category;
            SwipeCategoryPager pager = new SwipeCategoryPager(this, index -> onPageChanged(group, index));
            categoryPagers[group] = pager;
            categoryViews[group] = pager;
            if (group == CockpitNavigation.TV) {
                pager.addPage(refreshPage(remotePage(), group));
                pager.addPage(refreshPage(sourcesPage(), group));
                pager.addPage(refreshPage(systemPage(), group));
            } else {
                for (int section = 1; section <= 3; section++)
                    pager.addPage(SofacockpitComposeViewsKt.createTonmaschineComposePage(
                        this, soundbarModel, tvDialog::open, section, theme, connectionRefresh));
            }
        }
        for (View view : categoryViews) pages.addView(view, new FrameLayout.LayoutParams(-1, -1));
        footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(theme.theme.paddingDp),0,dp(theme.theme.paddingDp),0);
        footerPageName = label("", 12, MUTED);
        footerPageName.setMaxLines(1);
        footerPageName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        footerPageName.setGravity(Gravity.CENTER_VERTICAL);
        footer.addView(footerPageName,new LinearLayout.LayoutParams(0,-1,1f));
        for (int i = 0; i < dots.length; i++) {
            final int index = i;
            TextView dot = label("●", 12, MUTED);
            dot.setGravity(Gravity.CENTER);
            dot.setFocusable(true);
            dot.setOnClickListener(v -> selectScreen(navigation.category(), index));
            dots[i] = dot;
            footer.addView(dot, new LinearLayout.LayoutParams(dp(48), dp(44)));
        }
        shell.addView(footer, new LinearLayout.LayoutParams(-1, dp(44)));
        SystemBarInsets.install(getWindow(), shell);
        setContentView(shell);
        selectScreen(categoryToShow, pageToShow);
        renderTvState(tv.state());
        renderSoundConnection();
        renderRefreshing(Boolean.TRUE.equals(connectionRefresh.getRefreshing().getValue()));
    }

    private View refreshPage(View content, int category) {
        NativePullRefreshLayout layout = new NativePullRefreshLayout(this, content, ACCENT, SURFACE,
            () -> connectionRefresh.request(category));
        refreshLayouts.add(layout);
        return layout;
    }

    private void renderRefreshing(Boolean refreshing) {
        for (NativePullRefreshLayout layout : refreshLayouts)
            layout.setRefreshing(Boolean.TRUE.equals(refreshing));
    }

    private View createTabs() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(4));
        for (int category = 0; category < tabs.length; category++) {
            final int group = category;
            TextView tab = label(CockpitNavigation.tabLabel(group) + "  ▾", 15, TEXT);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(ThemeArt.typeface(theme.theme, Typeface.BOLD));
            tab.setFocusable(true);
            tab.setOnClickListener(v -> selectScreen(group, 0));
            tab.setOnLongClickListener(v -> {
                if (group == 0) showStartMenu(v);
                else showQuickMenu(group, v);
                return true; // native long-press haptic feedback
            });
            tabs[group] = tab;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            if (group != 0) lp.leftMargin = dp(6);
            row.addView(tab, lp);
        }
        return row;
    }

    private void showStartMenu(View anchor) {
        if (quickMenu != null) quickMenu.dismiss();
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.CENTER);
        String[] items={"Design auswählen","Software-Updates","Verbindungen","Über Sofacockpit","Verbindungen aktualisieren","Streaming-Start testen"};
        for (int i=0;i<items.length;i++) popup.getMenu().add(0,i+1,i,items[i]);
        popup.setOnMenuItemClickListener(item -> {
            switch(item.getItemId()) {
                case 1: themePicker.show(); return true;
                case 2: updates.showSettings(); return true;
                case 3: tvDialog.open(); return true;
                case 4:
                    new AlertDialog.Builder(this).setTitle("Sofacockpit")
                        .setMessage("Version "+updates.versionName()+"\n\nTV- und Soundbar-Steuerung.\nÖffentliches Projekt: github.com/sschermer/Sofacockpit")
                        .setPositiveButton("Schließen",null).show();
                    return true;
                case 5: connectionRefresh.request(navigation.category()); return true;
                case 6: showAppLaunchTestDialog(); return true;
                default: return false;
            }
        });
        popup.setOnDismissListener(menu -> { if(quickMenu==menu) quickMenu=null; });
        quickMenu=popup;popup.show();
    }

    private void showAppLaunchTestDialog() {
        if (isFinishing() || isDestroyed() ||
                (appLaunchTestDialog != null && appLaunchTestDialog.isShowing())) return;

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(14), dp(18), dp(12));
        body.setBackgroundColor(BG);
        TextView title = label("Streaming-Start testen", 21, TEXT);
        title.setTypeface(ThemeArt.typeface(theme.theme, Typeface.BOLD));
        body.addView(title);
        TextView explanation = label("Experimenteller vollständiger Play-Store-Aufruf. " +
            "Nach jedem Test werden TV-Rückmeldungen fünf Sekunden beobachtet. " +
            "Bitte am TV prüfen, ob die App startet. Jeder Test beginnt nur nach Antippen.", 13, MUTED);
        explanation.setPadding(0, dp(8), 0, dp(10));
        body.addView(explanation);
        TextView availability = label("", 13, MUTED);
        availability.setPadding(0, 0, 0, dp(6));
        body.addView(availability);

        AppLaunchTest.Target[] targets = {AppLaunchTest.Target.YOUTUBE, AppLaunchTest.Target.DYN};
        Button[] buttons = new Button[targets.length];
        TextView[] results = new TextView[targets.length];
        for (int i = 0; i < targets.length; i++) {
            Button button = appLaunchTestButton(targets[i].label + " testen");
            buttons[i] = button;
            LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, -2);
            buttonLp.topMargin = dp(10);
            body.addView(button, buttonLp);
            TextView result = label("Noch nicht getestet.", 13, TEXT);
            result.setPadding(0, dp(8), 0, dp(12));
            result.setTextIsSelectable(true);
            result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            results[i] = result;
            body.addView(result, new LinearLayout.LayoutParams(-1, -2));
        }

        Button copy = appLaunchTestButton("Ergebnisse kopieren");
        body.addView(copy, new LinearLayout.LayoutParams(-1, -2));
        TextView copyFeedback = label("", 12, MUTED);
        copyFeedback.setPadding(0, dp(6), 0, 0);
        copyFeedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(copyFeedback);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this).setView(scroll)
            .setNegativeButton("Schließen", null).create();
        appLaunchTestDialog = dialog;
        Runnable render = () -> {
            if (appLaunchTestDialog != dialog || !dialog.isShowing()) return;
            boolean busy = tv.appLaunchTestBusy();
            boolean allowed = tv.canTestAppLaunch();
            String status = busy ? "Test läuft …" : allowed ? "Bereit. Gewünschten Test antippen." :
                !tv.state().connected() ? "Für einen Test zuerst den Fernseher verbinden." :
                !tv.state().apps ? "Der TV meldet keine App-Link-Unterstützung." :
                "Test derzeit nicht möglich. TV-Verbindung prüfen.";
            if (!status.contentEquals(availability.getText())) availability.setText(status);
            for (int i = 0; i < targets.length; i++) {
                buttons[i].setEnabled(!busy && allowed);
                buttons[i].setAlpha(!busy && allowed ? 1f : .42f);
                AppLaunchTest.Result result = tv.appLaunchTestResult(targets[i]);
                String message = result == null ? "Noch nicht getestet." : result.message;
                if (!message.contentEquals(results[i].getText())) results[i].setText(message);
            }
        };
        for (int i = 0; i < targets.length; i++) {
            AppLaunchTest.Target target = targets[i];
            buttons[i].setOnClickListener(v -> {
                if (!tv.appLaunchTestBusy() && tv.canTestAppLaunch()) {
                    copyFeedback.setText("");
                    tv.testAppLaunch(target);
                }
                render.run();
            });
        }
        copy.setOnClickListener(v -> {
            StringBuilder report = new StringBuilder("Sofacockpit ").append(updates.versionName())
                .append(" – Streaming-Start testen\n");
            for (AppLaunchTest.Target target : targets) {
                AppLaunchTest.Result result = tv.appLaunchTestResult(target);
                report.append('\n').append(target.label).append(":\n")
                    .append(result == null ? "Noch nicht getestet." : result.message).append('\n');
            }
            report.append("Der tatsächliche App-Start muss am TV geprüft werden.");
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) {
                copyFeedback.setText("Zwischenablage nicht verfügbar.");
                return;
            }
            try {
                clipboard.setPrimaryClip(ClipData.newPlainText("Sofacockpit Streaming-Test", report));
                copyFeedback.setText("Beide Ergebnisse kopiert.");
            } catch (RuntimeException ex) {
                copyFeedback.setText("Ergebnisse konnten nicht kopiert werden.");
            }
        });
        dialog.setOnDismissListener(d -> {
            if (appLaunchTestDialog != dialog) return;
            appLaunchTestDialog = null;
            tv.setAppLaunchTestListener(null);
            tv.stopAppLaunchTest();
        });
        dialog.show();
        Button close = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        close.setAllCaps(false);
        close.setTextColor(ACCENT);
        close.setMinHeight(dp(48));
        if (dialog.getWindow() != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(20), dp(540));
            dialog.getWindow().setLayout(width, -2);
        }
        tv.setAppLaunchTestListener(render);
        render.run();
    }

    private Button appLaunchTestButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(TEXT);
        button.setTextSize(14 * theme.theme.fontScale);
        button.setTypeface(ThemeArt.typeface(theme.theme, Typeface.BOLD));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setBackground(shape(SURFACE_ALT, 14));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        return button;
    }

    private void closeAppLaunchTestDialog() {
        AlertDialog dialog = appLaunchTestDialog;
        appLaunchTestDialog = null;
        if (tv != null) {
            tv.setAppLaunchTestListener(null);
            tv.stopAppLaunchTest();
        }
        if (dialog != null) dialog.dismiss();
    }

    private void showQuickMenu(int category, View anchor) {
        if (quickMenu != null) quickMenu.dismiss();
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.CENTER);
        for (int i = 0; i < CockpitNavigation.pageCount(category); i++)
            popup.getMenu().add(0, i + 1, i, CockpitNavigation.pageLabel(category, i));
        popup.setOnMenuItemClickListener(item -> {
            selectScreen(category, item.getItemId() - 1);
            return true;
        });
        popup.setOnDismissListener(menu -> { if (quickMenu == menu) quickMenu = null; });
        quickMenu = popup;
        popup.show();
    }

    private void selectScreen(int category, int page) {
        if (!CockpitNavigation.valid(category, page)) return;
        navigation.select(category, page);
        for (int i = 0; i < categoryViews.length; i++) {
            if (i != category && categoryPagers[i] != null) categoryPagers[i].finishGesture();
            categoryViews[i].setVisibility(i == category ? View.VISIBLE : View.GONE);
        }
        if (categoryPagers[category] != null) categoryPagers[category].select(page);
        renderNavigation();
        if (category == CockpitNavigation.TV && page == 1) refreshTvAppInventory();
    }

    private void onPageChanged(int category, int page) {
        if (navigation.category() != category) return;
        navigation.select(category, page);
        renderNavigation();
        if (category == CockpitNavigation.TV && page == 1) refreshTvAppInventory();
    }

    private void renderNavigation() {
        int category = navigation.category();
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == category;
            tabs[i].setBackground(ThemeArt.surface(theme,selected?theme.theme.selected:SURFACE_ALT,
                theme.theme.cornerDp,getResources().getDisplayMetrics().density,selected,true));
            tabs[i].setTextColor(selected ? ACCENT : MUTED);
            tabs[i].setSelected(selected);
            tabs[i].setContentDescription(CockpitNavigation.tabLabel(i) + (selected ? ", ausgewählt" : "") +
                (i == 0 ? ", lange drücken für App-Menü" : ", lange drücken für Seitenauswahl"));
        }
        footer.setVisibility(category == 0 ? View.GONE : View.VISIBLE);
        footerPageName.setText(category == 0 ? "" : CockpitNavigation.pageLabel(category,navigation.page()));
        for (int i = 0; i < dots.length; i++) {
            boolean selected = i == navigation.page();
            dots[i].setTextColor(selected ? ACCENT : MUTED);
            dots[i].setSelected(selected);
            if (category != 0) dots[i].setContentDescription(CockpitNavigation.pageLabel(category, i) +
                (selected ? ", ausgewählt" : ""));
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("navigation_category", navigation.category());
        state.putInt("navigation_page", navigation.page());
        super.onSaveInstanceState(state);
    }

    private View homePage(MainViewModel vm) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(theme.theme.paddingDp), dp(12), dp(theme.theme.paddingDp), dp(12));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Verbindung", 21, TEXT);
        title.setTypeface(ThemeArt.typeface(theme.theme, Typeface.BOLD));
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
        // Connection is shown by the TV chip; power by its icon.
        tvHomeStatus.setVisibility(View.GONE);
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
        addVolumeControl(volume);
        LinearLayout.LayoutParams soundbarLp = new LinearLayout.LayoutParams(-1, -2);
        soundbarLp.topMargin = dp(2);
        soundbarLp.bottomMargin = dp(12);
        content.addView(SofacockpitComposeViewsKt.createSoundbarHomeComposeView(this, vm, theme), soundbarLp);
        return scroll;
    }

    /** Both screens use the same live model and TV-relative commands (CEC forwarding). */
    private void addVolumeControl(LinearLayout parent) {
        VolumeControl control = new VolumeControl();
        volumeControls.add(control);
        parent.addView(control.row, new LinearLayout.LayoutParams(-1, -2));
    }

    private final class VolumeControl {
        final LinearLayout row = new LinearLayout(MainActivity.this);
        final TextView unknown, ambiguous;
        final VolumeReading tvReading, soundReading;

        VolumeControl() {
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView quieter = tvButton("−", KeyEvent.KEYCODE_VOLUME_DOWN);
            quieter.setContentDescription("Leiser über den Fernseher");
            quieter.setTextSize(20 * theme.theme.fontScale);
            row.addView(quieter, new LinearLayout.LayoutParams(dp(54), dp(52)));
            LinearLayout readings = new LinearLayout(MainActivity.this);
            readings.setOrientation(LinearLayout.VERTICAL);
            readings.setGravity(Gravity.CENTER_VERTICAL);
            unknown = label("TV · Pegel unbekannt", 12, MUTED);
            unknown.setGravity(Gravity.CENTER);
            readings.addView(unknown, new LinearLayout.LayoutParams(-1, -2));
            tvReading = new VolumeReading(readings);
            soundReading = new VolumeReading(readings);
            ambiguous = label("Audioausgabe unklar", 11, MUTED);
            ambiguous.setGravity(Gravity.CENTER);
            ambiguous.setVisibility(View.GONE);
            readings.addView(ambiguous, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.leftMargin = lp.rightMargin = dp(8);
            row.addView(readings, lp);
            TextView louder = tvButton("+", KeyEvent.KEYCODE_VOLUME_UP);
            louder.setContentDescription("Lauter über den Fernseher");
            louder.setTextSize(20 * theme.theme.fontScale);
            row.addView(louder, new LinearLayout.LayoutParams(dp(54), dp(52)));
        }

        void render(TvRemoteClient.State t, DeviceState s, VolumePresentation.Mode mode) {
            boolean showTv = mode == VolumePresentation.Mode.TV || mode == VolumePresentation.Mode.BOTH;
            boolean showSound = mode == VolumePresentation.Mode.SOUNDBAR || mode == VolumePresentation.Mode.BOTH;
            unknown.setVisibility(mode == VolumePresentation.Mode.UNKNOWN ? View.VISIBLE : View.GONE);
            tvReading.group.setVisibility(showTv ? View.VISIBLE : View.GONE);
            soundReading.group.setVisibility(showSound ? View.VISIBLE : View.GONE);
            ambiguous.setVisibility(mode == VolumePresentation.Mode.BOTH ? View.VISIBLE : View.GONE);
            if (showTv) tvReading.render("TV", t.volumeLevel, t.volumeMax, Boolean.TRUE.equals(t.muted));
            if (showSound) soundReading.render("Soundbar · " + VolumePresentation.arcLabel(s.getCurrentInput()),
                s.getVolume(), 100, false);
        }
    }

    private final class VolumeReading {
        final LinearLayout group = new LinearLayout(MainActivity.this);
        final TextView value;
        final ProgressBar bar;

        VolumeReading(LinearLayout parent) {
            group.setOrientation(LinearLayout.VERTICAL);
            group.setVisibility(View.GONE);
            value = label("", 12, TEXT);
            value.setGravity(Gravity.CENTER);
            group.addView(value, new LinearLayout.LayoutParams(-1, -2));
            bar = new ProgressBar(MainActivity.this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setIndeterminate(false);
            bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(SURFACE_ALT));
            // The adjacent text supplies the exact reported value to accessibility services.
            bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            group.addView(bar, new LinearLayout.LayoutParams(-1, dp(10)));
            parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        }

        void render(String source, int level, int max, boolean muted) {
            value.setText(source + " · " + level + " / " + max + (muted ? " · Stumm" : ""));
            bar.setMax(max);
            bar.setProgress(level);
            bar.setProgressTintList(ColorStateList.valueOf(muted ? MUTED : ACCENT));
        }
    }

    private void renderVolumes(TvRemoteClient.State t) {
        DeviceState s = soundbarModel.getDevice().getValue();
        VolumePresentation.Mode mode = VolumePresentation.select(t.connected(), t.powerOn,
            t.volumeLevel, t.volumeMax, s.getConnection() == Connection.CONNECTED,
            s.getPowerOn(), s.getCurrentInput(), s.getVolume());
        for (VolumeControl control : volumeControls) control.render(t, s, mode);
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
        if (streamRow == null) return;
        streamRow.removeAllViews();
        // Saved mappings and their order survive absent apps. They are not a source of inventory.
        for (StreamingShortcuts.Entry entry : tvAppInventory.activeFavorites(shortcuts.entries)) {
            TclAppLauncher.App app = tvAppInventory.find(TclAppLauncher.packageForLink(entry.link));
            if (app == null) continue;
            TextView tile = actionButton(app.name, "");
            tile.setTextSize(12 * theme.theme.fontScale);
            tile.setPadding(dp(3), 0, dp(3), 0);
            tile.setContentDescription(app.name + " starten; lange drücken zum Bearbeiten");
            tile.setOnClickListener(v -> launchStreaming(entry));
            tile.setOnLongClickListener(v -> { shortcutOptions(entry); return true; });
            streamRow.addView(tile);
        }
        if (streamRow.getChildCount() == 0) {
            TextView empty = actionButton(tvAppInventory.verified() ? "+" : "TV-App-Liste nicht aktuell", "");
            empty.setContentDescription("TV-App als Favorit auswählen");
            empty.setOnClickListener(v -> addStreamingShortcut());
            streamRow.addView(empty);
        }
        sizeStreamingTiles();
        renderSourcesApps();
    }
    private void launchStreaming(StreamingShortcuts.Entry entry) {
        if (!tv.state().connected()) {
            Toast.makeText(this, "Zuerst TV verbinden.", Toast.LENGTH_LONG).show(); return;
        }
        String pkg = TclAppLauncher.packageForLink(entry.link);
        if (pkg == null) { launchStreamingLink(entry); return; }
        if (!tv.canRequestTclApps()) {
            Toast.makeText(this, "TV muss verbunden und eingeschaltet sein.", Toast.LENGTH_LONG).show(); return;
        }
        tv.launchTclApp(pkg, result -> {
            if (isFinishing() || isDestroyed()) return;
            switch (result.status) {
                case SENT:
                    Toast.makeText(this, entry.name + " – TCL-Startbefehl gesendet.", Toast.LENGTH_SHORT).show(); break;
                case UNAVAILABLE:
                    // No TCL launch attempted. Preserve the old link on other TVs.
                    launchStreamingLink(entry); break;
                case NOT_INSTALLED:
                    // The inventory can be incomplete. Only a previously configured URL
                    // may be used as an alternative; don't retry the old market route.
                    if (!entry.link.startsWith("market://launch?id=")) launchStreamingLink(entry);
                    else Toast.makeText(this, entry.name + " nicht in der TV-App-Liste gefunden.", Toast.LENGTH_LONG).show();
                    break;
                case FAILED:
                    Toast.makeText(this, "TCL-App-Start nicht bestätigt. Kein automatischer weiterer Startversuch.", Toast.LENGTH_LONG).show(); break;
                case BUSY:
                    Toast.makeText(this, "App-Aufruf läuft bereits.", Toast.LENGTH_SHORT).show(); break;
                default: break;
            }
        });
    }
    private void launchStreamingLink(StreamingShortcuts.Entry entry) {
        TvRemoteClient.State state = tv.state();
        if (!state.connected() || !state.apps) {
            Toast.makeText(this, !state.connected() ? "Zuerst TV verbinden." :
                "TV meldet keine App-Link-Unterstützung.", Toast.LENGTH_LONG).show();
            return;
        }
        if (entry.link.startsWith("market://launch?id=")) {
            // Preserve saved links and the experimental option for compatible TVs, but
            // do not send a known problematic launch mechanism on an ordinary tap.
            new AlertDialog.Builder(this).setTitle(entry.name + " – Paketname-Start")
                .setMessage("Dieser Startmechanismus ist auf manchen Google-TV-Geräten derzeit fehlerhaft und kann die TV-Verbindung unterbrechen. Nutze nach Möglichkeit einen funktionierenden App-Link oder öffne die App am TV. Trotzdem testen?")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Testen", (dialog, which) -> tv.launchApp(entry.link, entry.name))
                .show();
            return;
        }
        tv.launchApp(entry.link, entry.name);
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
        labels.add("Installierte TV-App zuordnen"); actions.add(() -> loadInstalledApps(entry));
        if (!entry.link.startsWith("market://launch?id=") && TclAppLauncher.packageForLink(entry.link) != null) {
            labels.add("Alternativ mit App-Link starten"); actions.add(() -> launchStreamingLink(entry));
        }
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
        loadInstalledApps(null);
    }
    private void loadInstalledApps(StreamingShortcuts.Entry old) {
        tvAppInventory.device(tv.inventoryDeviceKey());
        if (!tvAppInventory.verified()) {
            if (tv.canRequestTclApps()) {
                refreshTvAppInventory();
                Toast.makeText(this, "TV-App-Liste wird geladen. Danach erneut auswählen.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Keine aktuelle TV-App-Liste. Fernseher verbinden und Quellen aktualisieren.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        showInstalledApps(old, false);
    }

    private void showInstalledApps(StreamingShortcuts.Entry old, boolean all) {
        List<StreamingShortcuts.Entry> choices = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (TclAppLauncher.App app : tvAppInventory.visible(all)) {
            StreamingShortcuts.Entry entry = new StreamingShortcuts.Entry(app.name, app.packageName);
            boolean duplicate = false;
            for (StreamingShortcuts.Entry other : shortcuts.entries) {
                if (other != old && app.packageName.equals(TclAppLauncher.packageForLink(other.link))) {
                    duplicate = true; break;
                }
            }
            if (duplicate) continue;
            choices.add(entry);
            names.add(app.name + "\n" + app.packageName);
        }
        int count = choices.size();
        names.add(all ? "System-Apps ausblenden" : "Auch System-Apps anzeigen");
        names.add("App-Liste aktualisieren");
        new AlertDialog.Builder(this).setTitle(old == null ? "Installierte TV-Apps" : "TV-App für " + old.name)
            .setItems(names.toArray(new String[0]), (d, i) -> {
                if (i == count) showInstalledApps(old, !all);
                else if (i == count + 1) {
                    refreshTvAppInventory();
                    Toast.makeText(this, "App-Liste wird aktualisiert. Auswahl danach erneut öffnen.", Toast.LENGTH_LONG).show();
                } else {
                    StreamingShortcuts.Entry entry = choices.get(i);
                    if (old == null) {
                        if (shortcuts.entries.size() >= 50 || shortcuts.contains(entry)) return;
                        shortcuts.entries.add(entry);
                    } else {
                        int index = shortcuts.entries.indexOf(old);
                        if (index < 0) return;
                        shortcuts.entries.set(index, new StreamingShortcuts.Entry(old.name, entry.link));
                    }
                    saveStreamingShortcuts();
                }
            }).setNegativeButton("Schließen", null).show();
    }

    private void refreshTvAppInventory() {
        tvAppInventory.device(tv.inventoryDeviceKey());
        if (appInventoryLoading) return;
        if (!tv.canRequestTclApps()) {
            appInventoryError = "TV nicht verbunden oder ausgeschaltet.";
            tvAppInventory.stale();
            renderSourcesApps();
            renderStreamingShortcuts();
            return;
        }
        final String identity = tvAppInventory.device();
        appInventoryLoading = true;
        appInventoryError = "";
        renderSourcesApps();
        tv.requestTclApps(result -> {
            appInventoryLoading = false;
            if (isFinishing() || isDestroyed()) return;
            tvAppInventory.device(tv.inventoryDeviceKey());
            if (!identity.equals(tvAppInventory.device())) return;
            if (result.status == TclAppLauncher.Status.APPS) {
                tvAppInventory.received(result.apps);
                appInventoryError = "";
            } else {
                tvAppInventory.stale();
                appInventoryError = result.status == TclAppLauncher.Status.BUSY ?
                    "T-Cast beschäftigt. Bitte erneut aktualisieren." :
                    "TV-App-Liste konnte nicht geladen werden.";
            }
            renderStreamingShortcuts();
        });
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
        form.addView(label("Bei kompatiblen TCL-TVs werden Paketnamen direkt gestartet. Andere TVs benötigen einen funktionierenden App-Link.", 12, MUTED));
        if (old != null) { name.setText(old.name); link.setText(old.link); }
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(old == null ? "Eigene TV-App" : "Dienst bearbeiten")
            .setView(form).setNegativeButton("Abbrechen", null).setPositiveButton("Speichern", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                StreamingShortcuts.Entry entry = new StreamingShortcuts.Entry(name.getText().toString(), link.getText().toString());
                for (StreamingShortcuts.Entry other : shortcuts.entries) {
                    if (other != old && StreamingShortcuts.sameTarget(other, entry))
                        throw new IllegalArgumentException("Dieser Name oder Link ist bereits vorhanden.");
                }
                if (old == null) {
                    if (shortcuts.entries.size() >= 50) throw new IllegalArgumentException("Maximal 50 Schnellwahltasten.");
                    shortcuts.entries.add(entry);
                } else {
                    int index = shortcuts.entries.indexOf(old);
                    if (index < 0) { dialog.dismiss(); return; }
                    shortcuts.entries.set(index, entry);
                }
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
            setOnClickListener(v -> {
                if (!power) { tv.sendKey(key); return; }
                if (Build.VERSION.SDK_INT >= 37 &&
                    checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.ACCESS_LOCAL_NETWORK"}, 601);
                    Toast.makeText(MainActivity.this, "Netzwerkzugriff erlauben und Power erneut drücken.", Toast.LENGTH_LONG).show();
                    return;
                }
                tv.requestPower();
            });
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

    private LinearLayout fullPage() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(theme.theme.paddingDp), dp(6), dp(theme.theme.paddingDp), dp(8));
        body.setBackgroundColor(BG);
        return body;
    }

    private ScrollView scrollPage(LinearLayout body) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    private TextView compactStatus(LinearLayout parent, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(title, 19, TEXT);
        heading.setTypeface(ThemeArt.typeface(theme.theme, Typeface.BOLD));
        row.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView status = actionButton("Nicht verbunden", "");
        status.setTextSize(13 * theme.theme.fontScale);
        status.setMaxLines(1);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setPadding(dp(10),0,dp(10),0);
        status.setOnClickListener(v -> tvDialog.open());
        row.addView(status, new LinearLayout.LayoutParams(dp(145), dp(44)));
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        return status;
    }

    private View remotePage() {
        LinearLayout body = fullPage();
        tvRemoteStatus = compactStatus(body, "Fernbedienung");
        int[] directionKeys = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_CENTER};
        remoteDial = new ThemedDial(this, theme, id -> tv.sendKey(directionKeys[id]));
        // Natural square size, anchored below the header instead of centered in a weighted spacer.
        // Keep the entire dial on narrow/short screens and allow vertical scrolling when needed.
        LinearLayout.LayoutParams dialLp = new LinearLayout.LayoutParams(-1, -2);
        dialLp.bottomMargin = dp(2);
        body.addView(remoteDial, dialLp);
        addTvKeyRow(body, new String[]{"Zurück", "Home"},
            new int[]{KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME}, 52);
        addTvKeyRow(body, new String[]{"⏪", "▶ / Ⅱ", "⏩"},
            new int[]{KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD}, 48);
        LinearLayout volume = new LinearLayout(this);
        volume.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("LAUTSTÄRKE", 12, MUTED);
        title.setTypeface(ThemeArt.typeface(theme.theme, Typeface.BOLD));
        volume.addView(title, new LinearLayout.LayoutParams(-1, -2));
        addVolumeControl(volume);
        LinearLayout.LayoutParams volumeLp = new LinearLayout.LayoutParams(-1, -2);
        volumeLp.topMargin = dp(8);
        body.addView(volume, volumeLp);
        TextView keyboard = actionButton("Tastatur – in Vorbereitung", "");
        keyboard.setTextSize(13 * theme.theme.fontScale);
        keyboard.setEnabled(false);
        keyboard.setAlpha(.45f);
        LinearLayout.LayoutParams keyboardLp = new LinearLayout.LayoutParams(-1, dp(48));
        keyboardLp.topMargin = dp(6);
        body.addView(keyboard, keyboardLp);
        return scrollPage(body);
    }

    private void addTvKeyRow(LinearLayout parent, String[] labels, int[] keys, int height) {
        LinearLayout row = new LinearLayout(this);
        for (int i=0;i<labels.length;i++) {
            TextView button = tvButton(labels[i], keys[i]);
            if (keys[i] == KeyEvent.KEYCODE_MEDIA_REWIND) button.setContentDescription("Zurückspulen");
            if (keys[i] == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) button.setContentDescription("Wiedergabe / Pause");
            if (keys[i] == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) button.setContentDescription("Vorspulen");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,dp(height),1f);
            if(i>0) lp.leftMargin=dp(8);
            row.addView(button,lp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(8);
        parent.addView(row,lp);
    }

    private View sourcesPage() {
        LinearLayout body = fullPage();
        sourcesStatus = compactStatus(body, "Quellen");
        LinearLayout inputs = section(body, "HDMI-EINGÄNGE");
        for (int rowIndex=0; rowIndex<2; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (int col=0; col<2; col++) {
                final int port = rowIndex*2+col+1;
                TextView tile = actionButton(hdmiLabel(port), "");
                hdmiButtons.add(tile);
                tile.setOnClickListener(v -> {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    selectHdmi(port);
                });
                tile.setTextSize(15 * theme.theme.fontScale);
                compactInputButton(tile);
                tile.setContentDescription("HDMI " + port + ": " + hdmiLabel(port) + "; lange drücken zum Umbenennen");
                tile.setOnLongClickListener(v -> { editHdmiLabel(port, tile); return true; });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
                if (col>0) lp.leftMargin=dp(8);
                row.addView(tile,lp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
            lp.bottomMargin=dp(6);
            inputs.addView(row,lp);
        }
        TextView chooser = tvButton("TV-Eingangsauswahl",KeyEvent.KEYCODE_TV_INPUT);
        compactInputButton(chooser);
        inputs.addView(chooser,new LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT));
        inputs.addView(label("Antippen: HDMI direkt wählen. Lange drücken: Eingang benennen.",12,MUTED));
        LinearLayout apps = section(body,"APPS");
        TextView add = actionButton("+ TV-App / Favorit","");
        add.setOnClickListener(v -> addStreamingShortcut());
        apps.addView(add,new LinearLayout.LayoutParams(-1,dp(48)));
        apps.addView(label("Nur vom verbundenen TV gemeldete Apps. ★ = Favorit. Lange drücken zum Verwalten.",12,MUTED));
        LinearLayout inventoryActions = new LinearLayout(this);
        TextView refresh = actionButton("Aktualisieren", "");
        refresh.setOnClickListener(v -> refreshTvAppInventory());
        inventoryActions.addView(refresh, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView systemToggle = actionButton("System-Apps ein/aus", "");
        systemToggle.setOnClickListener(v -> { includeSystemApps = !includeSystemApps; renderSourcesApps(); });
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        toggleLp.leftMargin = dp(8);
        inventoryActions.addView(systemToggle, toggleLp);
        apps.addView(inventoryActions);
        appsInventoryStatus = label("", 12, MUTED);
        apps.addView(appsInventoryStatus);
        sourcesAppList = new LinearLayout(this);
        sourcesAppList.setOrientation(LinearLayout.VERTICAL);
        apps.addView(sourcesAppList,new LinearLayout.LayoutParams(-1,-2));
        renderSourcesApps();
        return scrollPage(body);
    }

    /** Compact source selection, while retaining a 48 dp minimum touch target.
     * Wrap-content allows custom HDMI labels and larger accessibility fonts to grow.
     */
    private void compactInputButton(TextView button) {
        button.setMinHeight(dp(48));
        button.setPadding(dp(6),dp(4),dp(6),dp(4));
    }

    private void renderHdmiButtons() {
        boolean ready = tv.hdmiDeviceKey() != null;
        for (TextView tile : hdmiButtons) {
            // Keep rename accessible even if not connected; clicks explain the reason.
            tile.setAlpha(ready ? 1f : .55f);
        }
    }

    private void selectHdmi(int port) {
        String device = tv.hdmiDeviceKey();
        if (device == null) {
            Toast.makeText(this, "TV verbinden und einschalten; App-Link-Unterstützung erforderlich.", Toast.LENGTH_LONG).show();
            return;
        }
        if (HdmiInputProfile.canSelect(device, port, key -> connectionOptions.getBoolean(key, false))) {
            sendHdmi(port, device);
            return;
        }
        // No model is reliably reported by the current Remote-v2 handshake.
        // On an unknown pairing, ask for the supported model once, not for a test on every port.
        new AlertDialog.Builder(this).setTitle("HDMI-Direktwahl einrichten")
            .setMessage("Die native Direktwahl ist für TCL 85C89L (Gerätebezeichnung 85C8L) bestätigt. Ist dies dein Fernseher? Bei anderen Modellen kann die Zuordnung abweichen; verwende dort die allgemeine TV-Eingangsauswahl.")
            .setNegativeButton("Abbrechen", null)
            .setNeutralButton("TV-Eingangsauswahl", (d, w) -> {
                if (device.equals(tv.hdmiDeviceKey())) tv.sendKey(KeyEvent.KEYCODE_TV_INPUT);
            })
            .setPositiveButton("TCL 85C89L", (d, w) -> {
                if (!device.equals(tv.hdmiDeviceKey())) {
                    Toast.makeText(this, "TV-Verbindung geändert. Bitte erneut auswählen.", Toast.LENGTH_LONG).show();
                    return;
                }
                connectionOptions.edit().putBoolean(HdmiInputProfile.profileKey(device), true).apply();
                sendHdmi(port, device);
            }).show();
    }

    private void sendHdmi(int port, String device) {
        if (!tv.selectTclHdmiInput(port, device))
            Toast.makeText(this, "HDMI-Befehl nicht gesendet. TV-Verbindung prüfen.", Toast.LENGTH_LONG).show();
    }

    private String hdmiLabel(int port) {
        return connectionOptions.getString("hdmi_name_" + port, "HDMI " + port);
    }
    private void editHdmiLabel(int port, TextView tile) {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setText(hdmiLabel(port));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("HDMI " + port + " benennen")
            .setView(name).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = name.getText().toString().trim();
            if (value.isEmpty() || value.length()>40) { name.setError("1–40 Zeichen"); return; }
            connectionOptions.edit().putString("hdmi_name_" + port,value).apply();
            tile.setText(value);
            tile.setContentDescription("HDMI " + port + ": " + value + "; lange drücken zum Umbenennen");
            dialog.dismiss();
        });
    }
    private void renderSourcesApps() {
        if (sourcesAppList == null) return;
        tvAppInventory.device(tv.inventoryDeviceKey());
        sourcesAppList.removeAllViews();
        if (appsInventoryStatus != null) {
            String status = appInventoryLoading ? "TV-App-Liste wird abgefragt …" :
                tvAppInventory.verified() ? tvAppInventory.apps().size() + " TV-Apps gemeldet" :
                tvAppInventory.hasSnapshot() ? "Zuletzt bekannter Bestand – nicht aktuell bestätigt" :
                "Noch kein TV-App-Bestand abgefragt";
            if (!appInventoryError.isEmpty()) status += " · " + appInventoryError;
            appsInventoryStatus.setText(status);
        }
        List<TclAppLauncher.App> entries = tvAppInventory.visible(includeSystemApps);
        for (int i = 0; i < entries.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            for (int j = 0; j < 2; j++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(56), 1f);
                if (j > 0) lp.leftMargin = dp(8);
                if (i + j >= entries.size()) { row.addView(new View(this), lp); continue; }
                TclAppLauncher.App app = entries.get(i + j);
                StreamingShortcuts.Entry favoriteEntry = null;
                for (StreamingShortcuts.Entry saved : shortcuts.entries)
                    if (app.packageName.equals(TclAppLauncher.packageForLink(saved.link))) {
                        favoriteEntry = saved; break;
                    }
                final StreamingShortcuts.Entry favorite = favoriteEntry;
                TextView tile = actionButton((favorite != null ? "★  " : "") + app.name, "");
                tile.setTextSize(13 * theme.theme.fontScale);
                tile.setContentDescription(app.name + (favorite != null ? ", Favorit" : "") +
                    "; lange drücken zum Verwalten");
                tile.setOnClickListener(v -> launchStreaming(
                    favorite == null ? new StreamingShortcuts.Entry(app.name, app.packageName) : favorite));
                tile.setOnLongClickListener(v -> {
                    if (favorite != null) shortcutOptions(favorite);
                    else new AlertDialog.Builder(this).setTitle(app.name)
                        .setItems(new String[]{"Zum Startscreen hinzufügen"}, (d, which) -> {
                            if (shortcuts.entries.size() >= 50)
                                Toast.makeText(this, "Maximal 50 Schnellwahltasten.", Toast.LENGTH_SHORT).show();
                            else {
                                StreamingShortcuts.Entry entry = new StreamingShortcuts.Entry(app.name, app.packageName);
                                if (!shortcuts.contains(entry)) {
                                    shortcuts.entries.add(entry);
                                    saveStreamingShortcuts();
                                }
                            }
                        }).setNegativeButton("Schließen", null).show();
                    return true;
                });
                row.addView(tile, lp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            sourcesAppList.addView(row, lp);
        }
    }
    private View systemPage() {
        LinearLayout body = fullPage();
        systemStatus = compactStatus(body,"Bild & System");
        LinearLayout settings = section(body,"TV-EINSTELLUNGEN");
        TextView open = tvButton("TV-Einstellungen öffnen",KeyEvent.KEYCODE_SETTINGS);
        settings.addView(open,new LinearLayout.LayoutParams(-1,dp(56)));
        settings.addView(label("Öffnet die Einstellungen am Fernseher, sofern der TCL den Android-Tastenbefehl annimmt.",13,MUTED));
        LinearLayout picture = section(body,"BILD");
        picture.addView(label("Bildmodus, Helligkeit, Kontrast und persönliche Bildprofile",16,TEXT));
        picture.addView(label("Direkte Steuerung noch nicht verfügbar. Die TCL-Schnittstelle muss untersucht werden.",13,MUTED));
        LinearLayout system = section(body,"SYSTEM");
        system.addView(label("Sleep-Timer und Energiesparmodus: vorerst über das TV-Einstellungsmenü.",14,MUTED));
        TextView connection = actionButton("TV-Verbindung","");
        connection.setOnClickListener(v -> tvDialog.open());
        system.addView(connection,new LinearLayout.LayoutParams(-1,dp(52)));
        return scrollPage(body);
    }

    private TextView deviceHeader(LinearLayout parent, String name, String status) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(12);
        rowLp.bottomMargin = dp(10);
        parent.addView(row, rowLp);
        TextView title = label(name, 12, MUTED);
        title.setTypeface(ThemeArt.typeface(theme.theme, Typeface.BOLD));
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView statusView = label(status, 13, MUTED);
        statusView.setPadding(dp(8), dp(8), 0, dp(8));
        row.addView(statusView);
        return statusView;
    }

    private TextView tvButton(String text, int key) {
        TextView button = actionButton(text, "");
        button.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            tv.sendKey(key);
        });
        tvKeys.add(button);
        return button;
    }

    private TextView connectionButton(String name) {
        TextView button = actionButton(name, "");
        button.setTextSize(15 * theme.theme.fontScale);
        button.setFocusable(true);
        button.setOnClickListener(v -> tvDialog.open());
        return button;
    }
    private void paintConnection(TextView button, String name, boolean connected, boolean connecting) {
        if (button == null) return;
        button.setTextColor(connected ? ACCENT :
            (connecting ? CockpitTheme.PENDING : CockpitTheme.ERROR));
        button.setContentDescription(name + " – " + (connected ? "Verbunden" :
            (connecting ? "Verbindungsaufbau" : "Nicht verbunden")) + "; Verbindungen öffnen");
    }
    private void renderSoundConnection() {
        DeviceState state = soundbarModel.getDevice().getValue();
        paintConnection(soundConnectionButton, "Sound", state.getConnection() == Connection.CONNECTED,
            state.getConnection() == Connection.CONNECTING && state.getError() == null);
        if (tvDialog != null) tvDialog.renderSound();
        if (tv != null) renderVolumes(tv.state());
    }
    private void renderTvState(TvRemoteClient.State state) {
        boolean ready = state.connected() && state.keys;
        TvConnectionIndicator indicator = tv.indicator();
        int statusColor = indicator.color(theme.theme);
        if (tvConnectionButton != null) {
            tvConnectionButton.setTextColor(statusColor);
            tvConnectionButton.setContentDescription("TV – " + indicator.label +
                (indicator == TvConnectionIndicator.WAKE_READY ? ", Wake-on-LAN eingerichtet" : "") +
                "; Verbindungen öffnen");
        }
        boolean wakeConfigured = tv.wakeConfigured();
        if (tvDeviceHeader != null) tvDeviceHeader.setVisibility(state.connected() || wakeConfigured ? View.VISIBLE : View.GONE);
        if (tvHomeStatus != null) {
            tvHomeStatus.setVisibility(!state.connected() && wakeConfigured ? View.VISIBLE : View.GONE);
            tvHomeStatus.setText(tv.wakeInProgress() ? "Aufwecken …" : indicator.label);
            tvHomeStatus.setTextColor(statusColor);
        }
        if (tvHomeDetail != null) {
            boolean showDetail = indicator == TvConnectionIndicator.ERROR || tv.wakeInProgress() ||
                (state.connected() && !"Verbunden".equals(state.detail));
            tvHomeDetail.setText(state.detail);
            tvHomeDetail.setVisibility(showDetail ? View.VISIBLE : View.GONE);
        }
        String shortStatus = tv.wakeInProgress() ? "Aufwecken …" : indicator.label;
        for (TextView status : new TextView[]{tvRemoteStatus, sourcesStatus, systemStatus}) {
            if (status == null) continue;
            status.setText(shortStatus);
            status.setTextColor(statusColor);
            status.setContentDescription((indicator == TvConnectionIndicator.WAKE_READY
                ? "Einschaltbereit – Wake-on-LAN eingerichtet" : state.detail) + " – TV-Verbindung öffnen");
        }
        renderHdmiButtons();
        if (remoteDial != null) remoteDial.setEnabled(ready);
        for (TextView key : tvKeys) {
            key.setEnabled(ready);
            key.setAlpha(ready ? 1f : .42f);
        }
        if (tvPower != null) {
            tvPower.setVisibility(state.connected() || wakeConfigured ? View.VISIBLE : View.GONE);
            boolean powerEnabled = ready || tv.canWake();
            tvPower.setEnabled(powerEnabled);
            tvPower.setAlpha(powerEnabled ? 1f : .42f);
            tvPower.setTextColor(Boolean.TRUE.equals(state.powerOn) ? ACCENT : TEXT);
            tvPower.setContentDescription(!state.connected() && wakeConfigured ? "Fernseher über LAN aufwecken" :
                state.powerOn == null ? "Fernseher ein oder aus, Zustand unbekannt" :
                (state.powerOn ? "Fernseher ausschalten" : "Fernseher einschalten"));
            tvPower.invalidate();
        }
        if (tvMute != null) {
            tvMute.setVisibility(state.connected() ? View.VISIBLE : View.GONE);
            tvMute.setTextColor(Boolean.TRUE.equals(state.muted) ? ACCENT : TEXT);
            tvMute.setContentDescription(state.muted == null ? "Stummschaltung, Zustand unbekannt" :
                (state.muted ? "Stummschaltung aufheben" : "Stummschaltung einschalten"));
            tvMute.invalidate();
        }
        tvAppInventory.device(tv.inventoryDeviceKey());
        if (!state.connected()) tvAppInventory.stale();
        renderStreamingShortcuts();
        boolean firstConnected = state.connected() && !wasTvInventoryConnected;
        wasTvInventoryConnected = state.connected();
        if (firstConnected) refreshTvAppInventory();
        renderVolumes(state);
        if (tvDialog != null) tvDialog.render(state);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && updates != null) updates.onWindowFocus();
    }

    @Override protected void onPause() {
        if (updates != null) updates.onPause();
        if (connectionRefresh != null) connectionRefresh.cancel();
        if (tv != null) tv.onBackground();
        if (soundbarModel != null) soundbarModel.onBackground();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (updates != null) updates.onResume();
        boolean allowed = Build.VERSION.SDK_INT < 37 ||
            checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (tv != null) { tv.onForeground(allowed); renderHdmiButtons(); }
        if (soundbarModel != null) soundbarModel.onForeground(allowed);
    }

    @Override protected void onDestroy() {
        if (quickMenu != null) quickMenu.dismiss();
        closeAppLaunchTestDialog();
        if (connectionRefresh != null) connectionRefresh.close();
        if (updates != null) updates.close();
        if (themePicker != null) themePicker.close();
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
        heading.setTypeface(ThemeArt.typeface(theme.theme, Typeface.BOLD));
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
        int inset=dp(theme.theme.paddingDp);
        card.setPadding(inset,inset,inset,inset);
        card.setBackground(ThemeArt.surface(theme,SURFACE,theme.theme.cornerDp,
            getResources().getDisplayMetrics().density,false,false));
        return card;
    }

    private TextView actionButton(String text, String message) {
        TextView button = label(text, 14, TEXT);
        button.setTypeface(ThemeArt.typeface(theme.theme, Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setBackground(shape(SURFACE_ALT, 14));
        button.setOnClickListener(v -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
        return button;
    }

    private TextView pill(String text) {
        TextView view = label(text, 10, MUTED);
        view.setTypeface(ThemeArt.typeface(theme.theme, Typeface.BOLD));
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), dp(7), dp(9), dp(7));
        view.setBackground(shape(SURFACE_ALT, 30));
        return view;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size * theme.theme.fontScale);
        view.setTypeface(ThemeArt.typeface(theme.theme,Typeface.NORMAL));
        view.setTextColor(color);
        return view;
    }

    private Drawable shape(int color, int radius) {
        return ThemeArt.surface(theme,color,theme.theme.cornerDp,getResources().getDisplayMetrics().density,false,true);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
