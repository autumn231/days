// 修复导入部分
package com.example.countdowndays;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// 添加缺少的导入
import android.os.Build;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;

// 其他必要导入
import android.view.Menu;
import android.view.MenuItem;
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.graphics.Color;

// 确保正确继承自AppCompatActivity
public class MainActivity extends AppCompatActivity implements EventAdapter.OnItemClickListener {
    private static final int REQUEST_PERMISSIONS = 1;
    private RecyclerView recyclerView;
    private EventAdapter adapter;
    private DatabaseHelper db;
    private List<Event> allEvents;
    private EditText searchBar;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // 设置菜单文字颜色
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            SpannableString spannable = new SpannableString(item.getTitle());
            spannable.setSpan(new ForegroundColorSpan(Color.BLACK), 0, spannable.length(), 0);
            item.setTitle(spannable);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivityForResult(intent, 3);
            return true;
        }

        if (id == R.id.action_about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // 检测小米系统方法
    private boolean isXiaomiDevice() {
        String manufacturer = Build.MANUFACTURER;
        return manufacturer != null && manufacturer.equalsIgnoreCase("Xiaomi");
    }

    // 获取状态栏高度方法
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 通用状态栏适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11及以上版本
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            // Android 10及以下版本
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }

        setContentView(R.layout.activity_main);

        // 处理Toolbar容器的padding，适配状态栏
        FrameLayout toolbarContainer = findViewById(R.id.toolbar_container);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11及以上使用系统提供的inset
            View.OnApplyWindowInsetsListener insetsListener = (v, insets) -> {
                int statusBarHeight = insets.getInsets(WindowInsets.Type.statusBars()).top;
                toolbarContainer.setPadding(0, statusBarHeight, 0, 0);
                return insets;
            };
            toolbarContainer.setOnApplyWindowInsetsListener(insetsListener);
        } else {
            // 低版本手动计算状态栏高度
            int statusBarHeight = getStatusBarHeight();
            toolbarContainer.setPadding(0, statusBarHeight, 0, 0);
        }

        // 绑定Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("祝你天天开心(●'◡'●)");
        }

        // 设置状态栏字体颜色为深色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        // 小米设备特殊处理
        if (isXiaomiDevice()) {
            // 设置状态栏字体颜色为黑色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }

        // 检查权限
        checkPermissions();

        // 初始化数据库
        db = new DatabaseHelper(this);

        // 初始化视图
        recyclerView = findViewById(R.id.events_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        searchBar = findViewById(R.id.search_bar);

        // 加载事件
        loadEvents();

        // 设置搜索功能
        setupSearch();

        // 设置添加按钮点击事件
        FloatingActionButton addButton = findViewById(R.id.add_button);
        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddEditEventActivity.class);
            startActivityForResult(intent, 1);
        });

        // 加载背景
        loadBackground();
    }

    // 检查权限
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予
            } else {
                Toast.makeText(this, "需要存储权限才能正常使用应用", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 加载事件列表
    private void loadEvents() {
        allEvents = db.getAllEvents();
        adapter = new EventAdapter(this, allEvents, this);
        recyclerView.setAdapter(adapter);
    }

    // 设置搜索功能
    private void setupSearch() {
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    adapter = new EventAdapter(MainActivity.this, allEvents, MainActivity.this);
                } else {
                    List<Event> filteredEvents = db.searchEvents(query);
                    adapter = new EventAdapter(MainActivity.this, filteredEvents, MainActivity.this);
                }
                recyclerView.setAdapter(adapter);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    public void onItemClick(Event event) {
        Intent intent = new Intent(this, EventDetailActivity.class);
        intent.putExtra("event", event);
        startActivityForResult(intent, 2);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            // 刷新事件列表
            loadEvents();
        }

        // 如果是从设置回来的，重新加载背景
        if (requestCode == 3 && resultCode == RESULT_OK) {
            loadBackground();
        }
    }

    // 添加onResume方法，确保从设置页面返回时重新加载背景
    @Override
    protected void onResume() {
        super.onResume();
        loadBackground();
    }
    
    // 改进loadBackground方法，添加错误处理和日志
    private void loadBackground() {
        try {
            SharedPreferences prefs = getSharedPreferences("CountdownDaysPrefs", MODE_PRIVATE);
            String bgPath = prefs.getString("backgroundPath", null);
    
            if (bgPath != null) {
                File file = new File(bgPath);
                if (file.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(file));
                    View mainLayout = findViewById(R.id.main_layout);
                    if (mainLayout != null) {
                        mainLayout.setBackground(new BitmapDrawable(getResources(), bitmap));
                    } else {
                        Toast.makeText(this, "未找到main_layout布局", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "背景图片文件不存在", Toast.LENGTH_SHORT).show();
                }
            } else {
                // 可选：当没有背景图片时，设置默认背景
                findViewById(R.id.main_layout).setBackgroundResource(android.R.color.white);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "加载背景失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
