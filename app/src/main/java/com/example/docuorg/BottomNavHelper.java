package com.example.docuorg;

import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public final class BottomNavHelper {

    private BottomNavHelper() {
    }

    public static void setupBottomNav(AppCompatActivity activity, int selectedItemId) {
        wireItem(activity, R.id.nav_home_item, DashboardActivity.class, selectedItemId);
        wireItem(activity, R.id.nav_documents_item, DocumentsActivity.class, selectedItemId);
        wireItem(activity, R.id.nav_ai_item, AiActivity.class, selectedItemId);
        wireItem(activity, R.id.nav_settings_item, SettingsActivity.class, selectedItemId);

        int activeColor = ContextCompat.getColor(activity, R.color.primary_teal);
        int inactiveColor = ContextCompat.getColor(activity, R.color.text_secondary);

        setItemSelected(activity, R.id.nav_home_icon, R.id.nav_home_label,
                selectedItemId == R.id.nav_home_item, activeColor, inactiveColor);
        setItemSelected(activity, R.id.nav_documents_icon, R.id.nav_documents_label,
                selectedItemId == R.id.nav_documents_item, activeColor, inactiveColor);
        setItemSelected(activity, R.id.nav_ai_icon, R.id.nav_ai_label,
                selectedItemId == R.id.nav_ai_item, activeColor, inactiveColor);
        setItemSelected(activity, R.id.nav_settings_icon, R.id.nav_settings_label,
                selectedItemId == R.id.nav_settings_item, activeColor, inactiveColor);
    }

    private static void wireItem(AppCompatActivity activity, int itemId, Class<?> destination,
                                 int selectedItemId) {
        View item = activity.findViewById(itemId);
        if (item == null) {
            return;
        }
        item.setOnClickListener(view -> {
            if (itemId == selectedItemId) {
                return;
            }
            Intent intent = new Intent(activity, destination);
            activity.startActivity(intent);
        });
    }

    private static void setItemSelected(AppCompatActivity activity, int iconId, int labelId,
                                        boolean selected, int activeColor, int inactiveColor) {
        int color = selected ? activeColor : inactiveColor;
        ImageView icon = activity.findViewById(iconId);
        TextView label = activity.findViewById(labelId);
        if (icon != null) {
            icon.setColorFilter(color);
        }
        if (label != null) {
            label.setTextColor(color);
        }
    }
}
