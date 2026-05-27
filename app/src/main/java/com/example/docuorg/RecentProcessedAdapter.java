package com.example.docuorg;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecentProcessedAdapter extends RecyclerView.Adapter<RecentProcessedAdapter.ViewHolder> {

    private final List<ProcessedItem> items = new ArrayList<>();
    private final LayoutInflater inflater;

    public RecentProcessedAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.item_ai_processed_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProcessedItem item = items.get(position);
        holder.title.setText(item.title != null && !item.title.isEmpty() ? item.title : "Untitled");
        String subtitle = item.category != null && !item.category.isEmpty() ? item.category : "";
        if (item.timestamp > 0) {
            String date = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(item.timestamp));
            if (!subtitle.isEmpty()) subtitle = subtitle + " • " + date;
            else subtitle = date;
        }
        holder.subtitle.setText(subtitle);

        if (item.imageUri != null && !item.imageUri.isEmpty()) {
            try {
                holder.thumbnail.setImageURI(Uri.parse(item.imageUri));
            } catch (Exception e) {
                holder.thumbnail.setImageResource(R.drawable.ic_doc_receipt);
            }
        } else {
            holder.thumbnail.setImageResource(R.drawable.ic_doc_receipt);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void submitList(List<ProcessedItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void addItem(ProcessedItem item) {
        if (item == null) return;
        items.add(0, item);
        notifyItemInserted(0);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView title;
        final TextView subtitle;

        ViewHolder(View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.ai_processed_thumb);
            title = itemView.findViewById(R.id.ai_processed_title);
            subtitle = itemView.findViewById(R.id.ai_processed_subtitle);
        }
    }
}




