package com.example.exo_java_demo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Tracks;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 画质/音轨选择列表（复用 speed_item 布局，勾选标记当前生效档位）。
 */
public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    /** 一个可选档位：isAuto 时 group/trackIndex 无效。 */
    public static class Option {
        public final int trackType; // C.TRACK_TYPE_VIDEO / C.TRACK_TYPE_AUDIO
        public final boolean isAuto;
        @Nullable
        public final Tracks.Group group;
        public final int trackIndex;
        public final String label;
        public final boolean selected;

        public Option(int trackType, Tracks.Group group, int trackIndex, String label,
                boolean selected) {
            this.trackType = trackType;
            this.group = group;
            this.trackIndex = trackIndex;
            this.label = label;
            this.selected = selected;
            this.isAuto = group == null;
        }

        public static Option auto(int trackType, String label, boolean selected) {
            return new Option(trackType, null, -1, label, selected);
        }
    }

    public interface OnOptionClickListener {
        void onOptionClick(Option option);
    }

    private final List<Option> options;
    private final OnOptionClickListener listener;

    public TrackAdapter(List<Option> options, OnOptionClickListener listener) {
        this.options = options;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.speed_item, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Option option = options.get(position);
        holder.label.setText(option.label);
        holder.checkMark.setVisibility(option.selected ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public int getItemCount() {
        return options.size();
    }

    class TrackViewHolder extends RecyclerView.ViewHolder {

        final TextView label;
        final ImageView checkMark;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.tvSpeed);
            checkMark = itemView.findViewById(R.id.ivCheckMark);
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onOptionClick(options.get(position));
                }
            });
        }
    }
}
