package com.mihir.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.List;

/** "Who's watching?": the home wallpaper frosted behind a row of round avatars, one per person. */
public class WhoActivity extends Activity {
    // Illustrated avatars, picked by position so each person keeps theirs.
    private static final int[] SCENES = {
            R.drawable.avatar_scene_0, R.drawable.avatar_scene_1, R.drawable.avatar_scene_2, R.drawable.avatar_scene_3,
    };

    private List<People.Person> people;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        people = People.all(this);
        if (people.isEmpty()) { finish(); return; }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ImageView wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wall.setImageBitmap(currentWallpaper());
        if (Build.VERSION.SDK_INT >= 31) {
            wall.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(dp(40), dp(40), Shader.TileMode.CLAMP));
        }
        wall.setScaleX(1.08f);
        wall.setScaleY(1.08f);
        root.addView(wall, new FrameLayout.LayoutParams(-1, -1));

        View frost = new View(this);
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x73000000, 0x59000000, 0x99000000});
        frost.setBackground(shade);
        root.addView(frost, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);

        TextView title = text(40, "sans-serif-light", Color.WHITE);
        title.setText("Who's watching?");
        title.setLetterSpacing(0.01f);
        column.addView(title, llp(-2, -2, 0, 0, 0, dp(56)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        int current = People.currentIndex(this);
        View focus = null;
        for (int i = 0; i < people.size(); i++) {
            View a = avatar(i, people.get(i));
            row.addView(a, llp(-2, -2, dp(28), 0, dp(28), 0));
            if (i == current || focus == null) focus = a.findViewWithTag("ring");
        }
        column.addView(row, llp(-2, -2, 0, 0, 0, 0));

        root.addView(column, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        setContentView(root);

        column.setAlpha(0f);
        column.setTranslationY(dp(24));
        column.animate().alpha(1f).translationY(0).setDuration(500).setInterpolator(new DecelerateInterpolator(2f)).start();
        if (focus != null) focus.requestFocus();
    }

    private View avatar(int index, People.Person p) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        FrameLayout ring = new FrameLayout(this);
        ring.setTag("ring");
        ring.setFocusable(true);
        ring.setClickable(true);
        int size = dp(150);
        GradientDrawable outline = new GradientDrawable();
        outline.setShape(GradientDrawable.OVAL);
        outline.setStroke(dp(4), 0x00FFFFFF);
        ring.setForeground(outline);
        ring.setPadding(dp(7), dp(7), dp(7), dp(7));

        View face;
        GradientDrawable fill;
        if (p.guest) {
            // Frosted glass disc with a silhouette for the guest.
            ImageView icon = new ImageView(this);
            icon.setImageResource(R.drawable.ic_person);
            icon.setPadding(dp(38), dp(34), dp(38), dp(42));
            fill = new GradientDrawable();
            fill.setColor(0x33FFFFFF);
            fill.setStroke(dp(1), 0x59FFFFFF);
            face = icon;
        } else {
            // Their picture (launcher/local/drawable-nodpi/avatar_<name>.jpg) or an illustrated scene,
            // cropped to a circle.
            ImageView pic = new ImageView(this);
            int photo = photo(p);
            pic.setImageResource(photo != 0 ? photo : SCENES[index % SCENES.length]);
            pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            pic.setClipToOutline(true);
            fill = new GradientDrawable();
            fill.setColor(0xFF2C2C2E);
            face = pic;
        }
        fill.setShape(GradientDrawable.OVAL);
        face.setBackground(fill);
        ring.setOutlineProvider(null);
        ring.addView(face, new FrameLayout.LayoutParams(size, size));
        box.addView(ring, new LinearLayout.LayoutParams(-2, -2));

        TextView name = text(20, "sans-serif", Color.WHITE);
        name.setText(p.name);
        name.setAlpha(0.6f);
        box.addView(name, llp(-2, -2, 0, dp(30), 0, dp(12)));

        ring.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f)
                    .setInterpolator(has ? new OvershootInterpolator(1.6f) : new DecelerateInterpolator())
                    .setDuration(has ? 320 : 200).start();
            outline.setStroke(dp(4), has ? 0xF2FFFFFF : 0x00FFFFFF);
            name.animate().alpha(has ? 1f : 0.6f).setDuration(200).start();
        });
        ring.setOnClickListener(v -> pick(index));
        return box;
    }

    private int photo(People.Person p) {
        String key = "avatar_" + p.name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "_");
        return getResources().getIdentifier(key, "drawable", getPackageName());
    }

    private void pick(int index) {
        People.choose(this, index);
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void onBackPressed() {
        // Back keeps whoever was watching.
        pick(People.currentIndex(this));
    }

    /** The wallpaper the home screen is showing, downsampled (it gets blurred anyway). */
    private Bitmap currentWallpaper() {
        try {
            String[] walls = getAssets().list("wallpapers");
            if (walls == null || walls.length == 0) return null;
            int i = getSharedPreferences("MainActivity", MODE_PRIVATE).getInt("wallpaper", 0);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;
            try (InputStream in = getAssets().open("wallpapers/" + walls[Math.max(0, i) % walls.length])) {
                return BitmapFactory.decodeStream(in, null, o);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private TextView text(float sp, String family, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private static LinearLayout.LayoutParams llp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }
}
