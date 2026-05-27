package com.example.docuorg;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecentDocumentsAdapter extends RecyclerView.Adapter<RecentDocumentsAdapter.RecentDocumentViewHolder> {

    private final List<DocumentItem> documents = new ArrayList<>();
    private final OnDocumentClickListener listener;

    public interface OnDocumentClickListener {
        void onDocumentClick(DocumentItem document);
    }

    public RecentDocumentsAdapter(OnDocumentClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<DocumentItem> newDocuments) {
        documents.clear();
        if (newDocuments != null) {
            documents.addAll(newDocuments);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecentDocumentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MaterialCardView cardView = (MaterialCardView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_document_card, parent, false);
        return new RecentDocumentViewHolder(cardView, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull RecentDocumentViewHolder holder, int position) {
        DocumentItem item = documents.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return documents.size();
    }

    static class RecentDocumentViewHolder extends RecyclerView.ViewHolder {

        private final ImageView imageView;
        private final TextView tagView;
        private final TextView titleView;
        private final TextView timeView;
        private final OnDocumentClickListener listener;
        private final List<DocumentItem> documents;

        public RecentDocumentViewHolder(@NonNull MaterialCardView cardView, OnDocumentClickListener listener) {
            super(cardView);
            this.listener = listener;
            this.documents = new ArrayList<>();
            this.imageView = cardView.findViewById(R.id.recent_card_image);
            this.tagView = cardView.findViewById(R.id.recent_card_tag);
            this.titleView = cardView.findViewById(R.id.recent_card_title);
            this.timeView = cardView.findViewById(R.id.recent_card_time);

            cardView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null && !documents.isEmpty()) {
                    listener.onDocumentClick(documents.get(0));
                }
            });
        }

        void bind(DocumentItem item) {
            documents.clear();
            documents.add(item);

            if (tagView != null) {
                tagView.setText(deriveTag(item));
            }
            if (titleView != null) {
                titleView.setText(item.bestTitle());
            }
            if (timeView != null) {
                String dateText = item.dateText;
                if (dateText == null || dateText.trim().isEmpty()) {
                    timeView.setText("Modified recently");
                } else {
                    timeView.setText("Modified " + dateText);
                }
            }

            if (imageView != null) {
                loadImage(item);
            }
        }

        private void loadImage(DocumentItem item) {
            if (item.uri == null || item.uri.isEmpty()) {
                setFallbackIcon(item);
                return;
            }

            try {
                Uri uri = Uri.parse(item.uri);
                String mime = item.mime != null ? item.mime.toLowerCase(Locale.US) : "";

                if (mime.contains("image")) {
                    imageView.setImageURI(uri);
                } else {
                    setFallbackIcon(item);
                }
            } catch (Exception e) {
                setFallbackIcon(item);
            }
        }

        private void setFallbackIcon(DocumentItem item) {
            if (item.category == null) {
                imageView.setImageResource(R.drawable.ic_doc_receipt);
                return;
            }
            String c = item.category.trim().toLowerCase(Locale.US);
            if (c.contains("receipt")) {
                imageView.setImageResource(R.drawable.ic_doc_receipt);
            } else if (c.contains("medical")) {
                imageView.setImageResource(R.drawable.ic_doc_medical);
            } else if (c.contains("tax")) {
                imageView.setImageResource(R.drawable.ic_doc_tax);
            } else if (c.contains("personal")) {
                imageView.setImageResource(R.drawable.ic_doc_personal);
            } else {
                imageView.setImageResource(R.drawable.ic_doc_receipt);
            }
        }

        private String deriveTag(DocumentItem item) {
            String mime = item.mime != null ? item.mime.toLowerCase(Locale.US) : "";
            if (mime.contains("pdf")) {
                return "PDF";
            }
            if (mime.contains("word") || mime.contains("doc")) {
                return "DOC";
            }
            if (mime.contains("image")) {
                return "IMG";
            }
            if (item.category != null && !item.category.trim().isEmpty()) {
                return item.category.trim().toUpperCase(Locale.US);
            }
            return "DOC";
        }
    }
}


