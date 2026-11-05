// SPDX-License-Identifier: MPL-2.0
package org.morserunner.tablet;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import java.util.ArrayList;
import java.util.List;

/** In-app training input only; never registers or replaces a system IME. */
final class TrainingKeyboard extends LinearLayout {
    interface Listener {
        void insert(String value);
        void delete();
        void clearField();
        void focusField(int field);
        void clearAll();
        void stopSending();
        void toggleTraining();
        void hide();
        void send();
    }
    private final Listener listener;
    private final LinearLayout letters, numbers;
    private final Button[] fields = new Button[3];
    private final List<Button> keys = new ArrayList<>();
    private boolean compact;
    private static final int INK=0xff1b2b43, BLUE=0xff3268cf;

    TrainingKeyboard(Context context, Listener listener) {
        super(context); this.listener=listener; setOrientation(VERTICAL);
        setPadding(dp(2),dp(4),dp(2),dp(2)); setBackgroundColor(0xffe9eef7);
        letters=panel(); numbers=panel();
        characterRow(letters,"1234567890",false,false);
        characterRow(letters,"QWERTYUIOP",false,false);
        characterRow(letters,"ASDFGHJKL",true,false);
        characterRow(letters,"ZXCVBNM/?",false,true);
        characterRow(numbers,"123",false,false);
        characterRow(numbers,"456",false,false);
        characterRow(numbers,"789",false,false);
        LinearLayout numericBottom=row(numbers);
        addKey(numericBottom,"清除",listener::clearField,1);
        addKey(numericBottom,"0",()->listener.insert("0"),1);
        backspace(numericBottom);
        addView(letters); addView(numbers);
        LinearLayout actions=new LinearLayout(context); actions.setOrientation(HORIZONTAL); addView(actions);
        String[] labels={"CALL","RST","NR"};
        for(int i=0;i<3;i++) { final int index=i; fields[i]=addKey(actions,labels[i],()->listener.focusField(index),1); fields[i].setContentDescription("切换到 "+labels[i]); }
        addKey(actions,"清空",listener::clearAll,.9f);
        Button more=addKey(actions,"更多",()->{},.9f); more.setOnClickListener(v->more(v));
        more.setContentDescription("更多训练操作：停发、开始或结束训练");
        addKey(actions,"收起",listener::hide,.9f);
        Button send=addKey(actions,"发送",listener::send,1.5f);
        send.setTextColor(Color.WHITE); send.setBackground(surface(BLUE));
        for(int i=0;i<actions.getChildCount();i++) ((Button)actions.getChildAt(i)).setTextSize(12);
        setField(0);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout panel(){LinearLayout p=new LinearLayout(getContext());p.setOrientation(VERTICAL);return p;}
    private LinearLayout row(LinearLayout parent){LinearLayout r=new LinearLayout(getContext());r.setOrientation(HORIZONTAL);parent.addView(r);return r;}
    private RippleDrawable surface(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(6));return new RippleDrawable(ColorStateList.valueOf(0x223268cf),d,null);}
    private Button addKey(LinearLayout row,String label,Runnable action,float weight){
        Button key=new Button(getContext());key.setText(label);key.setTextSize(label.length()>2?12:18);key.setAllCaps(false);
        key.setTextColor(INK);key.setMinHeight(0);key.setMinimumHeight(0);key.setMinWidth(0);key.setMinimumWidth(0);
        key.setPadding(dp(2),0,dp(2),0);key.setBackground(surface(Color.WHITE));key.setStateListAnimator(null);key.setElevation(0);
        key.setFocusable(false);key.setSoundEffectsEnabled(false);
        key.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);action.run();});
        LayoutParams lp=new LayoutParams(0,dp(48),weight);lp.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(key,lp);keys.add(key);return key;
    }
    private void characterRow(LinearLayout parent,String values,boolean inset,boolean delete){
        LinearLayout row=row(parent);
        if(inset)row.addView(new View(getContext()),new LayoutParams(0,1,.5f));
        for(int i=0;i<values.length();i++){String value=values.substring(i,i+1);addKey(row,value,()->listener.insert(value),1);}
        if(delete)backspace(row);
        if(inset)row.addView(new View(getContext()),new LayoutParams(0,1,.5f));
    }
    private void backspace(LinearLayout row){
        Button key=addKey(row,"⌫",listener::delete,1);key.setContentDescription("退格，长按清空当前字段");
        key.setOnLongClickListener(v->{listener.clearField();return true;});
    }
    void setField(int index){
        letters.setVisibility(index==0?VISIBLE:GONE);numbers.setVisibility(index==0?GONE:VISIBLE);
        for(int i=0;i<fields.length;i++){fields[i].setSelected(i==index);fields[i].setTextColor(i==index?BLUE:INK);fields[i].setBackground(surface(i==index?0xffd8e6ff:Color.WHITE));}
    }
    void setCompact(boolean value){
        if(compact==value)return;compact=value;
        for(Button key:keys){LayoutParams lp=(LayoutParams)key.getLayoutParams();lp.height=dp(value?36:48);key.setLayoutParams(lp);}
    }
    private void more(View anchor){
        PopupMenu menu=new PopupMenu(getContext(),anchor);menu.getMenu().add(0,1,0,"停发");menu.getMenu().add(0,2,1,"开始／结束训练");
        menu.setOnMenuItemClickListener(item->{if(item.getItemId()==1)listener.stopSending();else listener.toggleTraining();return true;});menu.show();
    }
}
