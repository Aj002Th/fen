---
name: Fen Worktree Workflow
description: 在 fen 仓库并行开发功能时使用：从最新 master 开 worktree、装独立开发版、手机人工验收后才合并、合并后更新 master 开发版并清理；正式版只在明确发版时更新
---

# Fen 并行开发流程

## 铁律（不可跳过）

1. 新功能必须基于**最新 master** 拉 `feat/<name>` 分支，worktree 建在 `fen` 的**并排目录** `fen-<name>`。
2. 每个功能一个独立开发版 `app.fen.dev.feat_<name>`（图标 `Fen·feat-<name>`），可与正式版同时安装。
3. **没有用户在手机上人工验收并明确说“可以合并”，禁止合并回 master。** 不要在“本地测试通过”后自行合并。
4. 合并成功后必须清理：worktree、已合并分支、手机上对应的开发版。
5. 合并后手机上要更新的是 **master 开发版** `app.fen.dev.master`（图标 `Fen·master`），用它复验合并结果。
6. **正式版 `app.fen`（黑色 `Fen`）是用户正在使用的版本，只在用户明确要求发版时才更新。开发、合并、清理过程中都禁止跑 `release-prod.ps1`。**

## 步骤

### 1. 开工作区（在 master 仓库根目录执行）

```powershell
.\scripts\new-worktree.ps1 <name>
cd ..\fen-<name>
```

- 自动 `fetch`，从 `origin/master`（没有远程则 `master`）建分支
- 打印开发版包名与即将出现的图标名

### 2. 开发 + 装开发版

```powershell
.\gradlew.bat :app:assembleDebug
python scripts\install.py
```

- `install.py` 自动读取 `output-metadata.json` 里的包名，不会碰正式版
- HyperOS 可能拦截 USB 安装：保持手机解锁、点允许

### 3. 造验收数据（可选）

```powershell
adb shell am start -a android.intent.action.VIEW -d "fen://debug/seed?count=300" -p app.fen.dev.feat_<name>
adb shell am start -a android.intent.action.VIEW -d "fen://debug/clear" -p app.fen.dev.feat_<name>
```

先启动 App 再发深链，否则可能落不到库里。

### 4. 交付验收

告诉用户：

- 要打开哪个图标（`Fen·feat-<name>`，不是黑色 `Fen`）
- 具体验证步骤与预期
- 等待用户明确确认

**此阶段不要合并。**

### 5. 合并（仅在用户确认后）

在 master 仓库根目录（`fen`）执行：

```powershell
git merge --no-ff feat/<name>
```

合并后**只更新 master 开发版**供复验，不要动正式版：

```powershell
.\gradlew.bat :app:assembleDebug
python scripts\install.py
```

- 得到的包名是 `app.fen.dev.master`（图标 `Fen·master`），和正式版共存、数据隔离
- 告诉用户合并已完成，请打开 `Fen·master` 复验
- 用户没有明确说“发版”之前，**不要**运行 `release-prod.ps1`

### 6. 清理

```powershell
.\scripts\remove-worktree.ps1 <name>
```

- 未合并的分支会被拒绝清理
- 会同时卸载手机/模拟器上的 `app.fen.dev.feat_<name>`
- 不会碰 `app.fen` 和 `app.fen.dev.master`

### 7. 发版（仅在用户明确要求时）

这一步才会更新用户正在用的黑色 `Fen`：

```powershell
# 1) 先升 versionCode / versionName，提交
# 2) 构建 release 并覆盖安装 app.fen
.\scripts\release-prod.ps1
```

- 脚本会再次要求输入确认，防止误操作
- 发版前确认 master 上的功能已验收，发版后通知用户可以更新到新版本

## 注意事项

- 验收脚本 `uiautomator` 卡在 `UiAutomationService already registered` 时，重启模拟器；不要反复重试。
- 版本隔离：正式版 `app.fen`，开发版 `app.fen.dev.<分支>`，数据互不影响。
- **正式版 `app.fen` 只在发版时更新。** 日常开发、合并、清理都不安装 release 包。
- 临时发现的问题在没有用户同意前不要顺手改进 master。
