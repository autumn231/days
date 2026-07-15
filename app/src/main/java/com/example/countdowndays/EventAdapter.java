package com.example.countdowndays;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Calendar;
import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {
    private Context context;
    private List<Event> eventList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Event event);
    }

    public EventAdapter(Context context, List<Event> eventList, OnItemClickListener listener) {
        this.context = context;
        this.eventList = eventList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.event_item, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = eventList.get(position);

        // 设置事件名称
        holder.eventName.setText(event.getName());

        // 计算剩余天数
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long diff = event.getEventDate() - currentTime;
        long days = diff / (1000 * 60 * 60 * 24);

        if (days > 0) {
            holder.eventDays.setText("还有 " + days + " 天");
        } else if (days == 0) {
            holder.eventDays.setText("今天");
        } else {
            holder.eventDays.setText("已过去 " + (-days) + " 天");
        }

        // 设置图片
        if (event.getImagePath() != null && !event.getImagePath().isEmpty()) {
            File imgFile = new File(event.getImagePath());
            if (imgFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                holder.eventImage.setImageBitmap(bitmap);
            }
        }

        // 设置置顶图标显示
        holder.pinIcon.setVisibility(event.isPinned() ? View.VISIBLE : View.GONE);

        // 设置点击事件
        holder.itemView.setOnClickListener(v -> listener.onItemClick(event));
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }

    public static class EventViewHolder extends RecyclerView.ViewHolder {
        ImageView eventImage, pinIcon;
        TextView eventName, eventDays;

        public EventViewHolder(View itemView) {
            super(itemView);
            eventImage = itemView.findViewById(R.id.event_image);
            eventName = itemView.findViewById(R.id.event_name);
            eventDays = itemView.findViewById(R.id.event_days);
            pinIcon = itemView.findViewById(R.id.pin_icon); // 新增置顶图标
        }
    }
}
