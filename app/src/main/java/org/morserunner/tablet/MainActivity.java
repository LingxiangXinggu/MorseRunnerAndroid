// SPDX-License-Identifier: MPL-2.0
package org.morserunner.tablet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.text.method.DigitsKeyListener;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG = 0xfff5f7fb, PANEL = 0xffffffff, FG = 0xff1b2b43, MUTED = 0xff63738a, ACCENT = 0xff3268cf;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private NativeEngine engine;
    private AudioPlayer audio;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private SharedPreferences prefs;
    private EditText call, rst, number;
    private TextView status, scores, rawScores, transmit, logView, subtitle, callsignInfo;
    private AlertDialog settingsDialog;
    private Spinner settingsLibrary;
    private View scorePanel, logHeading;
    private View trainingHeader, trainingActions;
    private TrainingKeyboard trainingKeyboard;
    private EditText lastTrainingField;
    private boolean compactInputStyle;
    private Runnable unregisterKeyboardBack;
    private ScrollView logScroll;
    private boolean scrollLogToLatest;
    private CallsignInfo info;
    private Button run;
    private String lastRows = "", csvToExport = "";
    private int generation;
    private boolean active, resetting;
    private final String[] modes = {"Pile-Up", "Single Calls", "WPX Competition", "HST Competition"};
    private final String[] libraries = {"WPX · 50,263", "WAPC · 585", "原库 · 59,942", "导入的 SCP"};
    private final String[] assets = {"wpx.scp", "wapc.scp", "original.scp"};
    private final Runnable refresh = new Runnable() {
        @Override public void run() { update(); handler.postDelayed(this, 200); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        // Previous versions saved QSK enabled by default. Disable it once on upgrade;
        // later explicit user choices remain untouched, as do all other effect bits.
        if (!prefs.getBoolean("qskDefaultOffApplied",false))
            prefs.edit().putInt("flags",prefs.getInt("flags",0)&~32).putBoolean("qskDefaultOffApplied",true).apply();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        // Native views honor IME insets without an additional UI runtime.
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        buildUi();
        try { info = new CallsignInfo(read(getAssets().open("ARRL.LIST"))); } catch (IOException e) { toast("国家前缀表读取失败"); }
        if (saved != null) { call.setText(saved.getString("call", "")); rst.setText(saved.getString("rst", "599")); number.setText(saved.getString("number", "")); }
        handler.post(refresh);
        handler.post(() -> { if (!prefs.getBoolean("setupComplete", false)) settings(true); });
    }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setPadding(dp(8), dp(5), dp(8), dp(5)); return t;
    }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(android.view.Gravity.CENTER_VERTICAL); return r; }
    private GradientDrawable background(int color) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(6)); return d; }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setTextColor(FG); b.setTextSize(14); b.setAllCaps(false);
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setMinimumWidth(0); b.setMinWidth(0); b.setPadding(dp(10), dp(8), dp(10), dp(8));
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x223268cf), background(PANEL), null));
        b.setStateListAnimator(null); b.setElevation(0); b.setTranslationZ(0);
        b.setOnClickListener(v -> action.run()); b.setFocusable(false); return b;
    }
    private void primary(Button b) {
        b.setTextColor(Color.WHITE);
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff), background(ACCENT), null));
    }
    private void showMore(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "训练设置");
        menu.getMenu().add(0, 2, 1, "导出 CSV");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) settings(); else if (item.getItemId() == 2) exportLog();
            return true;
        });
        menu.show();
    }
    private void weight(LinearLayout parent, View child, float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, w); lp.setMargins(dp(3), dp(4), dp(3), dp(4)); parent.addView(child, lp);
    }
    private TextView scoreCard(LinearLayout parent, String label, boolean emphasis) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setBackground(background(emphasis ? 0xffeaf1ff : PANEL)); card.addView(text(label, 12, MUTED));
        TextView value = text("0", 24, emphasis ? ACCENT : FG); value.setTypeface(Typeface.DEFAULT, Typeface.BOLD); card.addView(value); weight(parent, card, 1); return value;
    }
    private void setupSummary() {
        if (!prefs.getBoolean("setupComplete", false)) { subtitle.setText("完成设置，开始你的第一次训练"); return; }
        subtitle.setText(prefs.getString("myCall", "") + "  ·  " + modes[prefs.getInt("mode", 0)] + "  ·  " + libraries[prefs.getInt("library", 0)]);
        status.setText("本台 " + prefs.getInt("wpm", 30) + " WPM  ·  其他电台 " + prefs.getInt("dxMin", 15) + "–" + prefs.getInt("dxMax", 30) + " WPM");
    }
    private void compactLayout(int available) {
        if (available <= 0) return;
        boolean embedded = trainingKeyboard != null && trainingKeyboard.getVisibility() == View.VISIBLE;
        if (trainingKeyboard != null) trainingKeyboard.setCompact(available < dp(640));
        int detail = embedded || available < dp(520) ? View.GONE : View.VISIBLE;
        if (scorePanel != null) scorePanel.setVisibility(detail);
        if (subtitle != null) subtitle.setVisibility(detail);
        if (transmit != null) transmit.setVisibility(detail);
        if (callsignInfo != null) callsignInfo.setVisibility(detail);
        if (logHeading != null) logHeading.setVisibility(embedded || available < dp(420) ? View.GONE : View.VISIBLE);
        if (trainingActions != null) trainingActions.setVisibility(embedded ? View.GONE : View.VISIBLE);
        boolean tiny = embedded && available < dp(560);
        if (trainingHeader != null) trainingHeader.setVisibility(tiny ? View.GONE : View.VISIBLE);
        if (status != null) status.setVisibility(tiny ? View.GONE : View.VISIBLE);
        if (compactInputStyle != tiny && call != null) {
            compactInputStyle = tiny;
            for (EditText field : new EditText[]{call,rst,number}) {
                field.setTextSize(tiny ? 18 : 22); field.setPadding(dp(12),dp(tiny ? 6 : 10),dp(12),dp(tiny ? 6 : 10));
            }
        }
    }
    private EditText input(String initial, boolean numeric) {
        EditText t = new EditText(this); t.setSingleLine(true); t.setTextColor(FG); t.setTextSize(22); t.setTypeface(Typeface.MONOSPACE); t.setSelectAllOnFocus(true);
        t.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        if (numeric) {
            t.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
            t.setFilters(new InputFilter[]{DigitsKeyListener.getInstance("0123456789"), new InputFilter.LengthFilter(5)});
        } else {
            // Keep the text key listener installed by setInputType: DigitsKeyListener
            // changes the advertised IME class back to NUMBER, even with letters allowed.
            InputFilter callsignCharacters = (source, start, end, dest, dstart, dend) -> {
                StringBuilder accepted = new StringBuilder();
                for (int i = start; i < end; i++) {
                    char c = source.charAt(i);
                    if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '?') accepted.append(c);
                }
                return accepted.length() == end - start ? null : accepted.toString();
            };
            t.setFilters(new InputFilter[]{new InputFilter.AllCaps(), callsignCharacters, new InputFilter.LengthFilter(24)});
        }
        t.setImeHintLocales(new android.os.LocaleList(Locale.ENGLISH));
        t.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_FORCE_ASCII);
        t.setOnEditorActionListener((v, action, event) -> { if (event == null && action == EditorInfo.IME_ACTION_GO) { enter(false); return true; } return false; });
        android.graphics.drawable.StateListDrawable backgrounds = new android.graphics.drawable.StateListDrawable();
        GradientDrawable focused = background(PANEL); focused.setStroke(dp(2), ACCENT);
        GradientDrawable normal = background(PANEL); normal.setStroke(dp(1), 0xffdce3ed);
        backgrounds.addState(new int[]{android.R.attr.state_focused}, focused); backgrounds.addState(new int[]{}, normal);
        t.setBackground(backgrounds); t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setText(initial); return t;
    }
    private void buildUi() {
        LinearLayout root = new LinearLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                compactLayout(View.MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom());
                super.onMeasure(widthSpec, heightSpec);
            }
        }; root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12), dp(8), dp(12), dp(8)); root.setBackgroundColor(BG);
        if (android.os.Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            android.graphics.Insets ime = insets.getInsets(android.view.WindowInsets.Type.ime());
            view.setPadding(dp(12) + bars.left, dp(8) + bars.top, dp(12) + bars.right, dp(8) + Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        LinearLayout title = row(); trainingHeader = title;
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL);
        TextView name = text("Morse Runner", 24, FG); name.setTypeface(Typeface.DEFAULT, Typeface.BOLD); names.addView(name);
        subtitle = text("专注每一次通联", 12, MUTED); names.addView(subtitle); weight(title, names, 1);
        Button more = button("更多", () -> {}); more.setOnClickListener(v -> showMore(v)); title.addView(more);
        run = button("开始训练", this::toggle); primary(run); title.addView(run); root.addView(title);
        status = text("准备就绪", 13, MUTED); root.addView(status);
        setupSummary();
        LinearLayout scoreRow = row(); scorePanel = scoreRow; scores = scoreCard(scoreRow, "已核验得分 · Verified", true); rawScores = scoreCard(scoreRow, "录入得分 · Raw", false); root.addView(scoreRow);
        LinearLayout logTitle = row(); logHeading = logTitle; weight(logTitle, text("通联记录", 15, FG), 1); root.addView(logTitle);
        logView = text("TIME      CALL           RST  NR   SENT  PREFIX  CHK\n", 15, FG); logView.setTypeface(Typeface.MONOSPACE);
        HorizontalScrollView horizontal = new HorizontalScrollView(this); horizontal.addView(logView);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackground(background(PANEL)); scroll.setPadding(dp(8), dp(8), dp(8), dp(8)); scroll.addView(horizontal); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        logScroll = scroll;
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!scrollLogToLatest) return;
            scrollLogToLatest = false;
            int bottom = logScroll.getChildAt(0).getHeight() - logScroll.getHeight() + logScroll.getPaddingTop() + logScroll.getPaddingBottom();
            // Scroll after text and both scroll containers have finished layout; do not move input focus.
            logScroll.scrollTo(logScroll.getScrollX(), Math.max(0, bottom));
        });
        transmit = text("TX —", 14, MUTED); transmit.setMaxLines(1); root.addView(transmit);
        LinearLayout inputs = row();
        call = input("", false); rst = input("599", true); number = input("", true);
        addField(inputs, "CALL", call, 3); addField(inputs, "RST", rst, 1.2f); addField(inputs, "NR", number, 1.4f); root.addView(inputs);
        callsignInfo = text("", 12, MUTED); callsignInfo.setMaxLines(1); root.addView(callsignInfo);
        call.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { if (!resetting && engine != null) engine.command(3, s.toString(), 0, 0); if (info != null) callsignInfo.setText(info.lookup(s.toString())); }
            public void afterTextChanged(Editable s) { }
        });
        String[] labels = {"CQ · F1", "NR · F2", "TU · F3", "MY · F4", "HIS · F5", "B4 · F6", "? · F7", "NIL · F8"};
        Button[] macros = new Button[labels.length];
        for (int i = 0; i < labels.length; i++) { final int key = i; macros[i] = button(labels[i], () -> macro(key)); }
        root.addView(new ShortcutBar(this, macros));
        LinearLayout actions = row(); trainingActions = actions;
        weight(actions, button("键盘", this::toggleTrainingKeyboard), 1);
        weight(actions, button("停发", () -> command(2)), 1); weight(actions, button("清空", this::wipe), 1);
        Button send = button("发送 / Enter", () -> enter(false)); primary(send); weight(actions, send, 1.8f); root.addView(actions);
        trainingKeyboard = new TrainingKeyboard(this, new TrainingKeyboard.Listener() {
            public void insert(String value) { insertTrainingText(value); }
            public void delete() { deleteTrainingText(); }
            public void clearField() { trainingTarget().getText().clear(); }
            public void focusField(int field) { trainingFields()[field].requestFocus(); showTrainingKeyboard(); }
            public void clearAll() { wipe(); }
            public void stopSending() { command(2); }
            public void toggleTraining() { toggle(); }
            public void hide() { hideTrainingKeyboard(); }
            public void send() { enter(false); }
        });
        trainingKeyboard.setVisibility(View.GONE); root.addView(trainingKeyboard);
        EditText[] fields = trainingFields();
        for (int i=0;i<fields.length;i++) {
            final EditText field=fields[i]; final int index=i;
            field.setOnFocusChangeListener((v,focused)->{
                if (focused) { lastTrainingField=field; trainingKeyboard.setField(index);
                    if (usesBuiltInKeyboard() && prefs.getBoolean("setupComplete",false) && settingsDialog==null) showTrainingKeyboard(); }
            });
            field.setOnClickListener(v->{lastTrainingField=field; showTrainingKeyboard();});
        }
        configureTrainingKeyboard();
        setContentView(root); call.requestFocus();
    }
    private boolean usesBuiltInKeyboard() { return prefs.getInt("keyboardMode",0)==1; }
    private EditText[] trainingFields() { return new EditText[]{call,rst,number}; }
    private EditText trainingTarget() {
        for (EditText field:trainingFields()) if (field.hasFocus()) return field;
        return lastTrainingField==null ? call : lastTrainingField;
    }
    private void configureTrainingKeyboard() {
        for (EditText field:trainingFields()) field.setShowSoftInputOnFocus(!usesBuiltInKeyboard());
        if (!usesBuiltInKeyboard()) hideTrainingKeyboard();
    }
    private void showTrainingKeyboard() {
        if (!prefs.getBoolean("setupComplete",false) || settingsDialog!=null) return;
        EditText target=trainingTarget(); if (!target.hasFocus()) target.requestFocus();
        if (usesBuiltInKeyboard()) {
            if (target.getWindowToken()!=null) ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(target.getWindowToken(),0);
            trainingKeyboard.setField(target==call ? 0 : target==rst ? 1 : 2); trainingKeyboard.setVisibility(View.VISIBLE);
            if (android.os.Build.VERSION.SDK_INT>=33 && unregisterKeyboardBack==null)
                unregisterKeyboardBack=KeyboardBack.register(this,this::hideTrainingKeyboard);
        } else {
            hideTrainingKeyboard(); ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(target,InputMethodManager.SHOW_IMPLICIT);
        }
    }
    private void hideTrainingKeyboard() {
        if (trainingKeyboard!=null) trainingKeyboard.setVisibility(View.GONE);
        Runnable cleanup=unregisterKeyboardBack; unregisterKeyboardBack=null; if(cleanup!=null) cleanup.run();
    }
    @android.annotation.TargetApi(33)
    private static final class KeyboardBack {
        static Runnable register(Activity activity,Runnable back) {
            android.window.OnBackInvokedCallback callback=back::run;
            android.window.OnBackInvokedDispatcher dispatcher=activity.getOnBackInvokedDispatcher();
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback);
            return ()->dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }
    private void toggleTrainingKeyboard() {
        if (usesBuiltInKeyboard() && trainingKeyboard.getVisibility()==View.VISIBLE) hideTrainingKeyboard(); else showTrainingKeyboard();
    }
    private void insertTrainingText(String value) {
        EditText field=trainingTarget(); Editable text=field.getText();
        int start=field.getSelectionStart(),end=field.getSelectionEnd(); if(start<0 || end<0) start=end=text.length();
        int low=Math.min(start,end),high=Math.max(start,end),old=text.length();
        text.replace(low,high,value); field.setSelection(Math.min(text.length(),low+text.length()-(old-(high-low))));
    }
    private void deleteTrainingText() {
        EditText field=trainingTarget(); Editable text=field.getText();
        int start=field.getSelectionStart(),end=field.getSelectionEnd(); if(start<0 || end<0) start=end=text.length();
        int low=Math.min(start,end),high=Math.max(start,end);
        if(low!=high) text.delete(low,high); else if(low>0) { text.delete(low-1,low); low--; }
        field.setSelection(low);
    }
    private void addField(LinearLayout parent, String label, EditText field, float w) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.addView(text(label, 12, MUTED)); box.addView(field); weight(parent, box, w);
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int numeric(EditText field) { try { return Integer.parseInt(field.getText().toString()); } catch (NumberFormatException e) { return 0; } }
    private boolean ready() { if (!active || engine == null) { toast("请先开始训练"); return false; } return true; }
    private void command(int cmd) { if (ready()) engine.command(cmd, call.getText().toString(), numeric(rst), numeric(number)); }
    private void macro(int key) { int[] messages = {1, 2, 3, 4, 5, 6, 7, 8}; command(100 + messages[key]); }
    private void enter(boolean saveOnly) {
        if (!ready()) return;
        if (engine.command(saveOnly ? 1 : 0, call.getText().toString(), numeric(rst), numeric(number))) wipe();
        else if (saveOnly) toast("请检查 CALL、三位 RST 和 NR");
    }
    private void wipe() { resetting = true; call.setText(""); rst.setText("599"); number.setText(""); resetting = false; if (engine != null) engine.command(4, "", 0, 0); call.requestFocus(); }
    private void space() { if (rst.getText().length() == 0) rst.setText("599"); if (number.hasFocus()) call.requestFocus(); else number.requestFocus(); }
    private void toggle() { if (active) stopSession("训练已停止"); else startSession(); }
    private String read(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n; while ((n = input.read(b)) != -1) { out.write(b, 0, n); if (out.size() > 4 * 1024 * 1024) throw new IOException("文件超过 4 MB"); } return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    private void startSession() {
        if (!prefs.getBoolean("setupComplete", false)) { settings(true); return; }
        int token = ++generation;
        try {
            int lib = prefs.getInt("library", 0);
            String data = lib == 3 ? read(openFileInput("custom.scp")) : read(getAssets().open("calls/" + assets[lib]));
            TrainingProfile profile = new TrainingProfile(prefs.getString("myCall", ""), prefs.getInt("wpm", 30), prefs.getInt("dxMin", 15), prefs.getInt("dxMax", 30), prefs.getInt("mode", 0), lib);
            int[] options = profile.options(prefs.getInt("activity", 3), prefs.getInt("duration", 10), prefs.getInt("pitch", 600), prefs.getInt("bandwidth", 500), prefs.getInt("flags", 0), prefs.getInt("monitor", -20));
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(change -> { if (change < 0 && active && token == generation) stopSession("音频焦点丢失，训练已停止"); }, handler).build();
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) throw new IOException("无法取得音频焦点");
            if (engine != null) { engine.close(); engine = null; }
            engine = new NativeEngine(profile.call, data, options);
            audio = new AudioPlayer(engine, () -> handler.post(() -> { if (token == generation) stopSession("训练结束"); }), message -> handler.post(() -> { if (token == generation) { stopSession("音频错误"); toast(message == null ? "音频错误" : message); } }));
            active = true; lastRows = ""; wipe(); run.setText("停止"); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); audio.start();
            showTrainingKeyboard();
            subtitle.setText(modes[options[3] - 1] + " · " + libraries[lib] + " · " + prefs.getString("myCall", "N0CALL"));
        } catch (Exception | LinkageError error) { stopSession("启动失败"); toast("启动失败：" + error.getMessage()); }
    }
    private void saveLocalLog() {
        if (engine == null) return;
        try (OutputStream out = openFileOutput("last-session.csv", MODE_PRIVATE)) { out.write(engine.export().getBytes(StandardCharsets.UTF_8)); }
        catch (IOException e) { toast("训练记录保存失败：" + e.getMessage()); }
    }
    private void stopSession(String reason) {
        generation++; active = false;
        if (audio != null) { audio.stop(); audio = null; }
        if (engine != null) { engine.command(5, "", 0, 0); saveLocalLog(); }
        if (focusRequest != null) { audioManager.abandonAudioFocusRequest(focusRequest); focusRequest = null; }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); run.setText("开始训练"); status.setText(reason);
    }
    private void update() {
        if (engine == null) return;
        try {
            JSONObject s = new JSONObject(engine.state());
            if (active) status.setText(String.format(Locale.ROOT, "%02d:%02d  ·  本台 %d WPM  ·  %d 台  ·  TX %03d  ·  %d QSO/h  ·  RIT %+d  ·  得分 %d", s.getInt("seconds") / 60, s.getInt("seconds") % 60, s.getInt("wpm"), s.getInt("pileup"), s.getInt("serial"), s.getInt("rate"), s.getInt("rit"), s.getInt("score")));
            scores.setText(String.valueOf(s.getInt("score"))); rawScores.setText(String.valueOf(s.getInt("rawScore")));
            scores.setContentDescription("已核验得分 " + s.getInt("score") + "，点数 " + s.getInt("points") + "，乘数 " + s.getInt("mult"));
            rawScores.setContentDescription("录入得分 " + s.getInt("rawScore") + "，点数 " + s.getInt("rawPoints") + "，乘数 " + s.getInt("rawMult"));
            transmit.setText("TX " + (s.getString("tx").isEmpty() ? "—" : s.getString("tx")));
            if (active && s.optBoolean("advance") && !call.getText().toString().contains("?")) number.requestFocus();
            updateLog(s.getJSONArray("log"));
        } catch (Exception e) { if (active) { stopSession("状态读取失败"); toast(e.getMessage()); } }
    }
    void updateLog(JSONArray rows) throws org.json.JSONException {
            if (!lastRows.equals(rows.toString())) {
                lastRows = rows.toString(); StringBuilder out = new StringBuilder("TIME      CALL           RST  NR    SENT  PREFIX CHK\n");
                for (int i = 0; i < rows.length(); i++) { JSONObject q = rows.getJSONObject(i); int t = q.getInt("time"); out.append(String.format(Locale.ROOT, "%02d:%02d     %-14s %03d  %04d  %04d  %-5s %s\n", t / 60, t % 60, q.getString("call"), q.getInt("rst"), q.getInt("nr"), q.getInt("sent"), q.getString("prefix"), q.getString("error"))); }
                scrollLogToLatest = true;
                logView.setText(out.toString());
                // At the 100-row cap text may change without changing its height.
                logScroll.requestLayout();
            }
    }
    private void settings() { settings(!prefs.getBoolean("setupComplete", false)); }
    private void settings(boolean firstRun) {
        if (active) { toast("请先停止训练，再更改设置"); return; }
        if (settingsDialog != null && settingsDialog.isShowing()) return;
        hideTrainingKeyboard();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), dp(8), dp(18), dp(12)); scroll.addView(box);
        box.addView(text(firstRun ? "欢迎！先设置你的电台和训练偏好。之后随时可以在设置中调整。" : "选择适合自己的训练节奏", 14, MUTED));
        box.addView(text("训练输入键盘",14,MUTED));
        RadioGroup keyboardChoice=new RadioGroup(this); keyboardChoice.setOrientation(RadioGroup.VERTICAL);
        RadioButton systemKeyboard=new RadioButton(this); systemKeyboard.setId(View.generateViewId()); systemKeyboard.setText("系统键盘"); systemKeyboard.setTextColor(FG); keyboardChoice.addView(systemKeyboard);
        RadioButton builtInKeyboard=new RadioButton(this); builtInKeyboard.setId(View.generateViewId()); builtInKeyboard.setText("内置键盘 · 适合触屏抄报"); builtInKeyboard.setTextColor(FG); keyboardChoice.addView(builtInKeyboard);
        keyboardChoice.check(usesBuiltInKeyboard() ? builtInKeyboard.getId() : systemKeyboard.getId()); box.addView(keyboardChoice);
        box.addView(text("内置键盘有字母、数字和字段切换；长按退格可清空当前字段。外接键盘始终可用，设置页仍用系统键盘。",12,MUTED));
        EditText my = settingField(box, "本台呼号", prefs.getString("myCall", ""), false); my.setHint("例如 BD6IUT");
        EditText wpm = sliderField(box, "本台发报速度 · WPM（10–120）", prefs.getInt("wpm", 30), 10, 120);
        box.addView(text("其他电台速度 · WPM（5–120）", 14, MUTED));
        LinearLayout speeds = row();
        EditText dxMin = settingsInput("" + prefs.getInt("dxMin", Math.max(5, prefs.getInt("wpm", 30) / 2)), true);
        EditText dxMax = settingsInput("" + prefs.getInt("dxMax", prefs.getInt("wpm", 30)), true);
        addSpeedField(speeds,"最低",dxMin); addSpeedField(speeds,"最高",dxMax); box.addView(speeds);
        box.addView(text("来呼电台与 QRM 台在此范围内随机发报；最低和最高相同即为固定速度。PgUp / PgDn 只调整本台速度。", 12, MUTED));
        box.addView(text("训练／比赛模式", 14, MUTED)); Spinner mode = spinner(modes, prefs.getInt("mode", 0)); box.addView(mode);
        box.addView(text("Pile-Up：多台呼叫  ·  Single Calls：单台练习\nWPX：竞赛计分  ·  HST：高速抄收", 12, MUTED));
        box.addView(text("呼号库", 14, MUTED)); Spinner library = spinner(libraries, prefs.getInt("library", 0)); settingsLibrary = library; box.addView(library);
        box.addView(button("导入自己的 SCP 呼号库", () -> { Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(intent, 12); }));
        EditText dur = sliderField(box, "每轮时长 · 分钟（1–60）", prefs.getInt("duration", 10), 1, 60);
        LinearLayout advanced = new LinearLayout(this); advanced.setOrientation(LinearLayout.VERTICAL); advanced.setVisibility(View.GONE);
        Button expand = button("高级设置  ＋", () -> { advanced.setVisibility(advanced.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); });
        box.addView(expand); box.addView(advanced);
        EditText act = sliderField(advanced, "活跃度（1–10）", prefs.getInt("activity", 3), 1, 10);
        EditText pitch = sliderField(advanced, "音调 · Hz（300–1000）", prefs.getInt("pitch", 600), 300, 1000);
        EditText bw = sliderField(advanced, "带宽 · Hz（100–1000）", prefs.getInt("bandwidth", 500), 100, 1000);
        EditText mon = sliderField(advanced, "侧音衰减 · dB（0–40）", -prefs.getInt("monitor", -20), 0, 40);
        String[] labels = {"QRN 静电干扰", "QRM 电台干扰", "QSB 信号衰落", "Flutter 快速衰落", "LIDS 操作失误", "QSK 插入接收"}; CheckBox[] flags = new CheckBox[6];
        for (int i = 0; i < flags.length; i++) { flags[i] = new CheckBox(this); flags[i].setText(labels[i]); flags[i].setTextColor(FG); flags[i].setChecked((prefs.getInt("flags", 0) & (1 << i)) != 0); advanced.addView(flags[i]); }
        advanced.addView(text("WPX 开启全部电波效果；HST 关闭效果、使用 4 个台站及 600 Hz 带宽。各模式均使用你设置的电台速度范围。", 12, MUTED));
        box.addView(text("Morse Runner · 离线 CW 训练\n基于 BH1SCW/MorseRunner 1.71 · MPL-2.0", 12, MUTED));
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(firstRun ? "欢迎使用 Morse Runner" : "训练设置").setView(scroll).setPositiveButton(firstRun ? "完成设置" : "保存设置", null);
        if (!firstRun) builder.setNegativeButton("取消", null);
        AlertDialog dialog = builder.create(); settingsDialog = dialog; dialog.setCancelable(!firstRun); dialog.setCanceledOnTouchOutside(!firstRun);
        dialog.setOnDismissListener(v -> { settingsDialog = null; settingsLibrary = null; configureTrainingKeyboard();
            if (!isFinishing() && !isDestroyed() && usesBuiltInKeyboard()) showTrainingKeyboard(); });
        dialog.setOnShowListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    TrainingProfile profile = new TrainingProfile(my.getText().toString(), range(wpm, 10, 120), range(dxMin, 5, 120), range(dxMax, 5, 120), mode.getSelectedItemPosition(), library.getSelectedItemPosition());
                    int activity = range(act, 1, 10), duration = range(dur, 1, 60), tone = range(pitch, 300, 1000), width = range(bw, 100, 1000), monitor = range(mon, 0, 40);
                    if (profile.library == 3 && !getFileStreamPath("custom.scp").exists()) throw new IllegalArgumentException("请先导入 SCP 文件，或选择内置呼号库");
                    int bits = 0; for (int i = 0; i < flags.length; i++) if (flags[i].isChecked()) bits |= 1 << i;
                    prefs.edit().putString("myCall", profile.call).putInt("wpm", profile.txWpm).putInt("dxMin", profile.dxMin).putInt("dxMax", profile.dxMax).putInt("activity", activity).putInt("duration", duration).putInt("pitch", tone).putInt("bandwidth", width).putInt("monitor", -monitor).putInt("mode", profile.mode).putInt("library", profile.library).putInt("flags", bits).putInt("keyboardMode",keyboardChoice.getCheckedRadioButtonId()==builtInKeyboard.getId() ? 1 : 0).putBoolean("setupComplete", true).apply();
                    setupSummary(); dialog.dismiss(); call.requestFocus();
                    ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(call.getWindowToken(), 0);
                    configureTrainingKeyboard(); if (usesBuiltInKeyboard()) showTrainingKeyboard();
                    if (firstRun) toast("设置完成，点击“开始训练”即可开始");
                } catch (IllegalArgumentException e) { toast(e.getMessage()); }
            });
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(640)), -2);
        }
    }
    private EditText settingsInput(String initial, boolean numeric) {
        EditText field = input(initial, numeric); field.setTextSize(18); field.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_FORCE_ASCII); field.setOnEditorActionListener(null); return field;
    }
    private EditText settingField(LinearLayout parent, String label, String initial, boolean numeric) { parent.addView(text(label, 14, MUTED)); EditText field = settingsInput(initial, numeric); parent.addView(field); return field; }
    private EditText sliderField(LinearLayout parent,String label,int initial,int min,int max) {
        EditText field=settingField(parent,label,String.valueOf(initial),true);
        parent.addView(new SettingSlider(this,field,label,min,max)); return field;
    }
    private void addSpeedField(LinearLayout parent,String label,EditText field) {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(label,12,MUTED));box.addView(field);box.addView(new SettingSlider(this,field,"其他电台"+label+" WPM",5,120));weight(parent,box,1);
    }
    private Spinner spinner(String[] items, int index) { Spinner s = new Spinner(this); ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items); s.setAdapter(a); s.setSelection(index); return s; }
    private int range(EditText field, int min, int max) { int value; try { value = Integer.parseInt(field.getText().toString()); } catch (NumberFormatException e) { throw new IllegalArgumentException("请填写所有数字设置"); } if (value < min || value > max) throw new IllegalArgumentException("数值范围为 " + min + "–" + max); return value; }
    private void exportLog() {
        try { csvToExport = engine != null ? engine.export() : read(openFileInput("last-session.csv")); }
        catch (IOException e) { toast("尚无训练记录"); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.setType("text/csv"); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.putExtra(Intent.EXTRA_TITLE, "MorseRunner-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".csv"); startActivityForResult(intent, 11);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if (result != RESULT_OK || data == null || data.getData() == null) return; Uri uri = data.getData();
        try {
            if (request == 11) { try (OutputStream out = getContentResolver().openOutputStream(uri)) { if (out == null) throw new IOException("无法打开文件"); out.write(csvToExport.getBytes(StandardCharsets.UTF_8)); } toast("CSV 已导出"); }
            if (request == 12) { String imported = read(getContentResolver().openInputStream(uri));
                if (imported.indexOf('\0') >= 0 || !imported.matches("(?s).*[A-Za-z0-9].*")) throw new IOException("请选择文本 SCP 呼号库");
                // Validate with the same parser used by training before replacing the current import.
                try (NativeEngine validation = new NativeEngine("N0CALL", imported, new int[]{30, 3, 10, 1, 600, 500, 0, -20})) { }
                try (OutputStream out = openFileOutput("custom.scp", MODE_PRIVATE)) { out.write(imported.getBytes(StandardCharsets.UTF_8)); }
                if (settingsLibrary != null) settingsLibrary.setSelection(3);
                toast("呼号库已导入，保存设置后生效");
            }
        } catch (Exception | LinkageError e) { toast("文件处理失败：" + e.getMessage()); }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (settingsDialog != null && settingsDialog.isShowing()) return super.dispatchKeyEvent(event);
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if (key >= KeyEvent.KEYCODE_F1 && key <= KeyEvent.KEYCODE_F8) { if (event.getRepeatCount() == 0) macro(key - KeyEvent.KEYCODE_F1); return true; }
        if (key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER) { if (event.getRepeatCount() == 0) enter(event.isCtrlPressed() || event.isShiftPressed() || event.isAltPressed()); return true; }
        if (key == KeyEvent.KEYCODE_ESCAPE) { command(2); return true; }
        if (key == KeyEvent.KEYCODE_F11 || (key == KeyEvent.KEYCODE_W && (event.isCtrlPressed() || event.isAltPressed()))) { wipe(); return true; }
        if (key == KeyEvent.KEYCODE_SPACE) { space(); return true; }
        if (key == KeyEvent.KEYCODE_INSERT || event.getUnicodeChar() == ';') { macro(4); macro(1); return true; }
        if (event.getUnicodeChar() == '+' || event.getUnicodeChar() == '.' || event.getUnicodeChar() == ',' || event.getUnicodeChar() == '[') { if (ready() && engine.command(9, call.getText().toString(), numeric(rst), numeric(number))) wipe(); return true; }
        if (key == KeyEvent.KEYCODE_PAGE_UP || key == KeyEvent.KEYCODE_PAGE_DOWN || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (active && engine != null) try {
                JSONObject s = new JSONObject(engine.state()); int sign = key == KeyEvent.KEYCODE_PAGE_UP || key == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1;
                if (key == KeyEvent.KEYCODE_PAGE_UP || key == KeyEvent.KEYCODE_PAGE_DOWN) engine.command(6, "", s.getInt("wpm") + sign * 5, 0);
                else if (event.isCtrlPressed()) engine.command(7, "", s.getInt("bandwidth") + sign * 50, 0);
                else engine.command(8, "", s.getInt("rit") + sign * 10, 0);
            } catch (Exception ignored) { } return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("call", call.getText().toString()); out.putString("rst", rst.getText().toString()); out.putString("number", number.getText().toString()); }
    @Override protected void onPause() { if (active) stopSession("切换到后台，训练已停止并保存"); super.onPause(); }
    @Override public void onBackPressed() { if (trainingKeyboard!=null && trainingKeyboard.getVisibility()==View.VISIBLE) hideTrainingKeyboard(); else super.onBackPressed(); }
    @Override protected void onDestroy() { hideTrainingKeyboard(); if (settingsDialog != null) settingsDialog.dismiss(); handler.removeCallbacks(refresh); if (audio != null) audio.stop(); if (engine != null) engine.close(); super.onDestroy(); }
}
