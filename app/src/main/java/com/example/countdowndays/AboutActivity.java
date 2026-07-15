package com.example.countdowndays;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // 添加顶部Toolbar（保持与主界面风格一致）
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 显示返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("关于");  // 标题设为"关于"
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);  // 显示返回箭头
        }
    }

    // 处理返回按钮点击（返回上一页）
    @Override
    public boolean onSupportNavigateUp() {
        finish();  // 关闭当前页面
        return true;
    }
}
