package com.myapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(28, 28, 28, 28);
        root.setBackgroundColor(0xFF101820);

        TextView title = new TextView(this);
        title.setText("PrivateCall");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Private messaging and WebRTC calling");
        subtitle.setTextColor(0xFFB0BEC5);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        Button start = new Button(this);
        start.setText("Open chat");
        root.addView(start);
        start.setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)));

        setContentView(root);
    }
}
