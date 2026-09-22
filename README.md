# 私人TV Android TV

私人TV 的 Android TV 原生客户端（Kotlin / Jetpack Compose / Media3 + ijkplayer）。

当前版本：**v0.3.0 (versionCode 8)** —— 本版本的审查结论与全部改动见 [`docs/审查报告与改动说明.md`](docs/审查报告与改动说明.md)。

## 功能

- 多源并发搜索（并发数、超时来自远程配置），结果按匹配度稳定排序、跨源去重
- 搜索状态跨页面保留：进详情再返回不会丢结果
- 首拼键盘（字母 + 数字）+ 搜索联想 + 最近搜索
- 详情页：多线路（直链线路自动排前）、选集网格、续播按钮、上次观看高亮
- 播放器：ExoPlayer / IJK / 系统播放器三内核，播放失败按 Exo → IJK → 系统 自动降级并续播
- 播放器内：选集、倍速、换源（保留集数和进度）、遥控器快捷键
- 播放进度记忆：退出 / 切集 / 切后台 / 换源 时即时保存；首页"继续观看"一键续播
- 豆瓣热门推荐（走 `tv.181.cx/proxy/`）
- 设置页：默认内核、按源启用/关闭、刷新远程配置、清空记录、版本信息
- Android TV Leanback 启动入口，10-foot UI 焦点样式

## 遥控器快捷键（播放中）

| 按键 | 控制条隐藏时 | 控制条显示时 |
|------|-------------|-------------|
| 确定 / 播放暂停键 | 播放 / 暂停 | 触发当前焦点按钮 |
| 左 / 右 | 快退 / 快进（长按连续） | 移动焦点 |
| 上 / 下 / 菜单 | 呼出控制条 | 菜单键隐藏控制条 |
| 返回 | 退出播放 | 先隐藏控制条 / 关闭换源面板 |
| 频道 +/- | 下一集 / 上一集 | 同左 |

## 构建

需要 JDK 17 与 Android SDK（compileSdk 35 / build-tools 35.0.0）。

```bash
./gradlew assembleDebug      # 调试包（包名 cx.n181.stv.debug，可与正式包共存）
./gradlew assembleRelease    # 正式包
```

输出文件：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

### 正式签名

`keystore/keystore.properties` + `keystore/stv-release.jks` 存在时，release 使用正式签名；不存在时自动回退 debug 签名（方便 CI / 临时打包）。
签名文件不入库，**请务必自行备份**：换了签名后新包无法覆盖安装旧包。详见 `keystore/README.md`。

## 安装到电视

1. 电视 / 盒子开启"允许安装未知来源应用"（设置 → 安全 / 应用）。
2. 任选一种方式把 APK 装上去：
   - U 盘拷贝 APK，在电视上用文件管理器打开安装；
   - 电脑与电视同一局域网，电视开启 ADB 调试后：`adb connect <电视IP>:5555 && adb install -r app-release.apk`；
   - 用"当贝市场 / 悟空遥控"之类工具远程推送安装。
3. 在 Leanback 桌面找到"私人TV"图标启动。

## 配置

电视端启动时会尝试读取：

```text
https://tv.181.cx/app-config.json
```

读取顺序：内存缓存（30 分钟）→ 远程 → 上次成功的磁盘缓存 → 内置源配置。任何一步失败都不会阻塞界面。

远程配置支持的字段：`appName`、`announcement`、`proxyUrl`（豆瓣代理，默认 `https://tv.181.cx/proxy/`）、`search.concurrency / timeoutMs`、`player.*`、`sources[]`（`key / name / api / enabled / order / timeoutMs / headers`）。
