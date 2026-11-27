#!/usr/bin/env python3
"""本地模拟 GitHub Release，用来验收应用内更新流程。

用法示例：
    python scripts/mock_update_server.py \
        --dir app/build/outputs/apk/debug --version 9.9.9

然后在电脑上：
    adb reverse tcp:8765 tcp:8765
    adb shell am start -a android.intent.action.VIEW \
        -d "fen://debug/update-source?json=http://127.0.0.1:8765/latest.json" \
        -p app.fen.dev.feat_update

模拟器可用 10.0.2.2 代替 127.0.0.1。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

DEFAULT_NOTES = """### 更新内容
- 支持在应用内检查更新
- 下载并校验安装包后直接安装
"""


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="为 Fen 的应用内更新提供本地模拟 Release。",
    )
    parser.add_argument(
        "--dir",
        default="app/build/outputs/apk/debug",
        help="APK 所在目录（默认 app/build/outputs/apk/debug）",
    )
    parser.add_argument(
        "--apk",
        help="APK 文件名；默认取目录里第一个 .apk",
    )
    parser.add_argument(
        "--version",
        default="9.9.9",
        help="模拟的版本号，必须比手机上安装的版本新（默认 9.9.9）",
    )
    parser.add_argument("--port", type=int, default=8765, help="监听端口（默认 8765）")
    parser.add_argument("--host", default="0.0.0.0", help="监听地址（默认 0.0.0.0）")
    parser.add_argument(
        "--base-url",
        help="手机访问本服务用的地址前缀；默认 http://127.0.0.1:<port>（配合 adb reverse）",
    )
    parser.add_argument("--notes", default=DEFAULT_NOTES, help="Release 说明文本")
    return parser.parse_args(argv)


def pick_apk(directory: Path, name: str | None) -> Path:
    if name:
        apk = directory / name
        if not apk.is_file():
            raise SystemExit(f"找不到 APK：{apk}")
        return apk
    candidates = sorted(directory.glob("*.apk"))
    if not candidates:
        raise SystemExit(f"目录里没有 .apk：{directory}")
    return candidates[0]


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_release(
    version: str,
    notes: str,
    apk_name: str,
    apk_size: int,
    apk_hash: str,
    base_url: str,
) -> dict:
    base = base_url.rstrip("/")
    return {
        "tag_name": f"v{version}",
        "name": f"Fen {version}",
        "html_url": "https://github.com/Aj002Th/fen/releases",
        "body": notes,
        "assets": [
            {
                "name": apk_name,
                "browser_download_url": f"{base}/{apk_name}",
                "size": apk_size,
                "digest": f"sha256:{apk_hash}",
            },
            {
                "name": f"{apk_name}.sha256",
                "browser_download_url": f"{base}/{apk_name}.sha256",
                "size": 0,
            },
        ],
    }


def make_handler(apk: Path, apk_name: str, release: dict, apk_hash: str):
    apk_size = apk.stat().st_size

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt: str, *args) -> None:  # noqa: D401 - stdlib hook
            sys.stderr.write("[mock] " + (fmt % args) + "\n")

        def do_GET(self) -> None:  # noqa: N802 - stdlib hook
            path = self.path.split("?", 1)[0]
            if path in ("/latest.json", "/latest"):
                payload = json.dumps(release, ensure_ascii=False).encode("utf-8")
                self._send(200, "application/json; charset=utf-8", payload)
            elif path == f"/{apk_name}":
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Length", str(apk_size))
                self.end_headers()
                with apk.open("rb") as handle:
                    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                        self.wfile.write(chunk)
            elif path == f"/{apk_name}.sha256":
                payload = f"{apk_hash}  {apk_name}\n".encode("utf-8")
                self._send(200, "text/plain; charset=utf-8", payload)
            else:
                self._send(404, "text/plain; charset=utf-8", b"not found")

        def _send(self, code: int, content_type: str, payload: bytes) -> None:
            self.send_response(code)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

    return Handler


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    directory = Path(args.dir)
    if not directory.is_dir():
        raise SystemExit(f"目录不存在：{directory}")
    apk = pick_apk(directory, args.apk)
    apk_name = f"Fen-{args.version}.apk"
    apk_hash = sha256_of(apk)
    base_url = args.base_url or f"http://127.0.0.1:{args.port}"
    release = build_release(
        args.version,
        args.notes,
        apk_name,
        apk.stat().st_size,
        apk_hash,
        base_url,
    )
    handler = make_handler(apk, apk_name, release, apk_hash)
    server = ThreadingHTTPServer((args.host, args.port), handler)

    package = os.environ.get("FEN_DEV_PACKAGE", "app.fen.dev.feat_update")
    print(f"[mock] 版本 {args.version}，APK {apk}")
    print(f"[mock] sha256 {apk_hash}")
    print(f"[mock] http://127.0.0.1:{args.port}/latest.json")
    print()
    print("验收前在另一个终端执行：")
    print(f"  adb reverse tcp:{args.port} tcp:{args.port}")
    print("  adb shell am start -a android.intent.action.VIEW \\")
    print(
        f'    -d "fen://debug/update-source?json=http://127.0.0.1:{args.port}/latest.json" '
        f"-p {package}"
    )
    print()
    print("结束后清空调试源：")
    print(
        f'  adb shell am start -a android.intent.action.VIEW '
        f'-d "fen://debug/update-source?clear" -p {package}'
    )
    print()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[mock] 已停止")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
