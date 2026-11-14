# 灯带固件 OTA 升级说明

配套 App：v1.2.0 及以上  
升级服务：`FF12`  
- 写：`FF15`（Write No Response）
- 通知：`FF14`（Notify）

---

## 1. 前提

1. 灯带固件支持 FOTA；
2. 固件暴露 `FF12/FF14/FF15`；
3. 用官方 downloader 生成 `.fot` 文件；
4. 确认是普通 `.fot` 还是压缩 `.fot`（压缩文件头为 `POT`）；
5. 有对应的恢复/烧录方式。

App 只负责传输 `.fot`，不负责生成固件。

---

## 2. 生成固件升级包

使用中科蓝讯官方 downloader：

```text
.dcf 工程固件 → downloader → .fot
```

双备份与压缩升级的 SDK 配置见官方说明。App 端不需要区分，传输方式相同。

---

## 3. 上传 .fot 到 GitHub Release

在仓库：

```text
https://github.com/l2197096277-pixel/RGB_APP/releases
```

新建 Release，例如：

```text
Tag: fw-v1.2.3
Title: AuroraLight Firmware v1.2.3
```

上传：

```text
aurora-light-v1.2.3.fot
```

得到下载地址，例如：

```text
https://github.com/l2197096277-pixel/RGB_APP/releases/download/fw-v1.2.3/aurora-light-v1.2.3.fot
```

> Release 必须是公开的，否则 App 无法下载。

---

## 4. 计算 SHA-256

PowerShell：

```powershell
Get-FileHash ".\aurora-light-v1.2.3.fot" -Algorithm SHA256
```

复制输出的 Hash。

---

## 5. 编辑 fota.json

在 `gh-pages` 分支根目录编辑 `fota.json`：

```json
{
  "versionCode": 4097,
  "versionName": "1.0.1",
  "force": false,
  "fileUrl": "https://github.com/l2197096277-pixel/RGB_APP/releases/download/fw-v1.0.1/aurora-light-v1.0.1.fot",
  "sha256": "把上一步的SHA256填在这里",
  "size": 74752,
  "changelog": "修复了已知问题",
  "model": "aurora-light-v1"
}
```

字段说明：

| 字段 | 说明 |
|---|---|
| `versionCode` | 固件版本号，数字，必须大于当前设备版本 |
| `versionName` | 显示给用户的版本名 |
| `force` | 是否强制升级；`true` 时 App 向设备发送 `0xFFFF`，升级成功后仍记录 `versionCode` |
| `fileUrl` | `.fot` 的 HTTPS 下载地址 |
| `sha256` | `.fot` 的 SHA-256 |
| `size` | 文件字节数，可选 |
| `changelog` | App 固定显示“修复了已知问题”，此字段可保留 |
| `model` | 产品型号，防止刷错 |

提交：

```text
https://github.com/l2197096277-pixel/RGB_APP/blob/gh-pages/fota.json
```

---

## 6. 验证清单

浏览器打开：

```text
https://l2197096277-pixel.github.io/RGB_APP/fota.json
```

应该看到 JSON。

App：

1. 连接灯带；
2. 进入“我的”；
3. 看到“灯带固件”模块；
4. 有更新时显示红点；
5. 点“检查更新”；
6. 选择“是”；
7. 阅读注意事项，点“了解”；
8. 等待下载和升级；
9. 成功后重新连接。

---

## 7. 版本号示例

如果固件版本显示为：

```text
v1.2.3
```

需要设置对应的数值版本。App 会显示设备返回的 2 字节版本，例如：

```text
0x0102 → v0.0.1.2
```

具体映射以固件 SDK 的版本号定义为准。

---

## 8. 风险

- 错误的 `.fot` 可能导致灯带变砖；
- 升级中断可能无法启动；
- 压缩固件发送完成后还需要等待设备解压；
- 不要同时升级 App 和固件；
- 必须用备用设备先验证。

App 已实现：

- 检查更新；
- 红点提示；
- 是/否确认；
- 注意事项确认；
- SHA-256 校验；
- 进度条和百分比；
- 成功/失败提示；
- 升级期间屏蔽普通控制。
