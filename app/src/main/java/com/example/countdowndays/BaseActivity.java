package com.example.countdowndays;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import androidx.appcompat.app.AppCompatActivity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BaseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 适配状态栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11及以上版本
            getWindow().setDecorFitsSystemWindows(false);
            Window window = getWindow();
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        } else {
            // Android 11以下版本
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);

            // 小米设备特殊处理
            if (isXiaomiDevice()) {
                setXiaomiStatusBarLightMode(true);
            }
        }
    }

    // 检查是否为小米设备
    private boolean isXiaomiDevice() {
        return Build.MANUFACTURER.equalsIgnoreCase("xiaomi");
    }

    // 设置小米设备状态栏字体颜色
    private void setXiaomiStatusBarLightMode(boolean dark) {
        try {
            Window window = getWindow();
            Class<?> clazz = window.getClass();
            Class<?> layoutParams = Class.forName("android.view.MiuiWindowManager$LayoutParams");
            Field field = layoutParams.getField("EXTRA_FLAG_STATUS_BAR_DARK_MODE");
            int darkModeFlag = field.getInt(layoutParams);
            Method extraFlagField = clazz.getMethod("setExtraFlags", int.class, int.class);
            extraFlagField.invoke(window, dark ? darkModeFlag : 0, darkModeFlag);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}