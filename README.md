# 倒数日

一个简洁、现代的倒数日 / 纪念日 Android 应用，用 Kotlin + Jetpack Compose 编写。

记录每一个重要的日子 —— 生日、纪念日、考试、旅行……倒数或累计，陪伴你的时光。

## 功能特性

- **事件管理**：新增、编辑、删除、归档事件，支持名称、日期、描述、配图、分类、农历日期与重复事件（每天 / 每周 / 每月 / 每年）
- **倒数 / 累计**：自动计算距今天数，未来事件显示「还有 N 天」，过去显示「已 N 天」，今天显示「就是今天」；点一下数字还能切换到累计时长（几年几月几天）
- **展示页**：全屏大卡片展示重要日子，支持分享与保存为图片
- **三种卡片效果**：素纸（实色卡）/ 毛玻璃（通透模糊）/ 液态玻璃（真实折射质感），在展示页「背景」面板里切换
- **AI 智能裁切**：内置轻量显著性模型（U2-NetP + ncnn 推理），自动识别照片主体并居中，人物不再被裁出画面；卡面文字会按背景明暗自动切换深浅色
- **提醒通知**：一个事件可设多条提醒（最多 5 条），每条独立设提前天数（当天 / 1 / 2 / 3 / 5 / 7 / 14 / 30 天）与时刻，各自独立触发；重复事件的下一次提醒自动排程
- **纪念日预告**：已过去的日子自动推导下一个纪念节点（第 100 / 200 / 300… 天与每一周年），在列表卡、详情页和桌面小组件上提前预告
- **置顶 & 搜索**：重要事件一键置顶（实心 / 空心图钉区分状态），顶部搜索实时筛选，多组排序方式
- **时间线节点**：为每个事件添加多个时间线节点，按时间顺序串联展示；节点可配图、加文案，记录每个重要时刻
- **日期选择器**：自研月历选择器，标注法定节假日与调休工作日（放假标「休」、调休上班标「班」），内置国务院公布的 2026、2027 年安排；时间选择为滚轮式
- **备份与恢复**：事件数据可导出备份、再导入恢复，导入带事务保护，失败自动回滚
- **桌面小组件**：两款——列表款（最近三个日子）与主视觉款（单个日子整卡呈现：配图背景 + 智能裁切 + 衬线大数字，文字按背景明暗自动切换深浅色）；主视觉款添加时可选要显示的事件，长按可重新配置（视启动器支持）
- **主题模式**：跟随系统 / 浅色 / 深色三种模式；Android 12+ 支持从壁纸动态取色
- **Material 3**：大圆角卡片、柔和层次

## 技术栈

| 分类 | 技术 |
|------|------|
| 语言 | Kotlin（原生层 JNI / C++ 供模型推理） |
| UI | Jetpack Compose + Material 3 |
| 架构 | 单 Activity + Navigation Compose + MVVM |
| 数据库 | Room (KSP) |
| 异步 | Kotlin Coroutines + Flow + StateFlow |
| 图片加载 | Coil |
| 图片选择 | ActivityResultContracts.PickVisualMedia（系统 Photo Picker） |
| 玻璃效果 | Haze（毛玻璃）+ 自研 AGSL 折射着色器（液态玻璃） |
| AI 推理 | ncnn + U2-NetP 显著性模型（约 2.3MB，端侧离线运行） |
| 构建 | Gradle Kotlin DSL + Version Catalog |
| CI/CD | 构建机本地脚本（R8 混淆，仅 arm64-v8a） |

## 项目结构

```
app/src/main/java/com/example/countdowndays/
├── CountdownApp.kt            # Application + 依赖容器
├── MainActivity.kt            # 入口 Activity，主题驱动
├── widget/                    # 桌面小组件（列表款、主视觉款、主视觉配置界面）
├── data/                      # 数据层
│   ├── EventEntity.kt         # 事件表
│   ├── TimelineNodeEntity.kt  # 时间线节点表
│   ├── ReminderEntity.kt      # 提醒表（事件 1:N 多节点提醒）
│   ├── EventWithNodes.kt      # 事件 + 节点关联
│   ├── EventDao.kt            # Room DAO
│   ├── AppDatabase.kt         # 数据库
│   └── EventRepository.kt     # 仓库
├── util/                      # 工具类
│   ├── DateUtils.kt           # 日期计算（含农历）
│   ├── Anniversary.kt         # 纪念日（整百天 / 周年）推导
│   ├── ChinaHolidays.kt       # 法定节假日与调休工作日数据
│   ├── ImageStorage.kt        # 图片存储
│   ├── PrefsManager.kt        # 偏好设置（主题、卡片效果、组件选事件等）
│   ├── ReminderScheduler.kt   # 提醒调度
│   ├── SmartCrop.kt           # 智能裁切（显著性网格 + 裁切窗搜索 + 字色亮度分析）
│   └── SaliencyNative.kt      # ncnn 推理 JNI 桥
├── cpp/                       # saliency_jni.cpp：U2-NetP ncnn 推理
└── ui/                        # UI 层
    ├── theme/                 # 主题、配色、字体
    ├── navigation/            # 路由导航
    ├── common/                # 公共组件（EventCard、ViewModel 工厂）
    ├── main/                  # 主页
    ├── editevent/             # 新增/编辑事件
    ├── detail/                # 事件详情 + 时间线
    ├── showcase/              # 展示页（大卡片、三种卡片效果、分享图）
    ├── settings/              # 设置
    └── about/                 # 关于
```

## 构建

需要 JDK 17 + Android SDK 36。原生层（ncnn 推理）构建需要 NDK 27 与 ncnn Android 静态包（路径由 `NCNN_DIR` 环境变量指定，缺省指向构建机上的工具目录）。

```bash
# 编译 Release APK（开启 R8 + 资源压缩，只打包 arm64-v8a）
./gradlew assembleRelease

# 产物路径
app/build/outputs/apk/release/app-release.apk
```

### 发布签名

Release 包使用独立签名密钥，签名信息从仓库根目录的 `keystore.properties` 读取（**该文件不入库**）：

```properties
storeFile=/absolute/path/to/days-release.jks
storePassword=******
keyAlias=days
keyPassword=******
```

没有该文件时会自动回退到 debug 签名，方便任意机器验证构建，但这种包不能用于正式发布。

## 发布

发布不再依赖 GitHub Actions，由构建机本地脚本完成（构建 → 校验 → 打 tag → 上传 Release）：

```bash
./scripts/release.sh              # 构建并发布
./scripts/release.sh --build-only # 只构建，产物在 dist/
./scripts/release.sh --draft      # 发布为 draft
```

脚本会强制：

- release 构建（R8 混淆 + 资源压缩），不会误发 debug 包
- 只保留 `arm64-v8a` 单一 ABI，产物名 `days-v<version>-arm64-v8a.apk`
- ABI 与签名校验通过才上传
- Release Notes 从 [CHANGELOG.md](CHANGELOG.md) 自动提取对应版本内容

发布前请先提升 `app/build.gradle.kts` 中的 `versionCode` 与 `versionName`。GitHub Releases 每天只保留当天最新一个版本。

## 下载

最新版本（v4.5.2）请前往 [Releases](https://github.com/autumn231/days/releases) 页面下载 APK 安装。

## 关于

- 作者：[autumn231](https://github.com/autumn231)
- 仓库：https://github.com/autumn231/days
- 许可：MIT License
