// SPDX-License-Identifier: MPL-2.0
package org.morserunner.tablet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
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
import org.robolectric.shadows.ShadowToast;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w1000dp-h800dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
public class TrainingKeyboardTest {
    private SharedPreferences prefs;
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private View root;
    @Before public void clear(){prefs=RuntimeEnvironment.getApplication().getSharedPreferences("settings",Context.MODE_PRIVATE);prefs.edit().clear().commit();}
    @After public void close(){if(controller!=null)controller.pause().stop().destroy();}
    private void launch(boolean complete,int mode){
        if(complete)prefs.edit().putBoolean("setupComplete",true).putString("myCall","BD6IUT").commit();
        if(mode>=0)prefs.edit().putInt("keyboardMode",mode).commit();
        controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();root=activity.findViewById(android.R.id.content);
        shadowOf(Looper.getMainLooper()).idle();layout(1000,800);
    }
    private void layout(int width,int height){
        for(int i=0;i<2;i++){
            root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
        }
    }
    private View text(View view,String value,boolean visible){
        if(visible&&view.getVisibility()!=View.VISIBLE)return null;
        if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=text(((ViewGroup)view).getChildAt(i),value,visible);if(found!=null)return found;}
        return null;
    }
    private TrainingKeyboard keyboard(View view){
        if(view instanceof TrainingKeyboard)return (TrainingKeyboard)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TrainingKeyboard found=keyboard(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private View key(String label){View key=text(keyboard(root),label,true);assertNotNull(label,key);return key;}
    private void tap(String label){key(label).performClick();}
    private EditText field(View view,String label){View v=text(view,label,false);assertNotNull(label,v);ViewGroup parent=(ViewGroup)v.getParent();return (EditText)parent.getChildAt(parent.indexOfChild(v)+1);}
    private void openSettings(){
        text(root,"更多",false).performClick();android.widget.PopupMenu menu=ShadowPopupMenu.getLatestPopupMenu();menu.getMenu().performIdentifierAction(1,0);menu.dismiss();shadowOf(Looper.getMainLooper()).idle();
    }
    private void save(AlertDialog dialog){dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();layout(1000,800);}
    @Test public void firstRunChoicePersistsAndSettingsContinueToUseSystemKeyboard(){
        launch(false,-1);AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();View decor=dialog.getWindow().getDecorView();
        EditText my=field(decor,"本台呼号");assertTrue(my.getShowSoftInputOnFocus());
        ((RadioButton)text(decor,"内置键盘 · 适合触屏抄报",false)).setChecked(true);my.setText("BD6IUT");save(dialog);
        assertTrue(prefs.getBoolean("setupComplete",false));assertEquals(1,prefs.getInt("keyboardMode",-1));
        assertFalse(field(root,"CALL").getShowSoftInputOnFocus());assertEquals(View.VISIBLE,keyboard(root).getVisibility());
        controller.pause().stop().destroy();controller=null;launch(true,-1);
        assertEquals(View.VISIBLE,keyboard(root).getVisibility());assertFalse(field(root,"NR").getShowSoftInputOnFocus());
    }
    @Test public void insertionSelectionDeletionAndNumericFieldsUseExistingFilters(){
        launch(true,1);EditText call=field(root,"CALL");call.setText("K1ABC");call.setSelection(2,4);tap("Z");
        assertEquals("K1ZC",call.getText().toString());assertSame(call,root.findFocus());
        call.setSelection(2);tap("⌫");assertEquals("KZC",call.getText().toString());
        key("⌫").performLongClick();assertEquals("",call.getText().toString());
        tap("A");tap("1");tap("/");tap("?");assertEquals("A1/?",call.getText().toString());
        tap("NR");EditText number=field(root,"NR");assertTrue(number.hasFocus());assertNull(text(keyboard(root),"Q",true));
        number.setText("23");number.setSelection(1);tap("9");assertEquals("293",number.getText().toString());
        number.setSelection(0,2);tap("⌫");assertEquals("3",number.getText().toString());
        key("⌫").performLongClick();tap("⌫");assertEquals("",number.getText().toString());
        tap("RST");tap("5");assertEquals("5",field(root,"RST").getText().toString());
        tap("清空");assertEquals("",call.getText().toString());assertEquals("599",field(root,"RST").getText().toString());assertEquals("",number.getText().toString());assertSame(call,root.findFocus());
        tap("发送");assertEquals("请先开始训练",ShadowToast.getTextOfLatestToast());
    }
    @Test public void hideReopenBackAndPhysicalKeyboardKeepTheChosenField(){
        launch(true,1);tap("NR");EditText number=field(root,"NR");tap("收起");layout(1000,800);
        assertEquals(View.GONE,keyboard(root).getVisibility());text(root,"键盘",true).performClick();layout(1000,800);
        assertEquals(View.VISIBLE,keyboard(root).getVisibility());assertSame(number,root.findFocus());tap("7");assertEquals("7",number.getText().toString());
        activity.onBackPressed();assertEquals(View.GONE,keyboard(root).getVisibility());assertFalse(activity.isFinishing());
        number.performClick();assertEquals(View.VISIBLE,keyboard(root).getVisibility());tap("CALL");EditText call=field(root,"CALL");call.setText("");
        call.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_A));call.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_A));
        assertEquals("A",call.getText().toString());assertFalse(call.getShowSoftInputOnFocus());
        assertEquals(InputType.TYPE_CLASS_TEXT,call.getInputType()&InputType.TYPE_MASK_CLASS);
        assertEquals(InputType.TYPE_TEXT_VARIATION_NORMAL,call.getInputType()&InputType.TYPE_MASK_VARIATION);
    }
    @Test public void switchingModesAndCancellingPreservePreferences(){
        launch(true,-1);assertTrue(field(root,"CALL").getShowSoftInputOnFocus());assertEquals(View.GONE,keyboard(root).getVisibility());
        openSettings();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        ((RadioButton)text(dialog.getWindow().getDecorView(),"内置键盘 · 适合触屏抄报",false)).setChecked(true);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(Looper.getMainLooper()).idle();assertEquals(0,prefs.getInt("keyboardMode",0));
        openSettings();dialog=ShadowAlertDialog.getLatestAlertDialog();((RadioButton)text(dialog.getWindow().getDecorView(),"内置键盘 · 适合触屏抄报",false)).setChecked(true);save(dialog);
        assertFalse(field(root,"CALL").getShowSoftInputOnFocus());
        openSettings();dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(field(dialog.getWindow().getDecorView(),"本台呼号").getShowSoftInputOnFocus());
        ((RadioButton)text(dialog.getWindow().getDecorView(),"系统键盘",false)).setChecked(true);save(dialog);
        assertEquals(0,prefs.getInt("keyboardMode",-1));assertEquals(View.GONE,keyboard(root).getVisibility());
        assertTrue(field(root,"CALL").getShowSoftInputOnFocus());assertTrue(field(root,"RST").getShowSoftInputOnFocus());assertTrue(field(root,"NR").getShowSoftInputOnFocus());
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void embeddedKeyboardFitsPhoneTabletAndShortWindows() throws Exception {
        launch(true,1);
        for(int[] size:new int[][]{{400,850},{1000,800},{400,420},{1000,380}}){
            layout(size[0],size[1]);assertKeyboardFits();screenshot("built-in-"+size[0]+"x"+size[1]+".png",size[0],size[1]);
        }
        layout(400,850);tap("NR");layout(400,850);assertKeyboardFits();screenshot("built-in-numeric.png",400,850);
    }
    private void assertKeyboardFits(){
        TrainingKeyboard keyboard=keyboard(root);assertEquals(View.VISIBLE,keyboard.getVisibility());
        assertKeysInside(keyboard);
        assertTrue(field(root,"CALL").isShown());assertTrue(field(root,"NR").isShown());
    }
    private void assertKeysInside(View view){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof android.widget.Button){Rect bounds=new Rect();view.getDrawingRect(bounds);((ViewGroup)root).offsetDescendantRectToMyCoords(view,bounds);
            assertTrue(((TextView)view).getText()+" left/right",bounds.left>=0&&bounds.right<=root.getWidth());
            assertTrue(((TextView)view).getText()+" top/bottom "+bounds,bounds.top>=0&&bounds.bottom<=root.getHeight());}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)assertKeysInside(((ViewGroup)view).getChildAt(i));
    }
    private void screenshot(String name,int width,int height) throws Exception {
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));
        java.io.File dir=new java.io.File("build/validation/ui");dir.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
}
