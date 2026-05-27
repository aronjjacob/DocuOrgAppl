package com.example.docuorg;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * MainActivity implements Firebase Email/Password sign-in.
 *
 * Note: you must add your google-services.json to the app/ folder and enable
 * Email/Password sign-in in the Firebase console.
 */
public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        // If already signed in, go straight to dashboard
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Log the signed-in user's UID to help troubleshoot Firestore writes
            android.util.Log.i("MainActivity", "User already signed in: UID=" + currentUser.getUid());
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }

        MaterialButton loginButton = findViewById(R.id.login_button);
        TextInputEditText emailInput = findViewById(R.id.email_input);
        TextInputEditText passwordInput = findViewById(R.id.password_input);

        loginButton.setOnClickListener(view -> {
            String email = emailInput.getText() != null ? emailInput.getText().toString().trim() : "";
            String password = passwordInput.getText() != null ? passwordInput.getText().toString() : "";

            if (!isValidEmail(email)) {
                showMessage(view, "Enter a valid email address");
                return;
            }
            if (password.length() < 6) {
                showMessage(view, "Password must be at least 6 characters");
                return;
            }

            loginButton.setEnabled(false);
            loginButton.setText(getString(R.string.signing_in));

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        loginButton.setEnabled(true);
                        loginButton.setText(getString(R.string.login_action));
                        if (task.isSuccessful()) {
                            // Sign in success
                            FirebaseUser signedIn = mAuth.getCurrentUser();
                            if (signedIn != null) {
                                android.util.Log.i("MainActivity", "Sign-in successful: UID=" + signedIn.getUid());
                            } else {
                                android.util.Log.w("MainActivity", "Sign-in successful but no current user available");
                            }
                            startActivity(new Intent(MainActivity.this, DashboardActivity.class));
                            finish();
                        } else {
                            String message = "Authentication failed.";
                            if (task.getException() != null) {
                                message = task.getException().getMessage();
                            }
                            showMessage(view, message);
                        }
                    });
        });

        findViewById(R.id.signup_button).setOnClickListener(v ->
            startActivity(new Intent(this, SignUpActivity.class)));

        // Forgot password - show dialog to send reset email
        findViewById(R.id.forgot_password).setOnClickListener(v -> startPasswordResetFlow());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void startPasswordResetFlow() {
        final EditText input = new EditText(this);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.reset_password_title))
                .setMessage(getString(R.string.reset_password_body))
                .setView(input)
                .setPositiveButton("Send", (dialog, which) -> {
                    String email = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!isValidEmail(email)) {
                        showMessage(findViewById(R.id.main), "Enter a valid email");
                        return;
                    }
                    mAuth.sendPasswordResetEmail(email)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    showMessage(findViewById(R.id.main), "Reset email sent");
                                } else {
                                    String msg = "Failed to send reset email";
                                    if (task.getException() != null) msg = task.getException().getMessage();
                                    showMessage(findViewById(R.id.main), msg);
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMessage(View anchor, String message) {
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).show();
    }

    private boolean isValidEmail(CharSequence target) {
        return target != null && Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }
}