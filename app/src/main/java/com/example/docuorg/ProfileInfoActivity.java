package com.example.docuorg;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ProfileInfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile_info);

        findViewById(R.id.profile_info_back).setOnClickListener(v -> finish());

        EditText nameInput = findViewById(R.id.profile_name_input);
        EditText emailInput = findViewById(R.id.profile_email_input);
        View saveButton = findViewById(R.id.profile_save_button);

        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            if (user.getDisplayName() != null) {
                nameInput.setText(user.getDisplayName());
            }
            if (user.getEmail() != null) {
                emailInput.setText(user.getEmail());
            }
        }

        saveButton.setOnClickListener(v -> {
            String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
            String email = emailInput.getText() != null ? emailInput.getText().toString().trim() : "";
            if (name.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, R.string.profile_required_fields, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, R.string.profile_email_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (user == null) {
                Toast.makeText(this, R.string.profile_update_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            saveButton.setEnabled(false);
            com.google.firebase.auth.UserProfileChangeRequest request =
                    new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build();

            user.updateProfile(request)
                    .addOnSuccessListener(unused -> {
                        if (email.equals(user.getEmail())) {
                            Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        user.updateEmail(email)
                                .addOnSuccessListener(ignored -> {
                                    Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(err -> {
                                    Toast.makeText(this, R.string.profile_update_failed, Toast.LENGTH_SHORT).show();
                                    saveButton.setEnabled(true);
                                });
                    })
                    .addOnFailureListener(err -> {
                        Toast.makeText(this, R.string.profile_update_failed, Toast.LENGTH_SHORT).show();
                        saveButton.setEnabled(true);
                    });
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profile_info_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}
