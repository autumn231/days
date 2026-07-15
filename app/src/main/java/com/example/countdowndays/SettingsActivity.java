package com.example.countdowndays;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

// 修改类继承，从AppCompatActivity改为BaseActivity
public class SettingsActivity extends BaseActivity {
    private static final String PREFS_NAME = "CountdownDaysPrefs";
    private static final String KEY_BACKGROUND_PATH = "backgroundPath";

    private Button selectBackgroundButton, removeBackgroundButton;
    private ImageView backgroundPreview;
    private String backgroundPath;

    // 添加ActivityResultLauncher替代startActivityForResult
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        selectBackgroundButton = findViewById(R.id.select_background);
        removeBackgroundButton = findViewById(R.id.remove_background);
        backgroundPreview = findViewById(R.id.background_preview);

        // 初始化权限请求launcher
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean hasPermission = false;
                    for (Boolean granted : result.values()) {
                        if (granted) {
                            hasPermission = true;
                            break;
                        }
                    }

                    if (hasPermission) {
                        openImagePicker();
                    } else {
                        Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show();
                    }
                });

        // 初始化图片选择launcher
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        try {
                            // 将选中的图片保存到应用目录
                            InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                            File directory = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "CountdownDays");
                            if (!directory.exists()) {
                                directory.mkdirs();
                            }

                            File file = new File(directory, "background.jpg");
                            OutputStream outputStream = new FileOutputStream(file);
                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }
                            outputStream.close();
                            inputStream.close();

                            // 保存图片路径并显示预览
                            backgroundPath = file.getAbsolutePath();
                            backgroundPreview.setImageURI(selectedImageUri);

                            // 保存到SharedPreferences
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                    .putString(KEY_BACKGROUND_PATH, backgroundPath).apply();

                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(this, "图片处理失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        selectBackgroundButton.setOnClickListener(v -> selectBackgroundImage());
        removeBackgroundButton.setOnClickListener(v -> removeBackground());

        // 加载已保存的背景
        loadBackground();
    }

    // 选择背景图片
    private void selectBackgroundImage() {
        if (PermissionUtils.hasReadImagePermission(this)) {
            openImagePicker();
        } else {
            PermissionUtils.requestReadImagePermission(this, permissionLauncher);
        }
    }

    // 打开图片选择器
    private void openImagePicker() {
        // 使用ACTION_PICK打开相册应用
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    // 加载背景图片
    private void loadBackground() {
        backgroundPath = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_BACKGROUND_PATH, null);

        if (backgroundPath != null) {
            File imgFile = new File(backgroundPath);
            if (imgFile.exists()) {
                backgroundPreview.setImageURI(Uri.fromFile(imgFile));
            }
        }
    }

    // 移除背景图片
    private void removeBackground() {
        // 删除保存的背景图片
        if (backgroundPath != null) {
            File imgFile = new File(backgroundPath);
            if (imgFile.exists()) {
                imgFile.delete();
            }
        }

        // 清除保存的路径
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .remove(KEY_BACKGROUND_PATH).apply();

        // 重置预览
        backgroundPreview.setImageResource(android.R.color.darker_gray);
        backgroundPath = null;
    }
}

