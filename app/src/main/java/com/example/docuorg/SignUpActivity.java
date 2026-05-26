package com.example.docuorg;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;

import androidx.activity.EdgeToEdge;
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

public class SignUpActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);

        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        TextInputEditText emailInput = findViewById(R.id.signup_email_input);
        TextInputEditText passwordInput = findViewById(R.id.signup_password_input);
        TextInputEditText confirmInput = findViewById(R.id.signup_confirm_input);
        MaterialButton signupButton = findViewById(R.id.signup_submit_button);

        findViewById(R.id.signup_back).setOnClickListener(v -> finish());

        signupButton.setOnClickListener(view -> {
            String email = emailInput.getText() != null ? emailInput.getText().toString().trim() : "";
            String password = passwordInput.getText() != null ? passwordInput.getText().toString() : "";
            String confirm = confirmInput.getText() != null ? confirmInput.getText().toString() : "";

            if (!isValidEmail(email)) {
                showMessage(view, "Enter a valid email address");
                return;
            }
            if (password.length() < 6) {
                showMessage(view, "Password must be at least 6 characters");
                return;
            }
            if (!password.equals(confirm)) {
                showMessage(view, "Passwords do not match");
                return;
            }

            signupButton.setEnabled(false);
            signupButton.setText(getString(R.string.creating_account));

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        signupButton.setEnabled(true);
                        signupButton.setText(getString(R.string.sign_up_action));
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                user.sendEmailVerification();
                            }
                            startActivity(new Intent(SignUpActivity.this, DashboardActivity.class));
                            finish();
                        } else {
                            String message = "Sign up failed.";
                            if (task.getException() != null) {
                                message = task.getException().getMessage();
                            }
                            showMessage(view, message);
                        }
                    });
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.sign_up_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void showMessage(View anchor, String message) {
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).show();
    }

    private boolean isValidEmail(CharSequence target) {
        return target != null && Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }
}
