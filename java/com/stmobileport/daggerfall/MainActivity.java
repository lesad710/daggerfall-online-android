package com.stmobileport.daggerfall;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The Daggerfall launcher: the ARENA2 folder (the free data Bethesda released, downloaded here on request, or the
 * player's own copy), and the play button that opens the live game (DaggerfallActivity).
 */
public class MainActivity extends Activity {
    static final int BG = 0xFF101216, CARD = 0xFF1A1D23, GOLD = 0xFFE0BC66, PAPER = 0xFFEDE6D6, DIM = 0xFF9A9384;
    // Daggerfall Unity's DaggerfallGameFiles.zip: Bethesda's freeware release of the game, its arena2/ folder inside
    // the offline build (our phone-tuned build of the open-source edition, MIT), a release asset of this app's repository
    static final String WEB_ZIP = "https://github.com/lesad710/daggerfall-online-android/releases/latest/download/daggerfall-offline-web.zip";
    static final String DATA_ZIP = "https://drive.usercontent.google.com/download?id=0B0i8ZocaUWLGWHc1WlF3dHNUNTQ&export=download&confirm=t";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private EditText path;
    private TextView status;
    private Button play;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        getWindow().getDecorView().setBackgroundColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setStatusBarColor(BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(20), dp(24), dp(24));
        col.addView(label("Daggerfall Online", 30, PAPER, true));
        TextView sub = label("The Elder Scrolls II · онлайн и одиночная игра", 15, DIM, false);
        sub.setPadding(0, dp(2), 0, dp(14));
        col.addView(sub);

        LinearLayout game = card("Игра", null);
        status = label("", 15, PAPER, false);
        game.addView(status);
        play = button("Играть", true);
        play.setOnClickListener(v -> launch());
        game.addView(play, spaced(10));
        col.addView(game, spaced(0));

        LinearLayout files = card("Файлы игры", "Папка ARENA2 с файлами Daggerfall. Bethesda раздаёт игру бесплатно — "
            + "приложение скачает её само (~150 МБ).");
        path = new EditText(this);
        path.setSingleLine(true);
        path.setTextColor(PAPER);
        path.setTextSize(14);
        path.setText(prefs.getString("arena2", defaultArena2().getAbsolutePath()));
        path.setOnFocusChangeListener((v, focused) -> { if (!focused) { save(); refresh(); } });
        files.addView(path);
        Button get = button("Скачать файлы игры", false);
        get.setOnClickListener(v -> download());
        files.addView(get, spaced(8));
        col.addView(files, spaced(14));

        LinearLayout opts = card("Настройки", "Применяются при следующем нажатии «Играть».");
        opts.addView(label("Режим", 15, PAPER, false));
        android.widget.RadioGroup mode = choice("mode", new String[] {"Онлайн (сайт)", "Офлайн (без интернета, оптимизирован)"},
            new String[] {"online", "offline"}, "online");
        for (int i = 0; i < mode.getChildCount(); i++) mode.getChildAt(i).setOnClickListener(v -> {
            prefs.edit().putString("mode", v == mode.getChildAt(0) ? "online" : "offline").apply();
            refresh();
        });
        opts.addView(mode);
        TextView offNote = label("Офлайн — облегчённая для телефона версия: тяжёлые эффекты выключены по умолчанию, работает "
            + "плавнее и без интернета. Скачивается один раз (~270 МБ).", 13, DIM, false);
        opts.addView(offNote);
        Button getWeb = button("Скачать офлайн-версию", false);
        getWeb.setOnClickListener(v -> downloadWeb());
        opts.addView(getWeb, spaced(8));
        TextView uiLabel = label("Размер интерфейса", 15, PAPER, false);
        uiLabel.setPadding(0, dp(10), 0, 0);
        opts.addView(uiLabel);
        opts.addView(choice("ui", new String[] {"100%", "80%", "67%", "55%"}, new String[] {"1", "0.8", "0.67", "0.55"}, "0.67"));
        TextView q = label("Качество картинки (меньше — быстрее)", 15, PAPER, false);
        q.setPadding(0, dp(10), 0, 0);
        opts.addView(q);
        // the game's own render-scale tiers (systems/features.js 'render-scale'); others would be ignored by it
        opts.addView(choice("renderscale", new String[] {"100%", "85%", "75%", "67%", "50%"},
            new String[] {"1", "0.85", "0.75", "0.67", "0.5"}, "0.75"));
        col.addView(opts, spaced(14));

        TextView hint = label("Онлайн: игра открывается с сайта daggerfalljs.dev и всегда свежая; в меню — ONLINE (аккаунт "
            + "создаётся прямо в игре) или OFFLINE. Офлайн: облегчённая для телефона версия, лежит на телефоне, интернет не нужен; "
            + "в меню игры выберите OFFLINE. Управление экранное, «Назад» — Esc.\n\nDaggerfall Online — проект Lattymoy (MIT), иконка — его же; The Elder Scrolls и Daggerfall — "
            + "товарные знаки ZeniMax Media.", 13, DIM, false);
        hint.setLineSpacing(dp(2), 1f);
        col.addView(hint, spaced(14));

        LinearLayout links = new LinearLayout(this);
        links.setGravity(Gravity.CENTER);
        String[][] l = {{"✈ Telegram-канал: @st_mobile_port", "https://t.me/st_mobile_port"}};
        for (String[] e : l) {
            TextView t = label(e[0], 14, GOLD, false);
            t.setPadding(dp(10), dp(14), dp(10), dp(4));
            t.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(e[1]))));
            links.addView(t);
        }
        col.addView(links);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col);
        setContentView(scroll);
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override protected void onPause() { save(); super.onPause(); }

    // ------------------------------------------------------------------ data

    /** The app's own folder: no storage permission needed there. */
    private File defaultArena2() {
        File root = getExternalFilesDir(null);
        return new File(root != null ? root : getFilesDir(), "arena2");
    }

    private File arena2() { return new File(path.getText().toString().trim()); }
    private void save() { prefs.edit().putString("arena2", arena2().getAbsolutePath()).apply(); }

    private static boolean has(File dir, String name) {
        return new File(dir, name).isFile() || new File(dir, name.toUpperCase(Locale.ROOT)).isFile();
    }
    private boolean ready() { return has(arena2(), "art_pal.col") && has(arena2(), "arch3d.bsa"); }
    private boolean offline() { return "offline".equals(prefs.getString("mode", "online")); }
    /** The offline build (our phone-tuned build of the open-source edition), in the app's folder. */
    private File webDir() {
        File root = getExternalFilesDir(null);
        return new File(root != null ? root : getFilesDir(), "web");
    }
    private boolean webReady() { return new File(webDir(), "play/index.html").isFile(); }

    /** A folder outside the app's own one needs the all-files permission. */
    private boolean canRead(File dir) {
        File own = getExternalFilesDir(null);
        boolean inside = own != null && dir.getAbsolutePath().startsWith(own.getParentFile().getAbsolutePath());
        if (inside || Environment.isExternalStorageManager()) return true;
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Нужен доступ к файлам")
            .setMessage("Папка " + dir + " — вне папки приложения. Разрешите доступ ко всем файлам или оставьте путь "
                + "по умолчанию и скачайте файлы кнопкой.")
            .setPositiveButton("Разрешить", (d, w) -> startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName()))))
            .setNegativeButton("Отмена", null).show();
        return false;
    }

    private void refresh() {
        boolean data = ready(), web = !offline() || webReady(), ok = data && web;
        status.setText(ok ? "✔ Файлы на месте. Можно играть." : !data ? "Нужны файлы Daggerfall: нажмите «Скачать файлы игры» "
            + "или укажите свою папку ARENA2." : "Офлайн-версия ещё не скачана: нажмите «Скачать офлайн-версию» в настройках.");
        status.setTextColor(ok ? 0xFF8FD18F : GOLD);
        play.setEnabled(ok);
        play.setAlpha(ok ? 1f : 0.45f);
    }

    private void launch() {
        save();
        if (!canRead(arena2())) return;
        Intent game = new Intent(this, DaggerfallActivity.class);
        if (offline()) game.putExtra("web", webDir().getAbsolutePath());
        startActivity(game.putExtra("arena2", arena2().getAbsolutePath())
            .putExtra("ui", Float.parseFloat(prefs.getString("ui", "0.67")))
            .putExtra("renderscale", prefs.getString("renderscale", "0.75")));
    }

    /** DaggerfallGameFiles.zip's arena2/ entries unpacked flat into the ARENA2 folder. */
    private void download() {
        save();
        File dst = arena2();
        if (!canRead(dst)) return;
        unzip("Файлы Daggerfall", DATA_ZIP, dst, n -> {
            if (!n.toLowerCase(Locale.ROOT).startsWith("arena2/")) return null;
            String base = n.substring(7);
            return base.isEmpty() || base.contains("/") ? null : base;
        }, null);
    }

    /** The offline build unpacked whole into web.part, which replaces web only once every entry is out. */
    private void downloadWeb() {
        File web = webDir(), part = new File(web.getPath() + ".part");
        deleteTree(part);
        unzip("Офлайн-версия", WEB_ZIP, part, n -> n, () -> {
            deleteTree(web);
            return part.renameTo(web) ? null : "не удалось переложить файлы";
        });
    }

    interface NameMap { String map(String entry); }       // the entry's path under the folder, or null to skip it
    interface Finish { String run(); }                    // after a whole archive: an error, or null

    /** A zip from url unpacked into dst straight from the download stream (no copy of the archive on the phone). */
    private void unzip(String title, String url, File dst, NameMap names, Finish finish) {
        AlertDialog wait = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title).setMessage("Скачиваю…").setCancelable(false).show();
        new Thread(() -> {
            String err = null;
            int files = 0;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true);
                if (c.getResponseCode() != 200) throw new java.io.IOException("сервер ответил " + c.getResponseCode());
                String root = dst.getCanonicalPath() + File.separator;
                byte[] buf = new byte[1 << 16];
                long got = 0;
                try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(c.getInputStream(), 1 << 16))) {
                    ZipEntry e;
                    while ((e = z.getNextEntry()) != null) {
                        if (e.isDirectory()) continue;
                        String rel = names.map(e.getName().replace('\\', '/'));
                        if (rel == null) continue;
                        File out = new File(dst, rel);
                        if (!out.getCanonicalPath().startsWith(root)) continue;   // no entry may leave the folder
                        out.getParentFile().mkdirs();
                        try (OutputStream o = new FileOutputStream(out)) {
                            for (int k; (k = z.read(buf)) > 0; ) { o.write(buf, 0, k); got += k; }
                        }
                        files++;
                        final long mb = got >> 20;
                        final int nf = files;
                        if (nf % 50 == 0) ui.post(() -> wait.setMessage("Распаковано файлов: " + nf + " (" + mb + " МБ)"));
                    }
                }
                if (files == 0) err = "в архиве нет нужных файлов";
                else if (finish != null) err = finish.run();
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String e = err;
            ui.post(() -> {
                wait.dismiss();
                refresh();
                if (e != null) new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Не удалось скачать").setMessage(e + "\n\nПроверьте интернет и попробуйте ещё раз.")
                    .setPositiveButton("OK", null).show();
            });
        }, "download").start();
    }

    private static void deleteTree(File f) {
        if (f == null) return;
        java.nio.file.Path p = f.toPath();
        if (!java.nio.file.Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        File[] kids = java.nio.file.Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS) ? f.listFiles() : null;
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    // ------------------------------------------------------------------ UI helpers (the Blizz4A look)

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(2), stroke);
        return d;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.SERIF, Typeface.BOLD);
        return t;
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(primary ? 0xFF1A1408 : PAPER);
        b.setBackground(shape(primary ? GOLD : 0xFF2A2F38, 12, primary ? 0 : 0xFF4A4F58));
        b.setPadding(dp(18), dp(12), dp(18), dp(12));
        return b;
    }

    private LinearLayout card(String title, String subtitle) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(18), dp(20), dp(18));
        c.setBackground(shape(CARD, 18, 0xFF3A3326));
        c.addView(label(title, 20, GOLD, true));
        if (subtitle != null) {
            TextView s = label(subtitle, 13, DIM, false);
            s.setPadding(0, dp(2), 0, dp(8));
            c.addView(s);
        }
        return c;
    }

    /** A row of choices kept in prefs under key (the chosen value string). */
    private android.widget.RadioGroup choice(String key, String[] labels, String[] values, String def) {
        android.widget.RadioGroup g = new android.widget.RadioGroup(this);
        g.setOrientation(LinearLayout.HORIZONTAL);
        String now = prefs.getString(key, def);
        for (int i = 0; i < labels.length; i++) {
            android.widget.RadioButton b = new android.widget.RadioButton(this);
            b.setText(labels[i]);
            b.setTextColor(PAPER);
            b.setId(android.view.View.generateViewId());
            b.setPadding(0, 0, dp(14), 0);
            final String v = values[i];
            b.setOnClickListener(x -> prefs.edit().putString(key, v).apply());
            g.addView(b);
            if (v.equals(now)) b.setChecked(true);
        }
        return g;
    }

    private LinearLayout.LayoutParams spaced(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        return lp;
    }
}
