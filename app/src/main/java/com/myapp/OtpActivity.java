package com.myapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class OtpActivity extends Activity {
    private EditText[] otpBoxes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        String phone = getIntent().getStringExtra("phone");
        if (phone == null) phone = "XXXXXXXXXX";

        TextView subtitle = findViewById(R.id.txtOtpSubtitle);
        subtitle.setText("Enter the 6 digit code sent to\n+977 " + phone);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        otpBoxes = new EditText[]{
                findViewById(R.id.otp1),
                findViewById(R.id.otp2),
                findViewById(R.id.otp3),
                findViewById(R.id.otp4),
                findViewById(R.id.otp5),
                findViewById(R.id.otp6)
        };

        for (int i = 0; i < otpBoxes.length; i++) {
            final int index = i;
            otpBoxes[i].addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && index < 5) {
                        otpBoxes[index + 1].requestFocus();
                    }
                }
                public void afterTextChanged(Editable s) {}
            });
        }

        Button btnVerify = findViewById(R.id.btnVerify);
        btnVerify.setOnClickListener(v -> {
            StringBuilder otp = new StringBuilder();
            for (EditText box : otpBoxes) otp.append(box.getText().toString());
            if (otp.length() < 6) {
                Toast.makeText(this, "Enter complete 6 digit OTP", Toast.LENGTH_SHORT).show();
                return;
            }
            // Demo: any OTP works
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });

        findViewById(R.id.btnResend).setOnClickListener(v ->
                Toast.makeText(this, "OTP resent!", Toast.LENGTH_SHORT).show());
    }
}
