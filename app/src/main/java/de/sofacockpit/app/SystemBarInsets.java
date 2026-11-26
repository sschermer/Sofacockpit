package de.sofacockpit.app;

import android.graphics.Color;
import android.view.View;
import android.view.Window;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** The shared root owns system and keyboard insets; descendants must not pad twice. */
public final class SystemBarInsets {
    private SystemBarInsets() {}

    public static void install(Window window, View root) {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);

        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            // System bars and display cutouts overlap; the type-mask takes
            // the maximum per edge. Retain the safe area during transient bars.
            Insets safe = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(left + safe.left, top + safe.top, right + safe.right,
                bottom + Math.max(safe.bottom, ime.bottom));
            // The entire subtree, including the embedded Compose page, is
            // inside the safe area already. Do not apply these insets again.
            return WindowInsetsCompat.CONSUMED;
        });
        if (ViewCompat.isAttachedToWindow(root)) ViewCompat.requestApplyInsets(root);
        else root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                ViewCompat.requestApplyInsets(view);
            }
            @Override public void onViewDetachedFromWindow(View view) {}
        });
    }
}
