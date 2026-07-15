package com.example.countdowndays;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

// 修改类继承，从AppCompatActivity改为BaseActivity
public class AddEditEventActivity extends BaseActivity {
    private static final String TAG = "AddEditEventActivity";
    private static final int REQUEST_PERMISSION = 2;

    private EditText eventName, eventDescription;
    private Button selectDateButton, saveButton, selectImageButton;
    private ImageView eventImagePreview;
    private Calendar selectedDate;
    private Event editingEvent;
    private DatabaseHelper db;
    private String imagePath;

    // 添加ActivityResultLauncher替代startActivityForResult
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_event);

        eventName = findViewById(R.id.event_name);
        eventDescription = findViewById(R.id.event_description);
        selectDateButton = findViewById(R.id.select_date);
        saveButton = findViewById(R.id.save_button);
        selectImageButton = findViewById(R.id.select_image);
        eventImagePreview = findViewById(R.id.event_image_preview);

        db = new DatabaseHelper(this);
        selectedDate = Calendar.getInstance();

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

                            // 生成唯一文件名
                            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                            String imageFileName = "JPEG_" + timeStamp + "_";
                            File file = File.createTempFile(
                                    imageFileName,  /* prefix */
                                    ".jpg",         /* suffix */
                                    directory      /* directory */
                            );

                            OutputStream outputStream = new FileOutputStream(file);
                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }
                            outputStream.close();
                            inputStream.close();

                            // 保存图片路径并显示预览
                            imagePath = file.getAbsolutePath();
                            eventImagePreview.setImageURI(selectedImageUri);

                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(this, "图片处理失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        // 设置日期选择按钮
        selectDateButton.setOnClickListener(v -> showDatePicker());
        updateDateButtonText();

        // 设置保存按钮
        saveButton.setOnClickListener(v -> saveEvent());

        // 设置选择图片按钮
        selectImageButton.setOnClickListener(v -> selectImage());

        // 检查是否是编辑模式
        Intent intent = getIntent();
        if (intent.hasExtra("event_id")) {
            int eventId = intent.getIntExtra("event_id", -1);
            // 修改方法名从getEventById到getEvent
            editingEvent = db.getEvent(eventId);
            if (editingEvent != null) {
                eventName.setText(editingEvent.getName());
                eventDescription.setText(editingEvent.getDescription());
                selectedDate.setTimeInMillis(editingEvent.getEventDate());
                updateDateButtonText();

                // 加载图片
                if (editingEvent.getImagePath() != null) {
                    imagePath = editingEvent.getImagePath();
                    File imgFile = new File(imagePath);
                    if (imgFile.exists()) {
                        eventImagePreview.setImageURI(Uri.fromFile(imgFile));
                    }
                }
            }
        }
    }

    // 更新日期按钮文本
    private void updateDateButtonText() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
        selectDateButton.setText(sdf.format(selectedDate.getTime()));
    }

    // 显示日期选择器
    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(year, month, dayOfMonth);
                    updateDateButtonText();
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    // 选择图片
    private void selectImage() {
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

    // 保存事件
    private void saveEvent() {
        String name = eventName.getText().toString().trim();
        String description = eventDescription.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "请输入事件名称", Toast.LENGTH_SHORT).show();
            return;
        }

        if (editingEvent != null) {
            // 编辑现有事件
            editingEvent.setName(name);
            editingEvent.setDescription(description);
            editingEvent.setEventDate(selectedDate.getTimeInMillis());
            if (imagePath != null) {
                editingEvent.setImagePath(imagePath);
            }

            db.updateEvent(editingEvent);
            Toast.makeText(this, "事件已更新", Toast.LENGTH_SHORT).show();
        } else {
            // 创建新事件
            Event newEvent = new Event();
            newEvent.setName(name);
            newEvent.setDescription(description);
            newEvent.setEventDate(selectedDate.getTimeInMillis());
            newEvent.setImagePath(imagePath);

            long id = db.addEvent(newEvent);
            if (id != -1) {
                Toast.makeText(this, "事件已添加", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "添加失败", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        setResult(RESULT_OK);
        finish();
    }
}
