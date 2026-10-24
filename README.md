# 倒数日

一个简洁、现代的倒数日 / 纪念日 Android 应用，用 Kotlin + Jetpack Compose 全新编写。

记录每一个重要的日子 —— 生日、纪念日、考试、旅行……倒数或累计，陪伴你的时光。

## 功能特性

- **事件管理**：新增、编辑、删除事件，支持名称、日期、描述和配图
- **倒数/累计**：自动计算距今天数，未来事件显示「还有 N 天」，过去事件显示「已 N 天」，今天显示「就是今天」
- **置顶 & 搜索**：重要事件一键置顶，顶部搜索框实时筛选
- **时间线节点**：为每个事件添加多个时间线节点，用竖线串联按时间顺序展示，记录每个重要时刻
- **图片自适应**：事件配图按方向智能布局 —— 横版铺满顶部、竖版左侧竖条、方框顶部居中
- **主题模式**：跟随系统 / 浅色 / 深色三种模式，切换即时生效
- **动态取色**：Android 12+ 支持从壁纸动态提取主题色
- **Material 3**：采用最新 Material Design 3 设计语言，大圆角卡片、柔和层次
- **治愈系图标**：像素风格的小太阳，圆润脸庞配柔和光芒

## 技术栈

| 分类 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | 单 Activity + Navigation Compose + MVVM |
| 数据库 | Room (KSP) |
| 异步 | Kotlin Coroutines + Flow + StateFlow |
| 图片加载 | Coil |
| 图片选择 | ActivityResultContracts.PickVisualMedia（系统 Photo Picker） |
| 构建 | Gradle Kotlin DSL + Version Catalog |
| CI/CD | 构建机本地脚本（R8 混淆，仅 arm64-v8a） |

## 项目结构

```
app/src/main/java/com/example/countdowndays/
├── CountdownApp.kt            # Application + 依赖容器
├── MainActivity.kt            # 入口 Activity，主题驱动
├── data/                      # 数据层
│   ├── EventEntity.kt         # 事件表
│   ├── TimelineNodeEntity.kt  # 时间线节点表
│   ├── EventWithNodes.kt      # 事件 + 节点关联
│   ├── EventDao.kt            # Room DAO
│   ├── AppDatabase.kt         # 数据库
│   └── EventRepository.kt     # 仓库
├── util/                      # 工具类
│   ├── DateUtils.kt           # 日期计算
│   ├── ImageStorage.kt        # 图片存储
│   └── PrefsManager.kt        # 主题偏好
└── ui/                        # UI 层
    ├── theme/                 # 主题、配色、字体
    ├── navigation/            # 路由导航
    ├── common/                # 公共组件（EventCard、ViewModel 工厂）
    ├── main/                  # 主页
    ├── editevent/             # 新增/编辑事件
    ├── detail/                # 事件详情 + 时间线
    ├── settings/              # 设置
    └── about/                 # 关于
```

## 构建

需要 JDK 17 + Android SDK 36。

```bash
# 编译 Release APK
./gradlew assembleRelease

# 产物路径
app/build/outputs/apk/release/app-release.apk
```

## 构建

需要 JDK 17 + Android SDK 36。

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

发布前请先提升 `app/build.gradle.kts` 中的 `versionCode` 与 `versionName`。

## 下载

最新版本请前往 [Releases](https://github.com/autumn231/days/releases) 页面下载 APK 安装。

> v2.3 起改用独立签名密钥，与旧版本签名不一致，覆盖安装会失败，需要先卸载旧版本。

## 关于

- 作者：高翔
- 联系方式：gx13598483383
