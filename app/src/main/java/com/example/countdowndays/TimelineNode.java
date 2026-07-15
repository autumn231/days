package com.example.countdowndays;

import java.io.Serializable;
import java.util.Date;

public class TimelineNode implements Serializable {
    private String name;
    private Date time;

    public TimelineNode(String name, Date time) {
        this.name = name;
        this.time = time;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }
}