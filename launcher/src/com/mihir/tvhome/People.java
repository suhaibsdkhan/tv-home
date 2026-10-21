package com.mihir.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The people who use the TV (config people), each mapped to an Android user: the main user or a Google TV
 * profile (com.android.tv.profile) with its own app data, so its own Netflix, Spotify, YouTube logins.
 * Apps are opened inside the chosen person's profile with LauncherApps. Android only allows that for apps
 * with a phone-style launcher entry; TV-only apps (Prime Video, Crave…) open the main user's shared copy,
 * and are hidden from guests so nobody's account shows.
 */
final class People {
    static final int OWN = 0, SHARED = 1, NONE = 2;

    static final class Person {
        String name;
        int userId;
        boolean guest;
        UserHandle user;
    }

    static List<Person> all(Context c) {
        List<Person> out = new ArrayList<>();
        for (String entry : c.getResources().getStringArray(R.array.people)) {
            String[] f = entry.split("\\|");
            if (f.length < 2) continue;
            Person p = new Person();
            p.name = f[0].trim();
            p.userId = Integer.parseInt(f[1].trim());
            p.guest = f.length > 2 && f[2].trim().equals("guest");
            p.user = UserHandle.getUserHandleForUid(p.userId * 100000);
            out.add(p);
        }
        return out;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("people", Context.MODE_PRIVATE);
    }

    static int currentIndex(Context c) {
        return prefs(c).getInt("current", 0);
    }

    /** The person using the TV now, or null when no people are configured. */
    static Person current(Context c) {
        List<Person> all = all(c);
        if (all.isEmpty()) return null;
        int i = currentIndex(c);
        return all.get(i >= 0 && i < all.size() ? i : 0);
    }

    static void choose(Context c, int index) {
        prefs(c).edit().putInt("current", index).putBoolean("pick", false).putLong("boot", bootTime()).apply();
    }

    /** Ask "Who's watching?" again, e.g. when the screen comes back on. */
    static void requestPick(Context c) {
        prefs(c).edit().putBoolean("pick", true).apply();
    }

    static boolean needsPick(Context c) {
        if (all(c).isEmpty()) return false;
        SharedPreferences p = prefs(c);
        // A restart always asks again.
        return p.getBoolean("pick", true) || Math.abs(p.getLong("boot", 0) - bootTime()) > 60_000;
    }

    private static long bootTime() {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }

    static void countLaunch(Context c, Person p, String pkg) {
        String k = "n:" + (p == null ? 0 : p.userId) + ":" + pkg;
        prefs(c).edit().putInt(k, prefs(c).getInt(k, 0) + 1).apply();
    }

    static int launches(Context c, Person p, String pkg) {
        return prefs(c).getInt("n:" + (p == null ? 0 : p.userId) + ":" + pkg, 0);
    }

    private static boolean isMain(Person p) {
        return p == null || p.user.equals(Process.myUserHandle());
    }

    /** OWN: opens in the person's profile. SHARED: opens the main user's copy. NONE: not for this person. */
    static int availability(Context c, Person p, String pkg) {
        if (isMain(p)) return OWN;
        LauncherApps la = (LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (!la.getActivityList(pkg, p.user).isEmpty()) return OWN;
        return p.guest ? NONE : SHARED;
    }

    /**
     * Opens pkg as the person. main is the intent used when it runs in the main user. Returns false when
     * the caller should open main itself (main user or shared copy).
     */
    static boolean openInProfile(Context c, Person p, String pkg) {
        if (isMain(p)) return false;
        int a = availability(c, p, pkg);
        if (a == SHARED) return false;
        if (a == NONE) {
            Toast.makeText(c, "Not available for guests", Toast.LENGTH_SHORT).show();
            return true;
        }
        UserManager um = (UserManager) c.getSystemService(Context.USER_SERVICE);
        if (!um.isUserRunning(p.user)) {
            Toast.makeText(c, p.name + "'s profile is asleep after a restart. Run tools/profiles.sh wake",
                    Toast.LENGTH_LONG).show();
            return true;
        }
        LauncherApps la = (LauncherApps) c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        List<LauncherActivityInfo> acts = la.getActivityList(pkg, p.user);
        try {
            la.startMainActivity(acts.get(0).getComponentName(), p.user, null, null);
            countLaunch(c, p, pkg);
        } catch (RuntimeException e) {
            Toast.makeText(c, "Couldn't open it in " + p.name + "'s profile", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /** Opens a package for whoever is using the TV (used by the remote button remap). */
    static void open(Context c, String pkg) {
        Person p = current(c);
        if (openInProfile(c, p, pkg)) return;
        Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
            countLaunch(c, p, pkg);
        } catch (RuntimeException ignored) {
        }
    }

    static Intent whoIntent(Context c) {
        return new Intent(c, WhoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
