package org.morserunner.tablet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Spinner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowPopupMenu;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w1000dp-h800dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
public class SetupFlowTest {
    private ActivityController<MainActivity> controller;
    private SharedPreferences prefs;
    @Before public void clear() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }
    @After public void close() { if (controller != null) controller.pause().stop().destroy(); }
    private MainActivity launch() {
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        assertEquals(0,controller.get().getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_SECURE);
        shadowOf(Looper.getMainLooper()).idle(); return controller.get();
    }
    private View text(View root, String value) {
        if (root instanceof TextView && value.contentEquals(((TextView)root).getText())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)root;
            for (int i = 0; i < group.getChildCount(); i++) { View found = text(group.getChildAt(i), value); if (found != null) return found; }
        }
        return null;
    }
    private EditText field(AlertDialog dialog, String label) {
        View v = text(dialog.getWindow().getDecorView(), label); assertNotNull(label, v);
        ViewGroup parent = (ViewGroup)v.getParent(); return (EditText)parent.getChildAt(parent.indexOfChild(v) + 1);
    }
    private void basics(AlertDialog dialog) {
        field(dialog,"本台呼号").setText("BD6IUT");
        field(dialog,"本台发报速度 · WPM（10–120）").setText("60");
        field(dialog,"最低").setText("15"); field(dialog,"最高").setText("25");
    }
    private void openSettings(View root) {
        assertNull(text(root,"导出 CSV"));
        text(root,"更多").performClick();
        android.widget.PopupMenu menu = ShadowPopupMenu.getLatestPopupMenu();
        assertEquals("导出 CSV", menu.getMenu().findItem(2).getTitle());
        menu.getMenu().performIdentifierAction(1,0); menu.dismiss();
    }
    private Spinner spinner(AlertDialog dialog, String label) {
        View v = text(dialog.getWindow().getDecorView(), label);
        ViewGroup parent = (ViewGroup)v.getParent(); return (Spinner)parent.getChildAt(parent.indexOfChild(v) + 1);
    }
    @Test public void firstLaunchRequiresValidSettingsThenPersistsIndependentSpeeds() {
        launch(); AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog(); assertTrue(dialog.isShowing());
        dialog.onBackPressed(); assertTrue(dialog.isShowing());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(prefs.getBoolean("setupComplete", false)); assertTrue(dialog.isShowing());
        basics(dialog); field(dialog,"最低").setText("40");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(prefs.getBoolean("setupComplete", false)); assertTrue(dialog.isShowing());
        field(dialog,"最低").setText("15");
        spinner(dialog,"训练／比赛模式").setSelection(3);
        spinner(dialog,"呼号库").setSelection(3);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(prefs.getBoolean("setupComplete", false)); assertTrue(dialog.isShowing());
        spinner(dialog,"呼号库").setSelection(1);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(prefs.getBoolean("setupComplete", false)); assertFalse(dialog.isShowing());
        assertEquals("BD6IUT", prefs.getString("myCall", "")); assertEquals(60, prefs.getInt("wpm", 0));
        assertEquals(15, prefs.getInt("dxMin", 0)); assertEquals(25, prefs.getInt("dxMax", 0));
        assertEquals(3, prefs.getInt("mode", -1)); assertEquals(1, prefs.getInt("library", -1));
        controller.pause().stop().destroy(); controller = null;
        launch(); assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing());
    }
    @Test public void upgradePrefillsPreviousSettingsWithoutMarkingSetupComplete() {
        prefs.edit().putString("myCall", "W1AW").putInt("wpm", 40).putInt("mode", 2).putInt("library", 1).commit();
        launch(); AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("W1AW", field(dialog,"本台呼号").getText().toString());
        assertEquals("40", field(dialog,"本台发报速度 · WPM（10–120）").getText().toString());
        assertEquals("20", field(dialog,"最低").getText().toString());
        assertEquals("40", field(dialog,"最高").getText().toString());
        assertFalse(prefs.getBoolean("setupComplete", false));
    }
    @Test public void ordinarySettingsCanCancelWithoutSaving() {
        prefs.edit().putBoolean("setupComplete", true).putString("myCall","K1ABC").putInt("wpm",30).commit();
        MainActivity activity=launch(); openSettings(activity.getWindow().getDecorView());
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog(); basics(dialog);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        assertEquals("K1ABC",prefs.getString("myCall","")); assertEquals(30,prefs.getInt("wpm",0));
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderFirstRunGuide() throws Exception {
        launch(); AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.isShowing());
        render(dialog.getWindow().getDecorView(),640,740,"welcome-light.png");
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderTabletAndCompactLayouts() throws Exception {
        prefs.edit().putBoolean("setupComplete", true).putString("myCall","BD6IUT").putInt("wpm",30).putInt("dxMin",15).putInt("dxMax",25).commit();
        MainActivity activity = launch();
        View root = activity.findViewById(android.R.id.content);
        render(root,1000,800,"tablet-light.png");
        View send = text(root,"发送 / Enter"); assertNotNull(send);
        assertTrue(send.getGlobalVisibleRect(new android.graphics.Rect()));
        render(root,400,850,"phone-light.png");
        render(root,1000,380,"compact-light.png");
        assertTrue(send.getGlobalVisibleRect(new android.graphics.Rect()));
        View summary = text(root,"已核验得分 · Verified");
        assertFalse(summary.isShown());
        openSettings(root);
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        render(dialog.getWindow().getDecorView(),640,740,"settings-light.png");
    }
    private void render(View root, int width, int height, String name) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        root.layout(0,0,width,height);
        if (root.isLayoutRequested()) {
            root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            root.layout(0,0,width,height);
        }
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(bitmap));
        java.io.File dir=new java.io.File("build/validation/ui"); dir.mkdirs();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out); }
        bitmap.recycle();
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void changedLogFollowsBottomAfterLayoutWithoutStealingInputFocus() throws Exception {
        prefs.edit().putBoolean("setupComplete",true).putString("myCall","BD6IUT").commit();
        MainActivity activity=launch(); View root=activity.findViewById(android.R.id.content);
        android.widget.ScrollView scroll=findLogScroll(root); assertNotNull(scroll);
        View label=text(root,"NR"); ViewGroup parent=(ViewGroup)label.getParent();
        EditText input=(EditText)parent.getChildAt(parent.indexOfChild(label)+1);
        render(root,1000,800,"log-empty.png"); input.requestFocus(); assertSame(input,root.findFocus());
        org.json.JSONArray rows=logRows(1,80);
        activity.updateLog(rows); render(root,1000,800,"log-follow.png");
        root.getViewTreeObserver().dispatchOnGlobalLayout();
        assertTrue(scroll.getScrollY()>0);
        assertTrue(scroll.getChildAt(0).getBottom()-scroll.getScrollY()<=scroll.getHeight()-scroll.getPaddingBottom());
        assertSame(input,root.findFocus());
        // An unchanged 200 ms snapshot must not pull the user back down while reading older entries.
        scroll.scrollTo(0,0); activity.updateLog(new org.json.JSONArray(rows.toString()));
        root.getViewTreeObserver().dispatchOnGlobalLayout(); assertEquals(0,scroll.getScrollY());
        // At the 100-row display cap the height stays the same, but the newest entry must still appear.
        activity.updateLog(logRows(1,100)); render(root,1000,800,"log-follow.png");
        root.getViewTreeObserver().dispatchOnGlobalLayout(); scroll.scrollTo(0,0);
        activity.updateLog(logRows(2,100)); render(root,1000,800,"log-follow.png");
        root.getViewTreeObserver().dispatchOnGlobalLayout();
        assertTrue(scroll.getScrollY()>0);
        assertTrue(scroll.getChildAt(0).getBottom()-scroll.getScrollY()<=scroll.getHeight()-scroll.getPaddingBottom());
        assertSame(input,root.findFocus());
        render(root,1000,800,"log-follow.png");
        activity.updateLog(new org.json.JSONArray()); render(root,1000,800,"log-reset.png");
        root.getViewTreeObserver().dispatchOnGlobalLayout(); assertEquals(0,scroll.getScrollY());
    }
    private android.widget.ScrollView findLogScroll(View view) {
        if(view instanceof android.widget.ScrollView)return (android.widget.ScrollView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            android.widget.ScrollView found=findLogScroll(((ViewGroup)view).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    private org.json.JSONArray logRows(int first,int count) throws Exception {
        org.json.JSONArray rows=new org.json.JSONArray();
        for(int i=first;i<first+count;i++)rows.put(new org.json.JSONObject().put("time",i*10).put("call","K"+i+"ABC").put("rst",599).put("nr",i).put("sent",i).put("prefix","K"+i).put("error",""));
        return rows;
    }
    private void assertLetterKeyboard(EditText field) {
        android.view.inputmethod.EditorInfo info=new android.view.inputmethod.EditorInfo();
        assertNotNull(field.onCreateInputConnection(info));
        assertEquals(android.text.InputType.TYPE_CLASS_TEXT, info.inputType & android.text.InputType.TYPE_MASK_CLASS);
        assertEquals(android.text.InputType.TYPE_TEXT_VARIATION_NORMAL, info.inputType & android.text.InputType.TYPE_MASK_VARIATION);
        assertTrue((info.imeOptions & android.view.inputmethod.EditorInfo.IME_FLAG_FORCE_ASCII)!=0);
        assertFalse(field.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod);
        assertEquals(android.text.InputType.TYPE_CLASS_TEXT, field.getKeyListener().getInputType() & android.text.InputType.TYPE_MASK_CLASS);
        assertNotNull(info.hintLocales); assertEquals("en",info.hintLocales.get(0).getLanguage());
        field.setText("bd6iut/qrp? 中 文"); assertEquals("BD6IUT/QRP?",field.getText().toString());
    }
    private void assertNumberKeyboard(EditText field) {
        android.view.inputmethod.EditorInfo info=new android.view.inputmethod.EditorInfo();
        assertNotNull(field.onCreateInputConnection(info));
        assertEquals(android.text.InputType.TYPE_CLASS_NUMBER, info.inputType & android.text.InputType.TYPE_MASK_CLASS);
        assertTrue((info.imeOptions & android.view.inputmethod.EditorInfo.IME_FLAG_FORCE_ASCII)!=0);
        assertEquals("en",info.hintLocales.get(0).getLanguage());
        field.setText("1A2B"); assertEquals("12",field.getText().toString());
    }
    private EditText mainField(View root,String label) {
        View v=text(root,label); ViewGroup parent=(ViewGroup)v.getParent();
        return (EditText)parent.getChildAt(parent.indexOfChild(v)+1);
    }
    @Test public void firstRunCallRequestsEnglishTextKeyboardAndSpeedsRemainNumeric() {
        launch(); AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        assertLetterKeyboard(field(dialog,"本台呼号"));
        assertNumberKeyboard(field(dialog,"本台发报速度 · WPM（10–120）"));
        assertNumberKeyboard(field(dialog,"最低")); assertNumberKeyboard(field(dialog,"最高"));
    }
    @Test public void trainingCallAndSettingsCallUseTextWhileRstAndSerialUseNumbers() {
        prefs.edit().putBoolean("setupComplete",true).putString("myCall","BD6IUT").commit();
        MainActivity activity=launch(); View root=activity.findViewById(android.R.id.content);
        assertLetterKeyboard(mainField(root,"CALL"));
        assertNumberKeyboard(mainField(root,"RST")); assertNumberKeyboard(mainField(root,"NR"));
        openSettings(root); shadowOf(Looper.getMainLooper()).idle();
        assertLetterKeyboard(field(ShadowAlertDialog.getLatestAlertDialog(),"本台呼号"));
    }
    @Test public void asciiRequestSurvivesRepeatedNrToCallTransitionsAndClears() {
        prefs.edit().putBoolean("setupComplete",true).putString("myCall","BD6IUT").commit();
        MainActivity activity=launch(); View root=activity.findViewById(android.R.id.content);
        EditText call=mainField(root,"CALL"), number=mainField(root,"NR");
        for(int i=0;i<8;i++){
            call.setText("K1ABC");number.requestFocus();number.setText(String.valueOf(i+1));
            android.view.inputmethod.EditorInfo numeric=new android.view.inputmethod.EditorInfo();number.onCreateInputConnection(numeric);
            assertTrue((numeric.imeOptions&android.view.inputmethod.EditorInfo.IME_FLAG_FORCE_ASCII)!=0);
            // The clear button uses the same wipe()/CALL focus path as a successful Enter QSO.
            text(root,"清空").performClick();shadowOf(Looper.getMainLooper()).idle();
            assertTrue(call.hasFocus());assertEquals("",call.getText().toString());
            android.view.inputmethod.EditorInfo info=new android.view.inputmethod.EditorInfo();call.onCreateInputConnection(info);
            assertEquals(android.text.InputType.TYPE_CLASS_TEXT,info.inputType&android.text.InputType.TYPE_MASK_CLASS);
            assertEquals(android.text.InputType.TYPE_TEXT_VARIATION_NORMAL,info.inputType&android.text.InputType.TYPE_MASK_VARIATION);
            assertTrue((info.imeOptions&android.view.inputmethod.EditorInfo.IME_FLAG_FORCE_ASCII)!=0);assertEquals("en",info.hintLocales.get(0).getLanguage());
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void shortcutsWrapOnNarrowWindowsAndReturnToOneRowOnWideWindows() throws Exception {
        prefs.edit().putBoolean("setupComplete",true).putString("myCall","BD6IUT").commit();
        MainActivity activity=launch(); View root=activity.findViewById(android.R.id.content);
        render(root,1000,800,"shortcuts-tablet.png"); assertShortcutRows(root,1);
        render(root,400,850,"shortcuts-phone.png"); assertShortcutRows(root,2);
        // A narrow window with reduced height, such as an open software keyboard.
        render(root,400,420,"shortcuts-phone-compact.png"); assertShortcutRows(root,2);
        render(root,1000,800,"shortcuts-tablet-restored.png"); assertShortcutRows(root,1);
    }
    private void assertShortcutRows(View root,int rowCount) {
        String[] labels={"CQ · F1","NR · F2","TU · F3","MY · F4","HIS · F5","B4 · F6","? · F7","NIL · F8"};
        android.graphics.Rect[] rects=new android.graphics.Rect[8];
        for(int i=0;i<labels.length;i++){
            View button=text(root,labels[i]);assertNotNull(labels[i],button);
            assertTrue(button.isShown());
            android.graphics.Rect bounds=new android.graphics.Rect();button.getDrawingRect(bounds);
            ((ViewGroup)root).offsetDescendantRectToMyCoords(button,bounds);rects[i]=bounds;
            assertTrue(labels[i]+" horizontal bounds",bounds.left>=0 && bounds.right<=root.getWidth());
            assertTrue(labels[i]+" vertical bounds",bounds.top>=0 && bounds.bottom<=root.getHeight());
            android.view.ViewParent parent=button.getParent();
            while(parent instanceof View && parent!=root){assertFalse(parent instanceof android.widget.HorizontalScrollView);parent=parent.getParent();}
        }
        for(int i=1;i<4;i++)assertEquals(rects[0].top,rects[i].top);
        for(int i=5;i<8;i++)assertEquals(rects[4].top,rects[i].top);
        if(rowCount==1)assertEquals(rects[0].top,rects[4].top);
        else assertTrue(rects[4].top>=rects[0].bottom);
    }
}
