#!/usr/bin/env python3
"""Fen 开发命令行：构建、安装、验收、投屏、worktree 与发布。

用法（仓库根目录）:
    python scripts/fen.py doctor
    python scripts/fen.py install [--build] [--serial ...] [--screenshot]
    python scripts/fen.py accept [--build] [--serial ...] [--keep-data]
    python scripts/fen.py mirror [--serial ...]
    python scripts/fen.py worktree new <name> [--base REF]
    python scripts/fen.py worktree remove <name>
    python scripts/fen.py release [--yes] [--serial ...]

同一套参数在 Windows / macOS / Linux 上都能用；只有环境探测不同。
"""

from __future__ import annotations

import argparse
import datetime
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
IS_WINDOWS = os.name == "nt"
DEBUG_APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
RELEASE_APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"

_ADB: Path | None = None
_SDK: Path | None = None


# --------------------------------------------------------------------------
# 环境探测
# --------------------------------------------------------------------------

def setup_console() -> None:
    """让中文在管道/重定向里也按 UTF-8 输出，避免 Windows 上乱码。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass


def find_sdk() -> Path | None:
    global _SDK
    if _SDK is not None:
        return _SDK

    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        value = os.environ.get(key)
        if value and Path(value).is_dir():
            _SDK = Path(value)
            return _SDK

    candidates: list[Path] = []
    if IS_WINDOWS:
        candidates.append(Path(r"C:\Android\sdk"))
        local = os.environ.get("LOCALAPPDATA")
        if local:
            candidates.append(Path(local) / "Android" / "Sdk")
    elif sys.platform == "darwin":
        candidates.append(Path.home() / "Library" / "Android" / "sdk")
    else:
        candidates.append(Path.home() / "Android" / "Sdk")
        candidates.append(Path("/opt/android-sdk"))

    for candidate in candidates:
        if candidate.is_dir():
            _SDK = candidate
            return _SDK

    adb = shutil.which("adb")
    if adb:
        _SDK = Path(adb).resolve().parent.parent
    return _SDK


def adb_path() -> Path:
    global _ADB
    if _ADB is not None:
        return _ADB

    sdk = find_sdk()
    if sdk:
        candidate = sdk / "platform-tools" / ("adb.exe" if IS_WINDOWS else "adb")
        if candidate.is_file():
            _ADB = candidate
            return _ADB

    found = shutil.which("adb")
    if found:
        _ADB = Path(found)
        return _ADB

    raise SystemExit(
        "找不到 adb。请安装 Android SDK platform-tools，"
        "或把 platform-tools 加入 PATH，或设置 ANDROID_HOME。"
    )


def jdk_candidates() -> list[Path]:
    """常见的 JDK / Android Studio JBR 位置，仅在 JAVA_HOME 缺失时使用。"""
    homes: list[Path] = []
    if IS_WINDOWS:
        for base, pattern in (
            (r"C:\Program Files\Microsoft", "jdk-*"),
            (r"C:\Program Files\Java", "jdk-*"),
            (r"C:\Program Files\Eclipse Adoptium", "jdk-*"),
            (r"C:\Program Files\Android\Android Studio", "jbr"),
        ):
            root = Path(base)
            if not root.is_dir():
                continue
            if pattern == "jbr":
                homes.append(root / "jbr")
            else:
                homes.extend(path for path in root.glob(pattern) if path.is_dir())
    elif sys.platform == "darwin":
        homes.append(Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home"))
        for path in Path("/Library/Java/JavaVirtualMachines").glob("*/"):
            home = path / "Contents" / "Home"
            if home.is_dir():
                homes.append(home)
    else:
        homes.extend([
            Path.home() / "android-studio" / "jbr",
            Path("/opt/android-studio/jbr"),
        ])
        for path in Path("/usr/lib/jvm").glob("*"):
            if path.is_dir():
                homes.append(path)
    return homes


def java_home() -> Path | None:
    java_name = "java.exe" if IS_WINDOWS else "java"
    configured = os.environ.get("JAVA_HOME")
    if configured and (Path(configured) / "bin" / java_name).is_file():
        return Path(configured)

    candidates = [
        home for home in jdk_candidates()
        if (home / "bin" / java_name).is_file()
    ]
    if not candidates:
        return None

    def version_key(home: Path) -> int:
        match = re.search(r"(\d+)", home.name)
        return int(match.group(1)) if match else 0

    candidates.sort(key=version_key, reverse=True)
    return candidates[0]


def java_exe() -> Path | None:
    home = java_home()
    if home:
        return home / "bin" / ("java.exe" if IS_WINDOWS else "java")
    found = shutil.which("java")
    return Path(found) if found else None


def java_info() -> tuple[Path | None, int | None, str]:
    exe = java_exe()
    if not exe:
        return None, None, ""
    proc = subprocess.run([str(exe), "-version"], capture_output=True)
    text = (proc.stderr or proc.stdout).decode("utf-8", "replace").strip()
    first = text.splitlines()[0] if text else ""
    match = re.search(r'version "(\d+)(?:\.(\d+))?', first)
    major = None
    if match:
        major = int(match.group(1))
        if major == 1 and match.group(2):
            major = int(match.group(2))
    return exe, major, first


def gradle_command() -> list[str]:
    wrapper = ROOT / ("gradlew.bat" if IS_WINDOWS else "gradlew")
    if not wrapper.is_file():
        raise SystemExit(f"找不到 Gradle Wrapper: {wrapper}")
    if not IS_WINDOWS and not os.access(wrapper, os.X_OK):
        # 某些 checkout 会丢掉执行位，直接交给 sh 也能跑。
        return ["sh", str(wrapper)]
    return [str(wrapper)]


def find_scrcpy() -> Path | None:
    found = shutil.which("scrcpy")
    if found:
        return Path(found)

    candidates: list[Path] = []
    if IS_WINDOWS:
        local = os.environ.get("LOCALAPPDATA")
        if local:
            packages = Path(local) / "Microsoft" / "WinGet" / "Packages"
            if packages.is_dir():
                candidates.extend(packages.glob("Genymobile.scrcpy*/**/scrcpy.exe"))
    elif sys.platform == "darwin":
        candidates.extend([Path("/opt/homebrew/bin/scrcpy"), Path("/usr/local/bin/scrcpy")])
    else:
        candidates.extend([Path("/usr/bin/scrcpy"), Path("/usr/local/bin/scrcpy")])

    for candidate in candidates:
        if candidate.is_file():
            return candidate
    return None


def scrcpy_hint() -> str:
    if IS_WINDOWS:
        return "winget install Genymobile.scrcpy"
    if sys.platform == "darwin":
        return "brew install scrcpy"
    return "sudo apt install scrcpy（或发行版对应的包管理器）"


def python_cmd() -> str:
    return "python" if IS_WINDOWS else "python3"


# --------------------------------------------------------------------------
# 命令与设备
# --------------------------------------------------------------------------

def build_env() -> dict[str, str]:
    env = dict(os.environ)
    sdk = find_sdk()
    if sdk and not (env.get("ANDROID_HOME") or env.get("ANDROID_SDK_ROOT")):
        env["ANDROID_HOME"] = str(sdk)

    home = java_home()
    if home:
        env["JAVA_HOME"] = str(home)
    else:
        env.pop("JAVA_HOME", None)
    return env


def build_apk(variant: str) -> None:
    print(f"== 构建 {variant} ==")
    command = gradle_command() + [f":app:assemble{variant}", "-q"]
    code = subprocess.run(command, cwd=ROOT, env=build_env()).returncode
    if code != 0:
        raise SystemExit("构建失败")


def run_adb(*args: object, serial: str | None = None, timeout: int = 120):
    command = [str(adb_path())]
    if serial:
        command += ["-s", serial]
    command += [str(arg) for arg in args]
    try:
        proc = subprocess.run(command, capture_output=True, timeout=timeout)
    except FileNotFoundError:
        raise SystemExit(f"无法执行 adb: {command[0]}")
    return (
        proc.returncode,
        proc.stdout.decode("utf-8", "replace"),
        proc.stderr.decode("utf-8", "replace"),
    )


def devices() -> list[tuple[str, str]]:
    _, out, _ = run_adb("devices")
    rows: list[tuple[str, str]] = []
    for line in out.splitlines()[1:]:
        parts = line.strip().split()
        if len(parts) >= 2:
            rows.append((parts[0], parts[1]))
    return rows


def is_emulator(serial: str) -> bool:
    return serial.startswith("emulator-")


def device_help() -> str:
    if IS_WINDOWS:
        return (
            "请检查：\n"
            "  1) 数据线支持传输（不是只能充电的线）\n"
            "  2) 手机「开发者选项 → USB 调试」已打开\n"
            "  3) 手机 USB 用途选为「传输文件」\n"
            "  4) Windows 已装好 ADB 驱动"
        )
    if sys.platform == "darwin":
        return (
            "请检查：\n"
            "  1) 数据线支持传输\n"
            "  2) 手机「开发者选项 → USB 调试」已打开\n"
            "  3) 手机上点「允许 USB 调试」"
        )
    return (
        "请检查：\n"
        "  1) 数据线支持传输\n"
        "  2) 手机「开发者选项 → USB 调试」已打开\n"
        "  3) 手机上点「允许 USB 调试」\n"
        "  4) 普通用户需要 udev 规则（android-sdk-platform-tools-common）"
    )


def pick_device(
    serial: str | None = None,
    wait: int = 90,
    physical_only: bool = False,
    unique: bool = False,
) -> str:
    deadline = time.time() + wait
    hinted = False

    while True:
        rows = devices()
        if physical_only:
            rows = [row for row in rows if not is_emulator(row[0])]

        if serial:
            match = [row for row in rows if row[0] == serial]
            if match and match[0][1] == "device":
                return serial
            if match and match[0][1] == "unauthorized" and not hinted:
                print("手机已连接，但还没授权。请在手机上点「允许 USB 调试」。")
                hinted = True
        else:
            ready = [row for row in rows if row[1] == "device"]
            if unique and len(ready) > 1:
                names = ", ".join(row[0] for row in ready)
                raise SystemExit(f"检测到多台设备（{names}），请用 --serial 指定")
            physical = [row for row in ready if not is_emulator(row[0])]
            if physical:
                return physical[0][0]
            if ready:
                return ready[0][0]
            unauthorized = [row for row in rows if row[1] == "unauthorized"]
            if unauthorized and not hinted:
                print("手机已连接，但还没授权。请在手机上点「允许 USB 调试」。")
                hinted = True

        if time.time() >= deadline:
            if not rows:
                raise SystemExit("没有检测到任何设备。\n" + device_help())
            raise SystemExit(f"等待设备超时，当前状态: {rows}")
        time.sleep(1.5)


def package_of(apk: Path) -> str:
    meta = apk.parent / "output-metadata.json"
    if meta.is_file():
        try:
            value = json.loads(meta.read_text(encoding="utf-8")).get("applicationId")
            if value:
                return value
        except Exception:
            pass
    return "app.fen"


def take_screenshot(serial: str, directory: Path, name: str = "launch.png") -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    local = directory / name
    remote = "/sdcard/_fen.png"
    run_adb("shell", "screencap", "-p", remote, serial=serial)
    run_adb("pull", remote, str(local), serial=serial)
    return local


def install_apk(
    apk: Path,
    serial: str | None = None,
    wait: int = 90,
    physical_only: bool = False,
    screenshot: bool = False,
    launch: bool = True,
) -> None:
    apk = apk if apk.is_absolute() else (ROOT / apk)
    if not apk.is_file():
        raise SystemExit(f"找不到 APK: {apk}\n先构建：{python_cmd()} scripts/fen.py install --build")

    package = package_of(apk)
    target = pick_device(serial, wait=wait, physical_only=physical_only)
    kind = "模拟器" if is_emulator(target) else "真机"
    _, model, _ = run_adb("shell", "getprop", "ro.product.model", serial=target)
    _, release, _ = run_adb("shell", "getprop", "ro.build.version.release", serial=target)
    print(f"== 目标: {target}（{kind} / {model.strip()} / Android {release.strip()}）==")
    print(f"== 包名: {package} ==")

    size_mb = apk.stat().st_size / 1024 / 1024
    print(f"== 安装 {apk.name}（{size_mb:.1f} MB）==")
    code, out, err = run_adb("install", "-r", "-t", str(apk), serial=target, timeout=600)
    text = (out + err).strip()
    if code != 0 or "Success" not in out:
        raise SystemExit(
            "安装失败:\n" + text +
            "\n\n小米/HyperOS 常见原因：手机上没有点「继续安装」，或开启了「USB 安装」限制。"
        )
    print("安装成功")

    if launch:
        print("== 启动 ==")
        run_adb("shell", "am", "force-stop", package, serial=target)
        run_adb(
            "shell", "monkey", "-p", package,
            "-c", "android.intent.category.LAUNCHER", "1",
            serial=target,
        )
        time.sleep(3)
        line = ""
        deadline = time.time() + 10
        while time.time() < deadline:
            _, focus, _ = run_adb("shell", "dumpsys", "activity", "activities", serial=target)
            line = next(
                (l.strip() for l in focus.splitlines()
                 if "topResumedActivity" in l or "mResumedActivity" in l),
                "",
            )
            if package in line:
                break
            time.sleep(1)
        if package in line:
            print(f"已在前台: {line}")
        else:
            print("警告: 没有确认到前台 Activity，请在手机上查看是否弹出权限/安装提示。")

    if screenshot:
        stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
        path = take_screenshot(target, ROOT / "artifacts" / f"install-{stamp}")
        print(f"截图: {os.path.relpath(path, ROOT)}")


# --------------------------------------------------------------------------
# 验收
# --------------------------------------------------------------------------

RESULTS: list[tuple[str, bool, str]] = []


def check(name: str, fn) -> None:
    try:
        detail = fn() or ""
        RESULTS.append((name, True, detail))
        print(f"[PASS] {name} {detail}".rstrip())
    except Exception as exc:  # noqa: BLE001
        RESULTS.append((name, False, str(exc)))
        print(f"[FAIL] {name}: {exc}")


class Session:
    def __init__(self, serial: str, package: str, out_dir: Path) -> None:
        self.serial = serial
        self.package = package
        self.out_dir = out_dir
        self.dump_index = 0

    def shell(self, *args: object, timeout: int = 120):
        return run_adb("shell", *args, serial=self.serial, timeout=timeout)

    def dump(self) -> list[dict]:
        self.dump_index += 1
        remote = "/sdcard/ui.xml"
        local = self.out_dir / f"ui-{self.dump_index:02d}.xml"
        last = ""
        for _ in range(5):
            _, out, err = self.shell("uiautomator", "dump", remote)
            if "dumped" in out or "dumped" in err:
                break
            last = out + err
            time.sleep(0.5)
        else:
            raise RuntimeError(f"uiautomator dump 失败: {last.strip()}")

        run_adb("pull", remote, str(local), serial=self.serial)
        text = local.read_text(encoding="utf-8", errors="replace")
        nodes: list[dict] = []
        for match in re.finditer(r"<node[^>]*>", text):
            tag = match.group(0)
            text_match = re.search(r'text="([^"]*)"', tag)
            desc_match = re.search(r'content-desc="([^"]*)"', tag)
            bounds_match = re.search(
                r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', tag
            )
            if not bounds_match:
                continue
            x1, y1, x2, y2 = (int(value) for value in bounds_match.groups())
            label = (text_match.group(1) if text_match else "") or (
                desc_match.group(1) if desc_match else ""
            )
            nodes.append({
                "label": label,
                "center": ((x1 + x2) // 2, (y1 + y2) // 2),
                "box": (x1, y1, x2, y2),
            })
        return nodes

    def find(self, text: str, exact: bool = True) -> list[dict]:
        nodes = self.dump()
        if exact:
            return [node for node in nodes if node["label"].strip() == text]
        return [node for node in nodes if text in node["label"]]

    def tap(self, text: str, exact: bool = True, index: int = 0) -> tuple[int, int]:
        hits = self.find(text, exact=exact)
        if not hits:
            raise RuntimeError(f"界面上找不到文本「{text}」")
        x, y = hits[index]["center"]
        self.shell("input", "tap", str(x), str(y))
        return x, y

    def wait_text(self, text: str, timeout: float = 8.0, exact: bool = True) -> bool:
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                if self.find(text, exact=exact):
                    return True
            except RuntimeError:
                pass
            time.sleep(0.5)
        raise RuntimeError(f"等待文本「{text}」超时")

    def shot(self, name: str) -> Path:
        remote = "/sdcard/_acceptance.png"
        local = self.out_dir / name
        self.shell("screencap", "-p", remote)
        run_adb("pull", remote, str(local), serial=self.serial)
        return local

    def start_app(self) -> None:
        self.shell("am", "force-stop", self.package)
        time.sleep(0.5)
        code, out, err = self.shell(
            "monkey", "-p", self.package,
            "-c", "android.intent.category.LAUNCHER", "1",
        )
        if code != 0:
            raise RuntimeError(f"启动失败: {out}{err}")


def cmd_accept(args: argparse.Namespace) -> int:
    if args.build:
        build_apk("Debug")

    apk = Path(args.apk)
    apk = apk if apk.is_absolute() else (ROOT / apk)
    if not apk.is_file():
        raise SystemExit(f"找不到 APK: {apk}，先构建或加 --build")

    package = package_of(apk)
    print(f"== 包名 {package} ==")
    serial = pick_device(args.serial, wait=30, unique=True)

    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    out_dir = Path(args.out) if args.out else ROOT / "artifacts" / f"acceptance-{stamp}"
    out_dir.mkdir(parents=True, exist_ok=True)
    session = Session(serial, package, out_dir)
    print(f"== 设备 {serial} ==")

    def install() -> str:
        code, out, err = run_adb("install", "-r", "-t", str(apk), serial=serial, timeout=300)
        if code != 0 or "Success" not in out:
            raise RuntimeError((out + err).strip()[-300:])
        return apk.name

    check("安装 APK", install)

    if not args.keep_data:
        check(
            "清空应用数据",
            lambda: (session.shell("pm", "clear", package), "ok")[1],
        )

    check("冷启动", session.start_app)

    def home_ready() -> str:
        session.wait_text("明细", timeout=25)
        session.wait_text("记一笔", timeout=20)
        session.shot("01-home-empty.png")
        return ""

    check("首页加载 + 空态", home_ready)

    def open_add() -> str:
        session.tap("记一笔")
        session.wait_text("完成")
        session.wait_text("点击填写备注", exact=False)
        session.shot("02-add.png")
        return ""

    check("打开记账页", open_add)

    def enter_amount() -> str:
        for key in ("1", "2", "3"):
            session.tap(key)
            time.sleep(0.2)
        session.shot("03-add-filled.png")
        return ""

    check("输入金额 123", enter_amount)

    def save() -> str:
        session.tap("完成")
        session.wait_text("-123.00", timeout=20)
        session.wait_text("餐饮", timeout=5)
        session.shot("04-home-saved.png")
        return ""

    check("保存后回到明细并出现记录", save)

    def edit_record() -> str:
        session.tap("餐饮")
        session.wait_text("123.00", timeout=20)
        session.shot("05-edit-open.png")
        for key in ("2", "0", "0"):
            session.tap(key)
            time.sleep(0.2)
        session.tap("完成")
        session.wait_text("-200.00", timeout=20)
        session.shot("06-edit-saved.png")
        return ""

    check("点开记录并改金额", edit_record)

    def stats() -> str:
        session.tap("统计")
        session.wait_text("总览", timeout=10)
        session.wait_text("分类占比", timeout=10)
        session.wait_text("200.00", timeout=5)
        session.shot("07-stats.png")
        return ""

    check("统计页总览与分类占比", stats)

    def settings() -> str:
        session.tap("设置")
        session.wait_text("主题", timeout=10)
        session.wait_text("关于", timeout=5)
        session.shot("08-settings.png")
        return ""

    check("设置页结构", settings)

    def back_home() -> str:
        session.tap("返回")
        session.wait_text("明细", timeout=10)
        return ""

    check("设置返回明细", back_home)

    passed = sum(1 for result in RESULTS if result[1])
    total = len(RESULTS)
    summary = out_dir / "summary.txt"
    with open(summary, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(f"Fen 验收 {stamp}\n设备: {serial}\nAPK: {apk}\n\n")
        for name, ok, detail in RESULTS:
            handle.write(("PASS " if ok else "FAIL ") + name + (f"  {detail}" if detail else "") + "\n")
        handle.write(f"\n{passed}/{total} 通过\n")

    print()
    print(f"结果: {passed}/{total} 通过")
    print(f"截图与摘要: {os.path.relpath(out_dir, ROOT)}")
    return 0 if passed == total else 1


# --------------------------------------------------------------------------
# 投屏
# --------------------------------------------------------------------------

def cmd_mirror(args: argparse.Namespace) -> int:
    serial = args.serial or pick_device(wait=10)
    scrcpy = find_scrcpy()
    if not scrcpy:
        raise SystemExit(f"找不到 scrcpy。安装方式：{scrcpy_hint()}")

    log = Path(tempfile.gettempdir()) / "fen-mirror.log"
    err_log = Path(str(log) + ".err")
    for path in (log, err_log):
        try:
            path.unlink()
        except FileNotFoundError:
            pass

    command = [
        str(scrcpy),
        "-s", serial,
        f"--window-title=Fen-{serial}",
        "--max-size=1024",
        "--stay-awake",
        "--no-audio",
    ]

    with open(log, "wb") as out, open(err_log, "wb") as err:
        kwargs = {}
        if IS_WINDOWS:
            kwargs["creationflags"] = (
                subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP
            )
        else:
            kwargs["start_new_session"] = True
        proc = subprocess.Popen(command, stdout=out, stderr=err, **kwargs)

    time.sleep(2)
    if proc.poll() is not None:
        print("scrcpy 已退出。日志：")
        print(log.read_text(encoding="utf-8", errors="replace")[-2000:])
        print(err_log.read_text(encoding="utf-8", errors="replace")[-2000:])
        return 1

    print(f"正在投屏 {serial}（pid {proc.pid}）。关闭 scrcpy 窗口即可断开。")
    print(f"日志: {log}")
    return 0


# --------------------------------------------------------------------------
# Worktree
# --------------------------------------------------------------------------

def slug(name: str) -> str:
    safe = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    if not safe:
        raise SystemExit(f"无效的 worktree 名称: {name}")
    return safe


def git(*args: str) -> tuple[int, str, str]:
    proc = subprocess.run(
        ["git", "-C", str(ROOT), *args],
        capture_output=True,
        encoding="utf-8",
        errors="replace",
    )
    return proc.returncode, (proc.stdout or "").strip(), (proc.stderr or "").strip()


def cmd_worktree_new(args: argparse.Namespace) -> int:
    safe = slug(args.name)
    branch = f"feat/{safe}"
    target = ROOT.parent / f"fen-{safe}"
    if target.exists():
        raise SystemExit(f"目标已存在: {target}")

    subprocess.run(
        ["git", "-C", str(ROOT), "fetch", "--all", "--prune"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )

    base = args.base
    if not base:
        code, _, _ = git("rev-parse", "--verify", "--quiet", "origin/master")
        base = "origin/master" if code == 0 else "master"

    code, out, err = git("worktree", "add", str(target), "-b", branch, base)
    if code != 0:
        raise SystemExit(err or out or "git worktree add 失败")

    dev_id = re.sub(r"[^a-z0-9]+", "_", branch).strip("_")[:24] or "dev"
    print()
    print(f"Worktree : {target}")
    print(f"Branch   : {branch}  (from {base})")
    print(f"Dev app  : app.fen.dev.{dev_id}")
    print(f"Name     : Fen·{dev_id}")
    print()
    print("Next:")
    print(f'  cd "{target}"')
    print(f"  {python_cmd()} scripts/fen.py install")
    print()
    print("完成并通过手机验收后：")
    print(f"  git push -u origin {branch}")
    print("  在 GitHub 开 PR，squash merge 并删除远端分支")
    print(f"  {python_cmd()} scripts/fen.py worktree remove {safe}")
    print("  （如果远端分支还没删除，清理时加 --force）")
    return 0


def prune_remotes() -> None:
    """best effort 同步远端分支状态，用于识别 squash merge 后被删掉的分支。"""
    try:
        subprocess.run(
            ["git", "-C", str(ROOT), "fetch", "--all", "--prune"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=30,
            env={**os.environ, "GIT_TERMINAL_PROMPT": "0"},
        )
    except Exception:
        pass


def branch_merged(branch: str) -> bool:
    code, out, _ = git("branch", "--merged", "master", "--format=%(refname:short)")
    return code == 0 and branch in out.splitlines()


def upstream_gone(branch: str) -> bool:
    code, out, _ = git("for-each-ref", "--format=%(upstream:track)", f"refs/heads/{branch}")
    return code == 0 and "gone" in out


def cmd_worktree_remove(args: argparse.Namespace) -> int:
    safe = slug(args.name)
    branch = f"feat/{safe}"
    target = ROOT.parent / f"fen-{safe}"
    dev_id = re.sub(r"[^a-z0-9]+", "_", branch).strip("_")[:24] or "dev"
    package = f"app.fen.dev.{dev_id}"

    prune_remotes()
    merged = branch_merged(branch)
    gone = upstream_gone(branch)
    if not (merged or gone or args.force):
        raise SystemExit(
            f"分支 {branch} 还没有合并进 master，已拒绝清理。\n"
            "如果已经通过 squash merge 合并，请加 --force；"
            "远端分支删除后重新 fetch 也会自动识别。"
        )

    if target.exists():
        code, out, err = git("worktree", "remove", str(target), "--force")
        if code != 0:
            raise SystemExit(err or out or "git worktree remove 失败")

    delete_flag = "-d" if merged else "-D"
    code, out, err = git("branch", delete_flag, branch)
    if code != 0:
        raise SystemExit(err or out or "删除分支失败")

    try:
        for serial, state in devices():
            if state != "device":
                continue
            _, listing, _ = run_adb("shell", "pm", "list", "packages", package, serial=serial)
            if package not in listing:
                print(f"{serial}: 未安装 {package}，跳过")
                continue
            _, uninstall_out, uninstall_err = run_adb("uninstall", package, serial=serial)
            if "Success" in uninstall_out:
                print(f"已从 {serial} 卸载 {package}")
            else:
                print(f"{serial}: 卸载 {package} 失败（已忽略）: "
                      f"{(uninstall_out + uninstall_err).strip()}")
    except SystemExit:
        print("未找到 adb，跳过卸载开发版。")

    print()
    print(f"已清理: {target}")
    print(f"已删除分支: {branch}")
    print(f"已处理开发版: {package}")
    return 0


# --------------------------------------------------------------------------
# 发布
# --------------------------------------------------------------------------

def cmd_release(args: argparse.Namespace) -> int:
    if not args.yes:
        print("即将构建并安装正式版 app.fen（会覆盖手机上现有的正式版）。")
        answer = input("输入 release 继续: ").strip()
        if answer != "release":
            print("已取消。")
            return 1

    build_apk("Release")
    install_apk(
        RELEASE_APK,
        serial=args.serial,
        wait=90,
        screenshot=True,
        launch=True,
    )
    return 0


# --------------------------------------------------------------------------
# 环境自检
# --------------------------------------------------------------------------

def cmd_doctor(args: argparse.Namespace) -> int:
    print("Fen doctor")
    print(f"OS      : {platform.platform()}")
    print(f"Python  : {platform.python_version()}  ({sys.executable})")

    sdk = find_sdk()
    print(f"SDK     : {sdk if sdk else '未找到（请设置 ANDROID_HOME）'}")

    adb_ok = True
    try:
        adb = adb_path()
        print(f"adb     : {adb}")
        _, out, err = run_adb("version")
        first = (out or err).strip().splitlines()
        if first:
            print(f"          {first[0]}")
    except SystemExit as exc:
        adb_ok = False
        print(f"adb     : 不可用（{exc}）")

    exe, major, first = java_info()
    if exe:
        print(f"Java    : {exe}")
        if first:
            print(f"          {first}")
        if major is not None and major < 17:
            print("          ! 构建需要 JDK 17 或更高版本")
    else:
        print("Java    : 未找到（请设置 JAVA_HOME，或把 java 加入 PATH）")

    wrapper = ROOT / ("gradlew.bat" if IS_WINDOWS else "gradlew")
    if wrapper.is_file():
        mode = "可执行" if (IS_WINDOWS or os.access(wrapper, os.X_OK)) else "缺少执行权限"
        print(f"Gradle  : {wrapper}  ({mode})")
    else:
        print(f"Gradle  : 找不到 {wrapper}")

    scrcpy = find_scrcpy()
    if scrcpy:
        print(f"scrcpy  : {scrcpy}")
    else:
        print(f"scrcpy  : 未找到（投屏需要：{scrcpy_hint()}）")

    if adb_ok:
        rows = devices()
        print("设备    :")
        if rows:
            for serial, state in rows:
                print(f"          {serial}  {state}")
        else:
            print("          （无）")

    print()
    if not adb_ok:
        print("结论    : adb 不可用，安装/验收/投屏都无法执行。")
        return 1
    print("结论    : adb 可用，可以执行安装与验收。")
    return 0


# --------------------------------------------------------------------------
# 命令行
# --------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="fen.py",
        description="Fen 开发命令行（Windows / macOS / Linux 通用）",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    doctor = sub.add_parser("doctor", help="打印 SDK / adb / Java / Gradle / scrcpy / 设备状态")
    doctor.set_defaults(handler=cmd_doctor)

    install = sub.add_parser("install", help="安装 debug APK 并启动")
    install.add_argument("--build", action="store_true", help="先执行 assembleDebug")
    install.add_argument("--serial")
    install.add_argument("--apk", default=str(DEBUG_APK))
    install.add_argument("--wait", type=int, default=90, help="等待设备上线秒数")
    install.add_argument("--physical", action="store_true", help="只等真机，忽略模拟器")
    install.add_argument("--screenshot", action="store_true")
    install.add_argument("--no-launch", action="store_true")
    install.set_defaults(handler=lambda args: run_install(args))

    accept = sub.add_parser("accept", help="安装并走一遍核心流程")
    accept.add_argument("--build", action="store_true", help="先执行 assembleDebug")
    accept.add_argument("--serial")
    accept.add_argument("--apk", default=str(DEBUG_APK))
    accept.add_argument("--keep-data", action="store_true", help="保留已有数据")
    accept.add_argument("--out", default=None)
    accept.set_defaults(handler=cmd_accept)

    mirror = sub.add_parser("mirror", help="用 scrcpy 投屏")
    mirror.add_argument("--serial")
    mirror.set_defaults(handler=cmd_mirror)

    worktree = sub.add_parser("worktree", help="管理并行开发 worktree")
    worktree_sub = worktree.add_subparsers(dest="action", required=True)
    worktree_new = worktree_sub.add_parser("new", help="创建 worktree")
    worktree_new.add_argument("name")
    worktree_new.add_argument("--base", default="", help="基础分支，默认 origin/master")
    worktree_new.set_defaults(handler=cmd_worktree_new)
    worktree_remove = worktree_sub.add_parser("remove", aliases=["rm"], help="清理已合并的 worktree")
    worktree_remove.add_argument("name")
    worktree_remove.add_argument(
        "--force",
        action="store_true",
        help="分支未合并时也强制清理（squash merge 后使用）",
    )
    worktree_remove.set_defaults(handler=cmd_worktree_remove)

    release = sub.add_parser("release", help="构建并安装正式版（app.fen）")
    release.add_argument("--yes", action="store_true", help="跳过确认")
    release.add_argument("--serial")
    release.set_defaults(handler=cmd_release)

    return parser


def run_install(args: argparse.Namespace) -> int:
    if args.build:
        build_apk("Debug")
    install_apk(
        Path(args.apk),
        serial=args.serial,
        wait=args.wait,
        physical_only=args.physical,
        screenshot=args.screenshot,
        launch=not args.no_launch,
    )
    return 0


def main() -> int:
    setup_console()
    args = build_parser().parse_args()
    return args.handler(args)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n已取消。")
        sys.exit(130)
