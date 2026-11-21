---
name: Fen Worktree Workflow
description: 在 fen 仓库并行开发功能时使用：从最新 master 开 worktree、装独立开发版、手机人工验收后才合并、合并后清理并在手机上更新正式版
---

# Fen 并行开发流程

## 铁律（不可跳过）

1. 新功能必须基于**最新 master** 拉 `feat/<name>` 分支，worktree 建在 `fen` 的**并排目录** `fen-<name>`。
2. 每个功能一个独立开发版 `app.fen.dev.feat_<name>`（图标 `Fen·feat-<name>`），可与正式版同时安装。
3. **没有用户在手机上人工验收并明确说“可以合并”，禁止合并回 master。** 不要在“本地测试通过”后自行合并。
4. 合并成功后必须清理：worktree、已合并分支、手机上对应的开发版。
5. 合并并更新 master 后，必须把正式版 `app.fen` 更新到手机。

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

```powershell
git merge --no-ff feat/<name>
```

然后更新版本号，再更新正式版：

```powershell
.\scripts\release-prod.ps1
```

### 6. 清理

```powershell
.\scripts\remove-worktree.ps1 <name>
```

- 未合并的分支会被拒绝清理
- 会同时卸载手机/模拟器上的 `app.fen.dev.feat_<name>`

## 注意事项

- 验收脚本 `uiautomator` 卡在 `UiAutomationService already registered` 时，重启模拟器；不要反复重试。
- 版本隔离：正式版 `app.fen`，开发版 `app.fen.dev.<分支>`，数据互不影响。
- 临时发现的问题在没有用户同意前不要顺手改进 master。
