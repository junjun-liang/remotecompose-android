# Remote Compose Android 播放器

[Remote Compose Demo](https://github.com/armcha/remotecompose) 的 Android 客户端 —— 一个纯播放器应用，使用 [AndroidX Remote Compose](https://developer.android.com/jetpack/androidx/releases/compose-remote) 下载并渲染服务端驱动的 UI。

<img src="app-screenshot.png" width="300" alt="应用截图">

## 工作原理

该应用从 GitHub 下载预构建的二进制 Remote Compose 文档（`.rc` 文件），并使用 `RemoteComposePlayer` 进行原生渲染。无需 JSON 解析，无需 UI 创建代码 —— 只需二进制字节输入，原生 UI 输出。

```
GitHub (remotecompose 仓库)              Android 应用
┌────────────────────────┐              ┌────────────────────────┐
│  config.rc             │              │                        │
│  config_detail.rc      │  OkHttp GET  │  RemoteComposePlayer   │
│  config_estimates.rc   │ ───────────> │  渲染二进制文档         │
│  config_estimate_...rc │   ByteArray  │                        │
└────────────────────────┘              └────────────────────────┘
```

## 环境配置

1. 克隆此仓库
2. 将你的 GitHub token 添加到 `local.properties`：
   ```
   GITHUB_TOKEN=你的_github_token
   ```
3. 在 Android Studio 中构建并运行

Token 用于以更高的速率限制从 GitHub API 获取 `.rc` 文件。它通过 `BuildConfig` 读取，永远不会提交到源代码控制中。

## 架构

```
app/src/main/java/com/example/remotecompose/
├── MainActivity.kt              入口（全屏 + Compose）
├── MainViewModel.kt             获取 .rc 文档，管理 UI 状态
├── data/remote/
│   └── RemoteConfigFetcher.kt   用于下载二进制文档的 OkHttp 客户端
├── navigation/
│   └── AppNavigation.kt         4 个屏幕的 Compose 导航
├── ui/components/
│   ├── RemoteDocumentView.kt    RemoteComposePlayer 的 AndroidView 包装器
│   ├── RemoteScreenTopBar.kt    带刷新 + 最后更新时间的顶部栏
│   └── ErrorContent.kt          带重试的错误状态
├── ui/screen/
│   └── RemoteScreen.kt          屏幕可组合项（播放器 + 工具栏）
└── ui/theme/
    ├── Color.kt                 Material 3 调色板
    ├── Theme.kt                 支持动态颜色的浅色/深色主题
    └── Type.kt                  字体排版
```

## 屏幕

| 屏幕 | 配置 | 描述 |
|---|---|---|
| 首页 | `config.rc` | 带功能卡片的欢迎屏幕 |
| 详情 | `config_detail.rc` | 带点击操作的功能展示 |
| 估算 | `config_estimates.rc` | 带嵌套布局的估算卡片 |
| 估算详情 | `config_estimate_detail.rc` | 详细的估算明细 |

屏幕之间的导航由 Remote Compose 文档中的点击操作驱动（例如 `navigate:detail`、`navigate:estimates`）。

## 依赖

| 库 | 用途 |
|---|---|
| `remote-core` | Remote Compose 核心类型 |
| `remote-player-core` | 文档解析 |
| `remote-player-view` | `RemoteComposePlayer`（基于 View 的渲染器）|
| `okhttp` | 用于获取二进制文档的 HTTP 客户端 |
| `navigation-compose` | Compose 导航 |
| `lifecycle-viewmodel-compose` | ViewModel 集成 |

## 相关资源

- [remotecompose](https://github.com/armcha/remotecompose) —— Web 编辑器、JVM 转换器、JSON 配置和生成的二进制文档
- [Live Editor](https://armcha.github.io/remotecompose/) —— 支持实时 Compose/Wasm 预览的拖拽式 UI 构建器
- [Remote Compose 文档](https://github.com/androidx/androidx/tree/androidx-main/compose/remote/Documentation)
