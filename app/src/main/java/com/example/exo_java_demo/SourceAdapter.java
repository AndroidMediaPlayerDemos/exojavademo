package com.example.exo_java_demo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放源对话框列表：分区标题 + 源条目（条目副标题为 url，可为空）。
 */
public class SourceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** 一行数据：isHeader 时仅 title 生效，item 时 url 作为副标题（可空）。 */
    public static class Row {
        public final String title;
        public final String url;
        public final boolean isHeader;

        private Row(String title, String url, boolean isHeader) {
            this.title = title;
            this.url = url;
            this.isHeader = isHeader;
        }

        public static Row header(String title) {
            return new Row(title, null, true);
        }

        public static Row item(String title, String url) {
            return new Row(title, url, false);
        }
    }

    public interface OnSourceClickListener {
        void onSourceClick(String url);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final List<Row> rows = new ArrayList<>();
    private final OnSourceClickListener listener;

    public SourceAdapter(List<Row> initialRows, OnSourceClickListener listener) {
        this.rows.addAll(initialRows);
        this.listener = listener;
    }

    public void updateRows(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isHeader ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(
                    inflater.inflate(R.layout.item_source_header, parent, false));
        }
        return new ItemViewHolder(
                inflater.inflate(R.layout.item_source, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).title.setText(row.title);
        } else {
            ItemViewHolder itemHolder = (ItemViewHolder) holder;
            itemHolder.title.setText(row.title);
            if (row.url == null) {
                itemHolder.url.setVisibility(View.GONE);
            } else {
                itemHolder.url.setVisibility(View.VISIBLE);
                itemHolder.url.setText(row.url);
            }
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {

        final TextView title;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_section_title);
        }
    }

    class ItemViewHolder extends RecyclerView.ViewHolder {

        final TextView title;
        final TextView url;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_source_title);
            url = itemView.findViewById(R.id.tv_source_url);
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && !rows.get(position).isHeader) {
                    listener.onSourceClick(rows.get(position).url);
                }
            });
        }
    }
}
