"""一键验收脚本：编译产物 -> 安装 -> 走一遍核心流程 -> 断言 -> 截图。

用法（在仓库根目录）:
    python scripts/acceptance.py                # 安装现有 APK 并验收
    python scripts/acceptance.py --build        # 先 gradlew assembleDebug 再验收
    python scripts/acceptance.py --serial emulator-5554
    python scripts/acceptance.py --keep-data    # 不清空 App 数据（默认清空）

产物:
    artifacts/acceptance-<时间戳>/  截图 + ui dump + 结果摘要
"""

import argparse
import datetime
import io
import os
import re
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACKAGE = "app.fen"
APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk")

results = []


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


def adb(*args, serial=None, timeout=120):
    cmd = [ADB] + (["-s", serial] if serial else []) + list(args)
    proc = subprocess.run(cmd, capture_output=True, timeout=timeout)
    return proc.returncode, proc.stdout.decode("utf-8", "replace"), proc.stderr.decode("utf-8", "replace")


def check(name, fn):
    try:
        detail = fn() or ""
        results.append((name, True, detail))
        print(f"[PASS] {name} {detail}")
    except Exception as exc:  # noqa: BLE001
        results.append((name, False, str(exc)))
        print(f"[FAIL] {name}: {exc}")


def pick_device(serial):
    _, out, _ = adb("devices")
    rows = [line.split("\t")[0] for line in out.splitlines()[1:] if line.strip().endswith("device")]
    if serial:
        if serial not in rows:
            raise SystemExit(f"设备 {serial} 不在线，当前: {rows}")
        return serial
    if not rows:
        raise SystemExit("没有在线设备，先启动模拟器: C:\\Android\\tools\\device.ps1 boot")
    if len(rows) > 1:
        raise SystemExit(f"检测到多台设备 {rows}，请用 --serial 指定")
    return rows[0]


class Session:
    def __init__(self, serial, out_dir):
        self.serial = serial
        self.out_dir = out_dir
        self.dump_index = 0

    def shell(self, *args, **kwargs):
        return adb("shell", *args, serial=self.serial, **kwargs)

    def dump(self):
        self.dump_index += 1
        remote = "/sdcard/ui.xml"
        local = os.path.join(self.out_dir, f"ui-{self.dump_index:02d}.xml")
        last = ""
        for _ in range(5):
            code, out, err = self.shell("uiautomator", "dump", remote)
            if "dumped" in out or "dumped" in err:
                break
            last = out + err
            time.sleep(0.5)
        else:
            raise RuntimeError(f"uiautomator dump 失败: {last.strip()}")
        adb("pull", remote, local, serial=self.serial)
        text = io.open(local, encoding="utf-8", errors="replace").read()
        nodes = []
        for match in re.finditer(r"<node[^>]*>", text):
            tag = match.group(0)
            t = re.search(r'text="([^"]*)"', tag)
            d = re.search(r'content-desc="([^"]*)"', tag)
            b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', tag)
            if not b:
                continue
            x1, y1, x2, y2 = (int(v) for v in b.groups())
            label = (t.group(1) if t else "") or (d.group(1) if d else "")
            nodes.append({"label": label, "center": ((x1 + x2) // 2, (y1 + y2) // 2), "box": (x1, y1, x2, y2)})
        return nodes

    def find(self, text, exact=True):
        nodes = self.dump()
        if exact:
            hits = [n for n in nodes if n["label"].strip() == text]
        else:
            hits = [n for n in nodes if text in n["label"]]
        return hits

    def tap(self, text, exact=True, index=0):
        hits = self.find(text, exact=exact)
        if not hits:
            raise RuntimeError(f"界面上找不到文本「{text}」")
        x, y = hits[index]["center"]
        self.shell("input", "tap", str(x), str(y))
        return x, y

    def wait_text(self, text, timeout=8.0, exact=True):
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                if self.find(text, exact=exact):
                    return True
            except RuntimeError:
                pass
            time.sleep(0.5)
        raise RuntimeError(f"等待文本「{text}」超时")

    def shot(self, name):
        remote = "/sdcard/_acceptance.png"
        local = os.path.join(self.out_dir, name)
        self.shell("screencap", "-p", remote)
        adb("pull", remote, local, serial=self.serial)
        return local

    def start_app(self):
        self.shell("am", "force-stop", PACKAGE)
        time.sleep(0.5)
        code, out, err = self.shell("monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
        if code != 0:
            raise RuntimeError(f"启动失败: {out}{err}")


def rel(path):
    return os.path.relpath(path, ROOT)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial")
    parser.add_argument("--build", action="store_true", help="先执行 gradlew :app:assembleDebug")
    parser.add_argument("--apk", default=APK)
    parser.add_argument("--keep-data", action="store_true", help="保留已有数据")
    parser.add_argument("--out", default=None)
    args = parser.parse_args()

    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    out_dir = args.out or os.path.join(ROOT, "artifacts", f"acceptance-{stamp}")
    os.makedirs(out_dir, exist_ok=True)

    if args.build:
        print("== 构建 ==")
        env = dict(os.environ)
        env["ANDROID_HOME"] = env.get("ANDROID_HOME") or SDK
        if not env.get("JAVA_HOME") and os.path.isdir(r"C:\Program Files\Microsoft\jdk-21.0.13.102-hotspot"):
            env["JAVA_HOME"] = r"C:\Program Files\Microsoft\jdk-21.0.13.102-hotspot"
        gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
        proc = subprocess.run([gradlew, ":app:assembleDebug", "-q"], cwd=ROOT, env=env)
        if proc.returncode != 0:
            raise SystemExit("构建失败")

    if not os.path.exists(args.apk):
        raise SystemExit(f"找不到 APK: {args.apk}，先执行 gradlew :app:assembleDebug 或加 --build")

    serial = pick_device(args.serial)
    session = Session(serial, out_dir)
    print(f"== 设备 {serial} ==")

    def install():
        code, out, err = adb("install", "-r", "-t", args.apk, serial=serial, timeout=300)
        if code != 0 or "Success" not in out:
            raise RuntimeError((out + err).strip()[-300:])
        return os.path.basename(args.apk)

    check("安装 APK", install)

    if not args.keep_data:
        check("清空应用数据", lambda: (session.shell("pm", "clear", PACKAGE), "ok")[1])

    check("冷启动", session.start_app)

    def home_ready():
        session.wait_text("明细", timeout=15)
        session.wait_text("记一笔", timeout=10)
        return session.shot("01-home-empty.png") and ""

    check("首页加载 + 空态", home_ready)

    def open_add():
        session.tap("记一笔")
        session.wait_text("完成")
        session.wait_text("点击添加备注", exact=False)
        return session.shot("02-add.png") and ""

    check("打开记账页", open_add)

    def enter_amount():
        for key in ("1", "2", "3"):
            session.tap(key)
            time.sleep(0.2)
        return session.shot("03-add-filled.png") and ""

    check("输入金额 123", enter_amount)

    def save():
        session.tap("完成")
        session.wait_text("-123.00", timeout=10)
        session.wait_text("餐饮", timeout=5)
        return session.shot("04-home-saved.png") and ""

    check("保存后回到明细并出现记录", save)

    def edit_record():
        session.tap("餐饮")
        session.wait_text("123.00", timeout=10)
        session.shot("05-edit-open.png")
        for key in ("2", "0", "0"):
            session.tap(key)
            time.sleep(0.2)
        session.tap("完成")
        session.wait_text("-200.00", timeout=10)
        return session.shot("06-edit-saved.png") and ""

    check("点开记录并改金额", edit_record)

    def stats():
        session.tap("统计")
        session.wait_text("分类排行", timeout=10)
        session.wait_text("200.00", timeout=5)
        session.wait_text("最近 7 天", timeout=5)
        return session.shot("07-stats.png") and ""

    check("统计页环形图与排行", stats)

    def settings():
        session.tap("设置")
        session.wait_text("深色模式", timeout=10)
        session.wait_text("关于", timeout=5)
        return session.shot("08-settings.png") and ""

    check("设置页结构（右上角入口）", settings)

    def back_home():
        session.tap("返回")
        session.wait_text("明细", timeout=10)
        return ""

    check("设置返回明细", back_home)

    passed = sum(1 for r in results if r[1])
    total = len(results)
    summary = os.path.join(out_dir, "summary.txt")
    with io.open(summary, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"Fen 验收 {stamp}\n设备: {serial}\nAPK: {args.apk}\n\n")
        for name, ok, detail in results:
            f.write(("PASS " if ok else "FAIL ") + name + ("  " + detail if detail else "") + "\n")
        f.write(f"\n{passed}/{total} 通过\n")

    print()
    print(f"结果: {passed}/{total} 通过")
    print(f"截图与摘要: {rel(out_dir)}")
    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()



