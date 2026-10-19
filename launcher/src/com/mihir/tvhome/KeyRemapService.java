package com.mihir.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.media.tv.TvContract;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Remaps remote buttons (config.xml key_remaps), e.g. TCL's YouTube button to SmartTube.
 * An accessibility service sees keys before the manufacturer's key handler, which drops them when
 * its hard-coded app is disabled. Only the configured keys are touched; everything else passes through.
 * Turn on in Settings → Accessibility → Remote button remap.
 *
 * Entries are "key=target". key: a keycode number or name (KEYCODE_NOTIFICATION). target: a package,
 * "action:<intent action>", "component:<pkg>/<class>", or "tvinput:<TV input id>" (Live TV on that input).
 */
public class KeyRemapService extends AccessibilityService {
    private final SparseArray<String> remaps = new SparseArray<>();

    @Override
    protected void onServiceConnected() {
        for (String entry : getResources().getStringArray(R.array.key_remaps)) {
            String[] kv = entry.split("=", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim();
            int code = key.matches("\\d+") ? Integer.parseInt(key) : KeyEvent.keyCodeFromString(key);
            if (code != KeyEvent.KEYCODE_UNKNOWN) remaps.put(code, kv[1].trim());
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        String target = remaps.get(event.getKeyCode());
        if (target == null) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            Intent i = intentFor(target);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return true;
    }

    private Intent intentFor(String target) {
        if (target.startsWith("action:")) return new Intent(target.substring(7));
        if (target.startsWith("component:")) {
            ComponentName c = ComponentName.unflattenFromString(target.substring(10));
            return c == null ? null : new Intent().setComponent(c);
        }
        if (target.startsWith("tvinput:")) {
            return new Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(target.substring(8)));
        }
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(target);
        return i != null ? i : getPackageManager().getLaunchIntentForPackage(target);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
