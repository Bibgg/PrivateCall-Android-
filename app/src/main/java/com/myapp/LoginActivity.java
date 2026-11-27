package com.myapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class LoginActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText editPhone = findViewById(R.id.editPhone);
        Button btnGetOtp = findViewById(R.id.btnGetOtp);
        LinearLayout btnGoogle = findViewById(R.id.btnGoogle);

        btnGetOtp.setOnClickListener(v -> {
            String phone = editPhone.getText().toString().trim();
            if (phone.length() < 10) {
                Toast.makeText(this, "Please enter valid 10 digit number", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(this, OtpActivity.class);
            i.putExtra("phone", phone);
            startActivity(i);
        });

        btnGoogle.setOnClickListener(v -> {
            // Demo: skip to home
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
    }
}
