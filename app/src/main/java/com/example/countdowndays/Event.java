package com.example.countdowndays;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Event implements Serializable {
    private int id;
    private String name;
    private String description;
    private long eventDate; // 改为long类型
    private String imagePath;
    private boolean isPinned;
    private List<TimelineNode> timelineNodes; // 替代原有的timeline字符串

    public Event() {
        this.timelineNodes = new ArrayList<>();
        this.isPinned = false;
    }

    // 保留原有的getter和setter方法
    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getEventDate() { return eventDate; }
    public void setEventDate(long eventDate) { this.eventDate = eventDate; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    // 添加timelineNodes的getter和setter
    public List<TimelineNode> getTimelineNodes() {
        return timelineNodes;
    }

    public void setTimelineNodes(List<TimelineNode> timelineNodes) {
        this.timelineNodes = timelineNodes;
    }

    // 添加方法用于数据库存储和读取
    public String getTimelineString() {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (TimelineNode node : timelineNodes) {
            sb.append(node.getName()).append("|")
              .append(sdf.format(node.getTime())).append("->");
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 2); // 删除最后一个->
        }
        return sb.toString();
    }

    public void setTimelineString(String timelineString) {
        timelineNodes.clear();
        if (timelineString != null && !timelineString.isEmpty()) {
            String[] nodes = timelineString.split("->");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for (String nodeStr : nodes) {
                String[] parts = nodeStr.split("\\|");
                if (parts.length == 2) {
                    try {
                        TimelineNode node = new TimelineNode(parts[0], sdf.parse(parts[1]));
                        timelineNodes.add(node);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
