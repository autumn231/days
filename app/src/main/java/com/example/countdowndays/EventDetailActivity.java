package com.example.countdowndays;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;  // 添加这行导入
import java.util.List;
import java.util.Locale;

public class EventDetailActivity extends AppCompatActivity {
    private Event event;
    private DatabaseHelper db;
    private boolean isPinned;

    // 添加视图变量定义
    private ImageView eventImage;
    private TextView eventName;
    private TextView eventDate;
    private TextView eventDays;
    private TextView eventDescription;
    private EditText eventTimeline;
    private Button editButton;
    private Button deleteButton;
    private Button saveTimelineButton;
    private Button editTimelineButton;
    private LinearLayout timelineContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        // 添加Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("事件详情");
        }

        // 初始化数据库
        db = new DatabaseHelper(this);

        // 获取事件数据
        Intent intent = getIntent();
        event = (Event) intent.getSerializableExtra("event");

        if (event == null) {
            Toast.makeText(this, "无效的事件数据", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 修改：根据ID从数据库重新加载事件，确保获取最新数据
        int eventId = event.getId();
        event = db.getEvent(eventId);
        if (event == null) {
            Toast.makeText(this, "事件不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        isPinned = event.isPinned();

        // 初始化视图
        eventImage = findViewById(R.id.event_image);
        eventName = findViewById(R.id.event_name);
        eventDate = findViewById(R.id.event_date);
        eventDays = findViewById(R.id.event_days);
        eventDescription = findViewById(R.id.event_description);
        // 不再需要这些视图，可以移除或隐藏
        // eventTimeline = findViewById(R.id.event_timeline);
        editButton = findViewById(R.id.edit_button);
        deleteButton = findViewById(R.id.delete_button);
        // saveTimelineButton = findViewById(R.id.save_timeline_button);
        editTimelineButton = findViewById(R.id.edit_timeline_button);
        timelineContainer = findViewById(R.id.timeline_container);

        // 隐藏不再需要的视图
        findViewById(R.id.event_timeline).setVisibility(View.GONE);
        findViewById(R.id.save_timeline_button).setVisibility(View.GONE);

        // 显示事件详情
        displayEventDetails();

        // 设置按钮点击事件
        editButton.setOnClickListener(v -> editEvent());
        deleteButton.setOnClickListener(v -> deleteEvent());
        // 删除下面这行代码，因为saveTimelineButton未初始化
        // saveTimelineButton.setOnClickListener(v -> saveTimeline());
        editTimelineButton.setOnClickListener(v -> showTimelineEditor());
    }

    // 显示时间脉络编辑器
    private void showTimelineEditor() {
        // 直接打开编辑对话框，不再显示中间的编辑界面
        saveTimeline();
    }

    // 更新竖状时间脉络显示
    private void updateVerticalTimeline() {
        timelineContainer.removeAllViews();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        List<TimelineNode> nodes = event.getTimelineNodes();
        if (nodes != null && !nodes.isEmpty()) {
            for (int i = 0; i < nodes.size(); i++) {
                TimelineNode node = nodes.get(i);
                View nodeView = LayoutInflater.from(this).inflate(R.layout.timeline_node, null);
                TextView nodeText = nodeView.findViewById(R.id.node_text);
                // 显示名称和时间
                nodeText.setText(node.getName() + " (" + sdf.format(node.getTime()) + ")");
                timelineContainer.addView(nodeView);
            }
        }
    }

    // 保存时间脉络
    private void saveTimeline() {
        // 打开自定义对话框让用户编辑时间节点
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("编辑时间脉络");

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_timeline, null);
        builder.setView(dialogView);

        LinearLayout nodesContainer = dialogView.findViewById(R.id.nodes_container);
        Button addNodeButton = dialogView.findViewById(R.id.add_node_button);

        // 加载现有时间节点
        List<TimelineNode> existingNodes = event.getTimelineNodes();
        if (existingNodes != null && !existingNodes.isEmpty()) {
            for (TimelineNode node : existingNodes) {
                addNodeView(nodesContainer, node.getName(), node.getTime());
            }
        } else {
            // 添加一个默认节点
            addNodeView(nodesContainer, "", new Date());
        }

        // 添加节点按钮点击事件
        addNodeButton.setOnClickListener(v -> addNodeView(nodesContainer, "", new Date()));

        builder.setPositiveButton("保存", (dialog, which) -> {
            List<TimelineNode> nodes = new ArrayList<>();

            // 收集所有节点数据
            int childCount = nodesContainer.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View nodeView = nodesContainer.getChildAt(i);
                EditText nameEditText = nodeView.findViewById(R.id.node_name);
                Button timeButton = nodeView.findViewById(R.id.node_time);

                String name = nameEditText.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(this, "节点名称不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 从按钮标签获取时间
                Date time = (Date) timeButton.getTag();
                if (time == null) {
                    time = new Date();
                }

                nodes.add(new TimelineNode(name, time));
            }

            // 保存节点数据
            event.setTimelineNodes(nodes);
            db.updateEvent(event);
            Toast.makeText(this, "时间脉络已保存", Toast.LENGTH_SHORT).show();

            // 刷新UI
            updateVerticalTimeline();
            // 不需要再切换视图可见性，因为我们直接从对话框返回
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // 添加时间节点视图
    private void addNodeView(LinearLayout container, String name, Date time) {
        View nodeView = LayoutInflater.from(this).inflate(R.layout.timeline_node_edit, null);
        EditText nameEditText = nodeView.findViewById(R.id.node_name);
        Button timeButton = nodeView.findViewById(R.id.node_time);
        Button removeButton = nodeView.findViewById(R.id.remove_node);

        // 设置节点名称
        nameEditText.setText(name);

        // 设置时间按钮
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        timeButton.setText(sdf.format(time));
        timeButton.setTag(time);

        // 时间选择按钮点击事件
        timeButton.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(time);

            new DatePickerDialog(EventDetailActivity.this, (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                new TimePickerDialog(EventDetailActivity.this, (view1, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    Date selectedTime = calendar.getTime();
                    timeButton.setText(sdf.format(selectedTime));
                    timeButton.setTag(selectedTime);
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        // 删除按钮点击事件
        removeButton.setOnClickListener(v -> container.removeView(nodeView));

        container.addView(nodeView);
    }

    // 显示事件详情
    private void displayEventDetails() {
        // 设置事件名称
        eventName.setText(event.getName());

        // 设置日期
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
        eventDate.setText(sdf.format(new Date(event.getEventDate()))); // 转换为Date类型

        // 计算并显示剩余天数
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long diff = event.getEventDate() - currentTime;
        long days = diff / (1000 * 60 * 60 * 24);

        if (days > 0) {
            eventDays.setText("还有 " + days + " 天");
        } else if (days == 0) {
            eventDays.setText("今天");
        } else {
            eventDays.setText("已过去 " + (-days) + " 天");
        }

        // 设置描述
        eventDescription.setText(event.getDescription());

        // 设置时间脉络
        // 这里需要将timelineNodes转换为字符串显示
        StringBuilder timelineStr = new StringBuilder();
        // 移除重复定义的sdf变量，使用方法内已有的sdf
        for (TimelineNode node : event.getTimelineNodes()) {
            timelineStr.append(node.getName()).append(" (").append(sdf.format(node.getTime())).append(")->");
        }
        if (timelineStr.length() > 0) {
            timelineStr.setLength(timelineStr.length() - 2);
        }
        // 删除这行代码，因为eventTimeline已被注释初始化
        // eventTimeline.setText(timelineStr.toString());
        updateVerticalTimeline();

        // 设置图片
        if (event.getImagePath() != null && !event.getImagePath().isEmpty()) {
            File imgFile = new File(event.getImagePath());
            if (imgFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                eventImage.setImageBitmap(bitmap);
            }
        }
    }

    // 编辑事件
    private void editEvent() {
        Intent intent = new Intent(this, AddEditEventActivity.class);
        intent.putExtra("event_id", event.getId());
        startActivityForResult(intent, 1);
    }

    // 删除事件
    private void deleteEvent() {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除这个事件吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    db.deleteEvent(event);
                    // 删除关联的图片
                    if (event.getImagePath() != null && !event.getImagePath().isEmpty()) {
                        File imgFile = new File(event.getImagePath());
                        if (imgFile.exists()) {
                            imgFile.delete();
                        }
                    }
                    setResult(RESULT_OK);
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            // 重新加载事件数据
            Event updatedEvent = db.getEvent(event.getId());
            if (updatedEvent != null) {
                event = updatedEvent;
                displayEventDetails();
                setResult(RESULT_OK);
            } else {
                Toast.makeText(this, "事件不存在", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.event_detail_menu, menu);
        // 更新置顶按钮状态
        MenuItem pinItem = menu.findItem(R.id.action_pin);
        updatePinMenuItem(pinItem);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            // 处理返回按钮点击事件
            finish();
            return true;
        }

        if (id == R.id.action_pin) {
            togglePinStatus();
            updatePinMenuItem(item);
            return true;
        }

        // 其他菜单项处理...

        return super.onOptionsItemSelected(item);
    }

    private void togglePinStatus() {
        // 检查是否已经有3个置顶事件
        if (!isPinned) {
            List<Event> allEvents = db.getAllEvents();
            int pinnedCount = 0;
            for (Event e : allEvents) {
                if (e.isPinned()) {
                    pinnedCount++;
                }
            }

            if (pinnedCount >= 3) {
                Toast.makeText(this, "最多只能置顶3个事件", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // 切换置顶状态
        isPinned = !isPinned;
        event.setPinned(isPinned);
        db.updateEvent(event);

        // 显示提示
        String message = isPinned ? "事件已置顶" : "事件已取消置顶";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        // 设置结果为OK，以便MainActivity刷新列表
        setResult(RESULT_OK);
    }

    private void updatePinMenuItem(MenuItem item) {
        if (isPinned) {
            item.setIcon(R.drawable.ic_pin);
            item.setTitle("取消置顶");
        } else {
            item.setIcon(R.drawable.ic_pin_outline);
            item.setTitle("置顶");
        }
    }
}

