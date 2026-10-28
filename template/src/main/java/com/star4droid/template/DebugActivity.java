package com.star4droid.template;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shows the crash text of the game (started by Star2dApp). Built in code: no layout resources needed. */
public class DebugActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String error = getIntent() != null && getIntent().hasExtra("error") ? getIntent().getStringExtra("error") : "";
        int pad = (int) (8 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(0x1c, 0x1c, 0x1c));

        TextView title = new TextView(this);
        title.setText("The game stopped because of an error");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(pad * 2, pad, pad * 2, pad);
        title.setBackgroundColor(Color.rgb(0x3c, 0x3c, 0x3c));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        TextView text = new TextView(this);
        text.setText(error);
        text.setTextColor(Color.rgb(0xec, 0xef, 0xf1));
        text.setTextSize(12);
        text.setTextIsSelectable(true);
        text.setPadding(pad * 2, pad, pad * 2, pad);
        scroll.addView(text, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(pad, pad, pad, pad);
        Button copy = new Button(this);
        copy.setText("Copy");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("error", error));
            }
        });
        Button close = new Button(this);
        close.setText("Close");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishAffinity();
            }
        });
        bar.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(close, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }
}
