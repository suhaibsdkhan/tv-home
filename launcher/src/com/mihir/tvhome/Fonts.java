package com.mihir.tvhome;

import android.content.Context;
import android.graphics.Typeface;

/**
 * Inter (assets/fonts, SIL Open Font License), the open stand-in for Apple's San Francisco, which is
 * licensed for Apple devices only. Takes the system family names the views ask for.
 */
final class Fonts {
    private static Typeface light, regular, medium;

    static Typeface get(Context c, String family) {
        if (light == null) {
            light = Typeface.createFromAsset(c.getAssets(), "fonts/inter-light.ttf");
            regular = Typeface.createFromAsset(c.getAssets(), "fonts/inter-regular.ttf");
            medium = Typeface.createFromAsset(c.getAssets(), "fonts/inter-medium.ttf");
        }
        if (family.endsWith("-light") || family.endsWith("-thin")) return light;
        if (family.endsWith("-medium") || family.endsWith("-bold")) return medium;
        return regular;
    }
}
