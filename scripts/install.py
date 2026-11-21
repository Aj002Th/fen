"""把 debug APK 装到已连接的手机或模拟器上，并启动验证。

用法（仓库根目录）:
    python scripts/install.py                 # 自动挑设备（优先真机）
    python scripts/install.py --build         # 先构建再安装
    python scripts/install.py --serial 12345678
    python scripts/install.py --wait 180      # 最多等 3 分钟设备上线
    python scripts/install.py --screenshot    # 装完截一张图

说明:
    - 真机和模拟器同时在线时优先选真机；
    - 手机未授权（unauthorized）时会提示你在手机上点「允许」，并继续等待；
    - 不会清空 App 数据（那是 scripts/acceptance.py 的行为）。
"""

import argparse
import datetime
import os
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACKAGE = "app.fen"
APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk")


def package_of(apk_path):
    """从 AGP 生成的 output-metadata.json 读取 applicationId（开发版带分支后缀）。"""
    meta = os.path.join(os.path.dirname(apk_path), "output-metadata.json")
    if os.path.exists(meta):
        try:
            import json
            with open(meta, encoding="utf-8") as handle:
                value = json.load(handle).get("applicationId")
            if value:
                return value
        except Exception:
            pass
    return "app.fen"


def find_sdk():
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        value = os.environ.get(key)
        if value and os.path.isdir(value):
            return value
    for candidate in (r"C:\Android\sdk", os.path.expanduser(r"~\AppData\Local\Android\sdk")):
        if os.path.isdir(candidate):
            return candidate
    raise SystemExit("找不到 Android SDK，请设置 ANDROID_HOME")


SDK = find_sdk()
ADB = os.path.join(SDK, "platform-tools", "adb.exe")


def adb(*args, timeout=120):
    proc = subprocess.run([ADB] + list(args), capture_output=True, timeout=timeout)
    return (
        proc.returncode,
        proc.stdout.decode("utf-8", "replace"),
        proc.stderr.decode("utf-8", "replace"),
    )


def devices():
    _, out, _ = adb("devices")
    result = []
    for line in out.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) >= 2:
            result.append((parts[0], parts[1]))
    return result


def is_emulator(serial):
    return serial.startswith("emulator-")


def wait_for_device(serial, timeout, physical_only=False):
    """返回 (serial, state)。state 可能是 device / unauthorized / offline。"""
    deadline = time.time() + timeout
    hinted = False
    while True:
        rows = devices()
        if physical_only:
            rows = [r for r in rows if not is_emulator(r[0])]
        if serial:
            match = [r for r in rows if r[0] == serial]
            if match and match[0][1] == "device":
                return match[0]
            if match and match[0][1] == "unauthorized" and not hinted:
                print("手机已连接，但还没授权。请在手机上点「允许 USB 调试」（建议勾选“一律允许”）。")
                hinted = True
        else:
            ready = [r for r in rows if r[1] == "device"]
            physical = [r for r in ready if not is_emulator(r[0])]
            if physical:
                return physical[0]
            if ready:
                return ready[0]
            unauthorized = [r for r in rows if r[1] == "unauthorized"]
            if unauthorized and not hinted:
                print("手机已连接，但还没授权。请在手机上点「允许 USB 调试」（建议勾选“一律允许”）。")
                hinted = True

        if time.time() >= deadline:
            if not rows:
                raise SystemExit(
                    "没有检测到任何设备。请检查：\n"
                    "  1) 数据线是否支持传输（不是只能充电的线）\n"
                    "  2) 手机「开发者选项 → USB 调试」是否打开\n"
                    "  3) 手机 USB 用途是否选为「传输文件」\n"
                    "  4) Windows 是否装好 ADB 驱动（设备管理器里是否出现 ADB Interface）"
                )
            raise SystemExit(f"等待设备超时，当前状态: {rows}")
        time.sleep(1.5)


def build():
    env = dict(os.environ)
    env["ANDROID_HOME"] = env.get("ANDROID_HOME") or SDK
    if not env.get("JAVA_HOME") and os.path.isdir(r"C:\Program Files\Microsoft\jdk-21.0.13.102-hotspot"):
        env["JAVA_HOME"] = r"C:\Program Files\Microsoft\jdk-21.0.13.102-hotspot"
    gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
    print("== 构建 ==")
    code = subprocess.run([gradlew, ":app:assembleDebug", "-q"], cwd=ROOT, env=env).returncode
    if code != 0:
        raise SystemExit("构建失败")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial")
    parser.add_argument("--apk", default=APK)
    parser.add_argument("--build", action="store_true")
    parser.add_argument("--wait", type=int, default=90, help="等待设备上线秒数")
    parser.add_argument("--physical", action="store_true", help="只等真机，忽略模拟器")
    parser.add_argument("--screenshot", action="store_true")
    parser.add_argument("--no-launch", action="store_true")
    args = parser.parse_args()

    if args.build:
        build()

    if not os.path.exists(args.apk):
        raise SystemExit(f"找不到 APK: {args.apk}\n先执行 python scripts/install.py --build")

    global PACKAGE
    PACKAGE = package_of(args.apk)

    serial, _ = wait_for_device(args.serial, args.wait, physical_only=args.physical)
    kind = "模拟器" if is_emulator(serial) else "真机"
    _, model, _ = adb("-s", serial, "shell", "getprop", "ro.product.model")
    _, android, _ = adb("-s", serial, "shell", "getprop", "ro.build.version.release")
    print(f"== 目标: {serial}（{kind} / {model.strip()} / Android {android.strip()}）==")
    print(f"== 包名: {PACKAGE} ==")

    size_mb = os.path.getsize(args.apk) / 1024 / 1024
    print(f"== 安装 {os.path.basename(args.apk)}（{size_mb:.1f} MB）==")
    code, out, err = adb("-s", serial, "install", "-r", "-t", args.apk, timeout=600)
    text = (out + err).strip()
    if code != 0 or "Success" not in out:
        raise SystemExit(
            "安装失败:\n" + text +
            "\n\n小米/HyperOS 常见原因：手机上没有点「继续安装」，或开启了「USB 安装」限制。"
        )
    print("安装成功")

    if args.no_launch:
        return

    print("== 启动 ==")
    adb("-s", serial, "shell", "am", "force-stop", PACKAGE)
    adb("-s", serial, "shell", "monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(3)
    _, focus, _ = adb("-s", serial, "shell", "dumpsys", "activity", "activities")
    line = next((l.strip() for l in focus.splitlines() if "topResumedActivity" in l or "mResumedActivity" in l), "")
    if PACKAGE in line:
        print(f"已在前台: {line}")
    else:
        print("警告: 没有确认到前台 Activity，请在手机上查看是否弹出权限/安装提示。")

    if args.screenshot:
        out_dir = os.path.join(ROOT, "artifacts", "install-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))
        os.makedirs(out_dir, exist_ok=True)
        local = os.path.join(out_dir, "launch.png")
        adb("-s", serial, "shell", "screencap", "-p", "/sdcard/_install.png")
        adb("-s", serial, "pull", "/sdcard/_install.png", local)
        print(f"截图: {os.path.relpath(local, ROOT)}")

    print("\n完成。手机上直接点开「Fen」即可验收。")


if __name__ == "__main__":
    main()

