# 私人TV Android TV

这是私人TV的 Android TV 原生客户端第一版。

## 当前完成

- 多源搜索
- 搜索进度展示
- 详情页
- MacCMS 播放地址解析
- Media3 / ExoPlayer 播放
- 播放进度记忆
- 继续观看
- 最近搜索
- 基础设置页
- Android TV Leanback 启动入口

## 构建

```bash
./gradlew assembleDebug
```

输出文件：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 配置

电视端启动时会尝试读取：

```text
https://tv.181.cx/app-config.json
```

读取失败时使用内置源配置，保证 App 仍然可以打开。
