// SPDX-License-Identifier: MPL-2.0
package org.morserunner.tablet;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.SeekBar;

/** A numeric field stays editable, including temporarily empty/out-of-range text. */
final class SettingSlider extends SeekBar {
    private boolean updating;

    SettingSlider(Context context, EditText field, String label, int minimum, int maximum) {
        super(context);
        // Set max first: SeekBar initially has max=100 and would clamp a larger min.
        setMax(maximum); setMin(minimum);
        setContentDescription(label+"滑块");
        setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar,int progress,boolean fromUser) {
                if (!fromUser || updating) return;
                updating=true;
                field.setText(String.valueOf(progress)); field.setSelection(field.length());
                updating=false;
            }
            public void onStartTrackingTouch(SeekBar seekBar) { }
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { sync(s); }
            public void afterTextChanged(Editable value) { }
        });
        sync(field.getText());
    }
    private void sync(CharSequence value) {
        if (updating) return;
        try {
            int parsed=Integer.parseInt(value.toString());
            updating=true; setProgress(Math.max(getMin(),Math.min(getMax(),parsed)));
        } catch (NumberFormatException ignored) {
            // Do not replace an unfinished manual edit with a slider default.
        } finally { updating=false; }
    }
}
