package com.example.docuorg;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DocumentsAdapter extends RecyclerView.Adapter<DocumentsAdapter.DocumentViewHolder> {

    public interface OnDocumentClickListener {
        void onDocumentClick(@NonNull DocumentItem item);
    }

    @NonNull
    private final List<DocumentItem> items = new ArrayList<>();

    @Nullable
    private final OnDocumentClickListener clickListener;

    public DocumentsAdapter(@Nullable OnDocumentClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void submitList(@NonNull List<DocumentItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DocumentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_document_card, parent, false);
        return new DocumentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DocumentViewHolder holder, int position) {
        DocumentItem item = items.get(position);
        holder.bind(item, clickListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class DocumentViewHolder extends RecyclerView.ViewHolder {

        private final TextView title;
        private final TextView category;
        private final TextView date;
        private final ImageView icon;

        DocumentViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.document_item_title);
            category = itemView.findViewById(R.id.document_item_category);
            date = itemView.findViewById(R.id.document_item_date);
            icon = itemView.findViewById(R.id.document_item_icon);
        }

        void bind(@NonNull DocumentItem item, @Nullable OnDocumentClickListener listener) {
            title.setText(item.bestTitle());
            category.setText(item.category != null ? item.category : "");

            String dateText = item.dateText;
            if ((dateText == null || dateText.trim().isEmpty()) && item.dateMillis != null) {
                dateText = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
                        .format(new Date(item.dateMillis));
            }
            date.setText(dateText != null ? dateText : "");

            int iconRes = pickIconRes(item.category);
            if (iconRes != 0) {
                icon.setImageResource(iconRes);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDocumentClick(item);
                }
            });
        }

        private int pickIconRes(@Nullable String category) {
            if (category == null) {
                return R.drawable.ic_doc_receipt;
            }
            String c = category.trim().toLowerCase(Locale.US);
            if (c.contains("receipt")) {
                return R.drawable.ic_doc_receipt;
            }
            if (c.contains("medical")) {
                return R.drawable.ic_doc_medical;
            }
            if (c.contains("tax")) {
                return R.drawable.ic_doc_tax;
            }
            if (c.contains("personal")) {
                return R.drawable.ic_doc_personal;
            }
            return R.drawable.ic_doc_receipt;
        }
    }
}

