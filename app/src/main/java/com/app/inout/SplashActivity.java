package com.inout.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.inout.app.utils.EncryptionHelper;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 2000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                checkUserSession();
            }
        }, SPLASH_DELAY_MS);
    }

    private void checkUserSession() {
        EncryptionHelper encryptionHelper = EncryptionHelper.getInstance(this);
        String userRole = encryptionHelper.getUserRole();
        boolean isSetupDone = encryptionHelper.isSetupDone();

        if (userRole == null) {
            // No role selected yet -> First time launch
            startActivity(new Intent(SplashActivity.this, RoleSelectionActivity.class));
        } else {
            // Role exists, check which dashboard to load
            if ("admin".equals(userRole)) {
                if (isSetupDone) {
                    startActivity(new Intent(SplashActivity.this, AdminDashboardActivity.class));
                } else {
                    // Admin selected but hasn't uploaded JSON yet
                    startActivity(new Intent(SplashActivity.this, AdminSetupActivity.class));
                }
            } else if ("employee".equals(userRole)) {
                if (isSetupDone) {
                    startActivity(new Intent(SplashActivity.this, EmployeeDashboardActivity.class));
                } else {
                    // Employee selected but hasn't scanned QR yet
                    startActivity(new Intent(SplashActivity.this, EmployeeQrScanActivity.class));
                }
            } else {
                // Fallback for unknown state
                startActivity(new Intent(SplashActivity.this, RoleSelectionActivity.class));
            }
        }
        finish();
    }
}