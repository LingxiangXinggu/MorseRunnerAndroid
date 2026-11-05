// SPDX-License-Identifier: MPL-2.0
package org.morserunner.tablet;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

/** Keeps all eight macros visible, using the actual available window width. */
final class ShortcutBar extends LinearLayout {
    private final Button[] buttons;
    private int columns;

    ShortcutBar(Context context, Button[] buttons) {
        super(context);
        this.buttons = buttons;
        setOrientation(VERTICAL);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int cellWidth = dp(80);
        for (Button button : buttons) {
            cellWidth = Math.max(cellWidth, (int)Math.ceil(button.getPaint().measureText(button.getText().toString()))
                + button.getPaddingLeft() + button.getPaddingRight() + dp(6));
        }
        int nextColumns = available >= cellWidth * 8 ? 8 : 4;
        if (columns != nextColumns) {
            columns = nextColumns;
            removeAllViews();
            for (int first = 0; first < buttons.length; first += columns) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(HORIZONTAL);
                for (int i = first; i < Math.min(first + columns, buttons.length); i++) {
                    Button button = buttons[i];
                    if (button.getParent() != null) ((ViewGroup)button.getParent()).removeView(button);
                    LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
                    lp.setMargins(dp(3), dp(4), dp(3), dp(4));
                    row.addView(button, lp);
                }
                addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
