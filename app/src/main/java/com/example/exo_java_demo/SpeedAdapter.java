package com.example.exo_java_demo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

public class SpeedAdapter extends RecyclerView.Adapter<SpeedAdapter.SpeedViewHolder> {

    private float[] speeds;
    private OnItemClickListener onItemClickListener;
    private float currentSpeed;

    public SpeedAdapter(float[] speeds, OnItemClickListener onItemClickListener, float currentSpeed) {
        this.speeds = speeds;
        this.onItemClickListener = onItemClickListener;
        this.currentSpeed = currentSpeed;
    }

    /**
     * 当前生效档位更新（选档后调用）：对勾渲染依据 adapter 自己持有的档位，
     * 只 notify 不更新它的话，重开对话框对勾仍停在构造时的档位
     * （T1.1 验收实测抓出：选 2.0x 生效后重开对话框对勾仍在 1.0x）。
     */
    public void setCurrentSpeed(float currentSpeed) {
        this.currentSpeed = currentSpeed;
    }

    @NonNull
    @Override
    public SpeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.speed_item, parent, false);
        return new SpeedViewHolder(view, onItemClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull SpeedViewHolder holder, int position) {
        float speed = speeds[position];
        holder.tvSpeed.setText(String.format(Locale.US, "%.1fx", speed));
        if (speed == currentSpeed) {
            holder.ivCheckMark.setVisibility(View.VISIBLE);
        } else {
            holder.ivCheckMark.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return speeds.length;
    }

    public interface OnItemClickListener {
        void onItemClick(float speed);
    }

    public static class SpeedViewHolder extends RecyclerView.ViewHolder {

        TextView tvSpeed;
        ImageView ivCheckMark;
        public SpeedViewHolder(@NonNull View itemView, final OnItemClickListener onItemClickListener) {
            super(itemView);
            tvSpeed = itemView.findViewById(R.id.tvSpeed);
            ivCheckMark = itemView.findViewById(R.id.ivCheckMark);
            itemView.setOnClickListener(v -> {
                String value = tvSpeed.getText().toString();
                value = value.substring(0, value.length() - 1);
                onItemClickListener.onItemClick(Float.parseFloat(value));
            });
        }
    }
}




