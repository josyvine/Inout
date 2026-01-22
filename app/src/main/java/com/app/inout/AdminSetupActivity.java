package com.inout.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.inout.app.databinding.ActivityAdminSetupBinding;
import com.inout.app.utils.EncryptionHelper;
import com.inout.app.utils.FirebaseManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class AdminSetupActivity extends AppCompatActivity {

    private static final String TAG = "AdminSetupActivity";
    private ActivityAdminSetupBinding binding;
    private String jsonContent = null;
    private String parsedProjectId = null;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            processSelectedFile(uri);
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnSelectJson.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFilePicker();
            }
        });

        binding.btnSaveContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateAndSave();
            }
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json"); // Filter for JSON files
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(intent);
    }

    private void processSelectedFile(Uri uri) {
        try {
            jsonContent = readTextFromUri(uri);
            if (jsonContent != null) {
                // Quick validation to check if it looks like a google-services.json
                JSONObject root = new JSONObject(jsonContent);
                JSONObject projectInfo = root.getJSONObject("project_info");
                parsedProjectId = projectInfo.getString("project_id");

                binding.tvFileName.setText("Loaded: " + parsedProjectId);
                binding.tvFileName.setVisibility(View.VISIBLE);
                
                Toast.makeText(this, "JSON Loaded Successfully", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing file", e);
            jsonContent = null;
            parsedProjectId = null;
            binding.tvFileName.setText("Error: Invalid google-services.json");
            binding.tvFileName.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Invalid JSON File", Toast.LENGTH_LONG).show();
        }
    }

    private String readTextFromUri(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }
        inputStream.close();
        return stringBuilder.toString();
    }

    private void validateAndSave() {
        String companyName = binding.etCompanyName.getText().toString().trim();

        if (TextUtils.isEmpty(companyName)) {
            binding.etCompanyName.setError("Company Name is required");
            return;
        }

        if (jsonContent == null || parsedProjectId == null) {
            Toast.makeText(this, "Please upload a valid google-services.json file", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Configure Firebase dynamically using the utility
        boolean success = FirebaseManager.setConfiguration(this, jsonContent, companyName, parsedProjectId);

        if (success) {
            // 2. Initialize the Firebase Instance immediately
            FirebaseManager.initialize(this);

            Toast.makeText(this, "Setup Complete. Proceeding to Login...", Toast.LENGTH_SHORT).show();

            // 3. Move to Login Activity to Authenticate Admin on this new Firebase project
            Intent intent = new Intent(AdminSetupActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Failed to save configuration.", Toast.LENGTH_SHORT).show();
        }
    }
}