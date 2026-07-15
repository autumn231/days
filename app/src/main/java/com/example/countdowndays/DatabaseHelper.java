package com.example.countdowndays;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "EventsDatabase";
    private static final int DATABASE_VERSION = 2; // 增加数据库版本
    private static final String TABLE_EVENTS = "events";

    // 表的列名
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_DATE = "date";
    private static final String KEY_IMAGE = "image";
    private static final String KEY_TIMELINE = "timeline";
    private static final String KEY_PINNED = "is_pinned"; // 新增置顶列

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createEventsTable = "CREATE TABLE " + TABLE_EVENTS + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_NAME + " TEXT,"
                + KEY_DESCRIPTION + " TEXT,"
                + KEY_DATE + " LONG,"
                + KEY_IMAGE + " TEXT,"
                + KEY_TIMELINE + " TEXT,"
                + KEY_PINNED + " INTEGER DEFAULT 0" + ")"; // 0表示false
        db.execSQL(createEventsTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // 添加置顶列
            db.execSQL("ALTER TABLE " + TABLE_EVENTS + " ADD COLUMN " + KEY_PINNED + " INTEGER DEFAULT 0");
        }
    }

    // 更新添加事件方法
    public long addEvent(Event event) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_NAME, event.getName());
        values.put(KEY_DESCRIPTION, event.getDescription());
        values.put(KEY_DATE, event.getEventDate());
        values.put(KEY_IMAGE, event.getImagePath());
        values.put(KEY_TIMELINE, event.getTimelineString()); // 使用新方法
        values.put(KEY_PINNED, event.isPinned() ? 1 : 0);

        long id = db.insert(TABLE_EVENTS, null, values);
        db.close();
        return id;
    }

    // 删除重复的getEvent方法

    // 更新获取所有事件方法 - 置顶事件优先
    public List<Event> getAllEvents() {
        List<Event> eventList = new ArrayList<>();
        String selectQuery = "SELECT  * FROM " + TABLE_EVENTS + " ORDER BY " + KEY_PINNED + " DESC, " + KEY_DATE + " ASC";

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Event event = new Event();
                event.setId(Integer.parseInt(cursor.getString(0)));
                event.setName(cursor.getString(1));
                event.setDescription(cursor.getString(2));
                event.setEventDate(Long.parseLong(cursor.getString(3)));
                event.setImagePath(cursor.getString(4));
                event.setTimelineString(cursor.getString(5)); // 使用新方法
                event.setPinned(cursor.getInt(6) == 1);
                eventList.add(event);
            } while (cursor.moveToNext());
        }

        cursor.close();
        return eventList;
    }

    // 更新更新事件方法
    public void updateEvent(Event event) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_NAME, event.getName());
        values.put(KEY_DESCRIPTION, event.getDescription());
        values.put(KEY_DATE, event.getEventDate());
        values.put(KEY_IMAGE, event.getImagePath());
        values.put(KEY_PINNED, event.isPinned() ? 1 : 0);
        values.put(KEY_TIMELINE, event.getTimelineString());

        db.update(TABLE_EVENTS, values, KEY_ID + " = ?",
                new String[]{String.valueOf(event.getId())});
        db.close();
    }

    // 获取事件
    public Event getEvent(int id) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_EVENTS, new String[]{KEY_ID,
                        KEY_NAME, KEY_DESCRIPTION, KEY_DATE, KEY_IMAGE, KEY_TIMELINE, KEY_PINNED},
                KEY_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null, null);

        Event event = null;
        if (cursor != null && cursor.moveToFirst()) {
            event = new Event();
            event.setId(cursor.getInt(0));
            event.setName(cursor.getString(1));
            event.setDescription(cursor.getString(2));
            event.setEventDate(Long.parseLong(cursor.getString(3)));
            event.setImagePath(cursor.getString(4));
            event.setTimelineString(cursor.getString(5));
            event.setPinned(cursor.getInt(6) == 1);
            cursor.close();
        }
        db.close();
        return event;
    }

    // 添加删除事件方法
    public void deleteEvent(Event event) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_EVENTS, KEY_ID + " = ?",
                new String[]{String.valueOf(event.getId())});
        db.close();
    }

    // 添加搜索事件方法
    public List<Event> searchEvents(String query) {
        List<Event> eventList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_EVENTS + 
                            " WHERE " + KEY_NAME + " LIKE '%" + query + "%' OR " + 
                            KEY_DESCRIPTION + " LIKE '%" + query + "%'" +
                            " ORDER BY " + KEY_PINNED + " DESC, " + KEY_DATE + " ASC";

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Event event = new Event();
                event.setId(Integer.parseInt(cursor.getString(0)));
                event.setName(cursor.getString(1));
                event.setDescription(cursor.getString(2));
                event.setEventDate(Long.parseLong(cursor.getString(3)));
                event.setImagePath(cursor.getString(4));
                event.setTimelineString(cursor.getString(5));
                event.setPinned(cursor.getInt(6) == 1);
                eventList.add(event);
            } while (cursor.moveToNext());
        }

        cursor.close();
        return eventList;
    }
}

