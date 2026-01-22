package com.inout.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.inout.app.databinding.ActivityLoginBinding;
import com.inout.app.models.User;
import com.inout.app.utils.EncryptionHelper;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private ActivityLoginBinding binding;
    
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    
    private String expectedRole; // "admin" or "employee" from local storage

    // Modern ActivityResultLauncher for Google Sign-In intent
    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            firebaseAuthWithGoogle(account.getIdToken());
                        }
                    } catch (ApiException e) {
                        Log.w(TAG, "Google sign in failed", e);
                        updateUI(null);
                        Toast.makeText(this, "Google Sign-In Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    updateUI(null); // Stop loading spinner
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 1. Initialize Firebase & Local Config
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        expectedRole = EncryptionHelper.getInstance(this).getUserRole();
        
        // 2. Configure Google Sign-In
        // We use the default_web_client_id. In a dynamic setup, this key MUST match 
        // the client_id in the uploaded google-services.json for the 'oauth_client' type 3.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) 
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // 3. UI Setup
        String companyName = EncryptionHelper.getInstance(this).getCompanyName();
        binding.tvLoginTitle.setText("Login to " + companyName);
        binding.tvLoginSubtitle.setText("Role: " + (expectedRole != null ? expectedRole.toUpperCase() : "UNKNOWN"));

        binding.btnGoogleSignIn.setOnClickListener(v -> signIn());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is already signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            checkUserInFirestore(currentUser);
        }
    }

    private void signIn() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnGoogleSignIn.setVisibility(View.INVISIBLE);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        signInLauncher.launch(signInIntent);
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            checkUserInFirestore(user);
                        } else {
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                            updateUI(null);
                        }
                    }
                });
    }

    /**
     * Checks if the user document exists in Firestore.
     * If not, creates it with the correct role.
     */
    private void checkUserInFirestore(FirebaseUser firebaseUser) {
        if (firebaseUser == null) return;

        DocumentReference userRef = db.collection("users").document(firebaseUser.getUid());

        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                // User exists - Check if roles match
                User user = documentSnapshot.toObject(User.class);
                if (user != null && user.getRole().equals(expectedRole)) {
                    proceedToDashboard(user);
                } else {
                    // Role Mismatch (e.g., trying to login as Admin but is actually an Employee)
                    Toast.makeText(LoginActivity.this, "Error: Account role mismatch.", Toast.LENGTH_LONG).show();
                    mAuth.signOut();
                    updateUI(null);
                }
            } else {
                // New User - Create Profile
                createUserProfile(firebaseUser, userRef);
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error fetching user", e);
            Toast.makeText(LoginActivity.this, "Network Error. Please try again.", Toast.LENGTH_SHORT).show();
            updateUI(null);
        });
    }

    private void createUserProfile(FirebaseUser firebaseUser, DocumentReference userRef) {
        User newUser = new User(firebaseUser.getUid(), firebaseUser.getEmail(), expectedRole);
        
        // Auto-fill name if available from Google
        if (firebaseUser.getDisplayName() != null) {
            newUser.setName(firebaseUser.getDisplayName());
        }

        // Logic based on Role
        if ("admin".equals(expectedRole)) {
            newUser.setApproved(true); // Admin is auto-approved (they possess the JSON)
        } else {
            newUser.setApproved(false); // Employee must be approved by Admin
        }

        userRef.set(newUser)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(LoginActivity.this, "Account Created.", Toast.LENGTH_SHORT).show();
                    proceedToDashboard(newUser);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating profile", e);
                    Toast.makeText(LoginActivity.this, "Failed to create database record.", Toast.LENGTH_SHORT).show();
                    mAuth.signOut();
                    updateUI(null);
                });
    }

    private void proceedToDashboard(User user) {
        Intent intent;
        if ("admin".equals(user.getRole())) {
            intent = new Intent(this, AdminDashboardActivity.class);
        } else {
            // Employee Flow
            // If profile is incomplete (no phone/photo) or not approved, we generally go to Dashboard
            // Dashboard handles the "Locked" state if not approved.
            // ProfileActivity is checked there or here. 
            // We'll send to Dashboard, Dashboard checks if profile needed.
            intent = new Intent(this, EmployeeDashboardActivity.class);
        }
        
        // Clear back stack so they can't go back to Login
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void updateUI(FirebaseUser user) {
        binding.progressBar.setVisibility(View.GONE);
        binding.btnGoogleSignIn.setVisibility(View.VISIBLE);
    }
}