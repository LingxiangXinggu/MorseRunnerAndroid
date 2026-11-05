// SPDX-License-Identifier: MPL-2.0
package org.morserunner.tablet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowPopupMenu;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w1000dp-h800dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
public class SettingsAdjustmentTest {
    private SharedPreferences prefs;
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    @Before public void clear(){prefs=RuntimeEnvironment.getApplication().getSharedPreferences("settings",Context.MODE_PRIVATE);prefs.edit().clear().commit();}
    @After public void close(){if(controller!=null)controller.pause().stop().destroy();}
    private void launch(){
        prefs.edit().putBoolean("setupComplete",true).putString("myCall","BD6IUT").commit();
        controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();shadowOf(Looper.getMainLooper()).idle();
    }
    private View text(View view,String value){
        if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=text(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}
        return null;
    }
    private AlertDialog settings(){
        text(activity.getWindow().getDecorView(),"更多").performClick();android.widget.PopupMenu menu=ShadowPopupMenu.getLatestPopupMenu();menu.getMenu().performIdentifierAction(1,0);menu.dismiss();shadowOf(Looper.getMainLooper()).idle();return ShadowAlertDialog.getLatestAlertDialog();
    }
    private EditText field(AlertDialog dialog,String label){View v=text(dialog.getWindow().getDecorView(),label);ViewGroup parent=(ViewGroup)v.getParent();return (EditText)parent.getChildAt(parent.indexOfChild(v)+1);}
    private SettingSlider slider(EditText field){ViewGroup parent=(ViewGroup)field.getParent();return (SettingSlider)parent.getChildAt(parent.indexOfChild(field)+1);}
    private void drag(SeekBar slider,int value){slider.setProgress(value);shadowOf(slider).getOnSeekBarChangeListener().onProgressChanged(slider,value,true);}
    private void save(AlertDialog dialog){dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();}
    @Test public void slidersAndExactManualValuesStayInSyncAndPersist(){
        launch();AlertDialog dialog=settings();View decor=dialog.getWindow().getDecorView();text(decor,"高级设置  ＋").performClick();
        EditText pitch=field(dialog,"音调 · Hz（300–1000）"),width=field(dialog,"带宽 · Hz（100–1000）");
        assertEquals(300,slider(pitch).getMin());assertEquals(1000,slider(pitch).getMax());
        drag(slider(pitch),725);assertEquals("725",pitch.getText().toString());
        pitch.setText("637");assertEquals(637,slider(pitch).getProgress());
        drag(slider(width),425);assertEquals("425",width.getText().toString());
        width.setText("413");assertEquals(413,slider(width).getProgress());
        String[] labels={"本台发报速度 · WPM（10–120）","最低","最高","每轮时长 · 分钟（1–60）","活跃度（1–10）","侧音衰减 · dB（0–40）"};
        int[] values={45,16,28,15,4,18};
        for(int i=0;i<labels.length;i++){EditText f=field(dialog,labels[i]);drag(slider(f),values[i]);assertEquals(String.valueOf(values[i]),f.getText().toString());}
        save(dialog);assertFalse(dialog.isShowing());
        assertEquals(637,prefs.getInt("pitch",0));assertEquals(413,prefs.getInt("bandwidth",0));assertEquals(-18,prefs.getInt("monitor",0));assertEquals(45,prefs.getInt("wpm",0));
        assertEquals(16,prefs.getInt("dxMin",0));assertEquals(28,prefs.getInt("dxMax",0));assertEquals(15,prefs.getInt("duration",0));assertEquals(4,prefs.getInt("activity",0));
        dialog=settings();assertEquals("637",field(dialog,"音调 · Hz（300–1000）").getText().toString());assertEquals(413,slider(field(dialog,"带宽 · Hz（100–1000）")).getProgress());
    }
    @Test public void emptyAndOutOfRangeTypingIsNotOverwrittenAndInvalidValuesDoNotSave(){
        launch();AlertDialog dialog=settings();EditText pitch=field(dialog,"音调 · Hz（300–1000）");
        pitch.setText("");assertEquals("",pitch.getText().toString());save(dialog);assertTrue(dialog.isShowing());assertFalse(prefs.contains("pitch"));
        pitch.setText("9");assertEquals("9",pitch.getText().toString());assertEquals(300,slider(pitch).getProgress());save(dialog);assertTrue(dialog.isShowing());
        pitch.setText("1001");assertEquals("1001",pitch.getText().toString());assertEquals(1000,slider(pitch).getProgress());save(dialog);assertTrue(dialog.isShowing());
        pitch.setText("612");save(dialog);assertFalse(dialog.isShowing());assertEquals(612,prefs.getInt("pitch",0));
    }
    @Test public void sliderChangesCanBeCancelled(){
        launch();AlertDialog dialog=settings();drag(slider(field(dialog,"音调 · Hz（300–1000）")),900);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(Looper.getMainLooper()).idle();assertFalse(prefs.contains("pitch"));
    }
    @Test public void qskIsOffForNewInstallsAndLegacyDefaultsButCanBeExplicitlyReenabled(){
        prefs.edit().putInt("flags",32|4|2).commit();launch();assertEquals(4|2,prefs.getInt("flags",-1));
        AlertDialog dialog=settings();CheckBox qsk=(CheckBox)text(dialog.getWindow().getDecorView(),"QSK 插入接收");assertFalse(qsk.isChecked());qsk.setChecked(true);save(dialog);
        assertEquals(32|4|2,prefs.getInt("flags",0));
        controller.pause().stop().destroy();controller=null;launch();assertEquals(32|4|2,prefs.getInt("flags",0));
        dialog=settings();assertTrue(((CheckBox)text(dialog.getWindow().getDecorView(),"QSK 插入接收")).isChecked());
        dialog.dismiss();shadowOf(Looper.getMainLooper()).idle();controller.pause().stop().destroy();controller=null;
        prefs.edit().clear().commit();launch();assertEquals(0,prefs.getInt("flags",-1));dialog=settings();assertFalse(((CheckBox)text(dialog.getWindow().getDecorView(),"QSK 插入接收")).isChecked());
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderAdvancedAdjustments() throws Exception {
        launch();AlertDialog dialog=settings();View decor=dialog.getWindow().getDecorView();text(decor,"高级设置  ＋").performClick();
        decor.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(740,View.MeasureSpec.EXACTLY));decor.layout(0,0,640,740);
        EditText pitch=field(dialog,"音调 · Hz（300–1000）");View ancestor=pitch;
        while(!(ancestor instanceof android.widget.ScrollView))ancestor=(View)ancestor.getParent();
        android.widget.ScrollView scroll=(android.widget.ScrollView)ancestor;
        android.graphics.Rect rect=new android.graphics.Rect();pitch.getDrawingRect(rect);scroll.offsetDescendantRectToMyCoords(pitch,rect);scroll.scrollTo(0,rect.top-45);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(640,740,android.graphics.Bitmap.Config.ARGB_8888);decor.draw(new android.graphics.Canvas(bitmap));
        java.io.File dir=new java.io.File("build/validation/ui");dir.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,"settings-sliders.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
}
