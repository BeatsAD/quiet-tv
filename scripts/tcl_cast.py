#!/usr/bin/env python3
"""TCL phone-local APK push protocol, with a loopback-only download server.

Protocol reference: pengchujin/aerial-tv tools/tcast-push.py (MIT).
Copyright (c) 2026 pengchujin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import re
import socket
import struct
import threading
import time
import uuid


class Unavailable(ValueError):
    """No usable push channel; no installation request has been accepted."""


class Refused(ValueError):
    """The viewer refused. Stop, rather than silently using another installer."""


def send(connection, message):
    data = message.encode("utf-8")
    connection.sendall(struct.pack(">I", len(data)) + data)


def frames(buffer):
    messages = []
    while len(buffer) >= 4:
        length = struct.unpack(">I", buffer[:4])[0]
        if not 0 < length <= 1024 * 1024:
            raise ValueError("多屏互动消息长度无效")
        if len(buffer) < length + 4:
            break
        messages.append(buffer[4:4 + length].decode("utf-8"))
        buffer = buffer[4 + length:]
    return messages, buffer


def transfer(connection, url, metadata, size, timeout, progress):
    send(connection, "159>>1>>0")
    # This MD5 identifies the URL in TCL's protocol; APK trust is checked separately with SHA-256.
    token = hashlib.md5(url.encode()).hexdigest()
    send(connection, ">>".join(["223", "INSTALL", metadata["package"], "Quiet TV", url, "2",
                              str(metadata["version_code"]), f"{size / 1048576:.3f}", "", "", "", token]))
    deadline, heartbeat, buffer = time.monotonic() + timeout, time.monotonic(), b""
    connection.settimeout(1)
    reported = set()
    while time.monotonic() < deadline:
        try:
            data = connection.recv(65536)
            if not data:
                raise ValueError("多屏互动连接中断，安装结果未确认；不自动换渠道重装")
            buffer += data
        except socket.timeout:
            pass
        messages, buffer = frames(buffer)
        for message in messages:
            fields = message.split(">>")
            if len(fields) < 5 or fields[0] != "223" or fields[2] != metadata["package"]:
                continue
            # Some firmware omits the correlation token; when supplied it must match this request.
            correlation = fields[5] if fields[1] == "INSTALL" and len(fields) >= 6 else fields[6] if fields[1] == "PROGRESS" and len(fields) >= 7 else ""
            if correlation and correlation != token:
                continue
            if fields[1] == "INSTALL" and fields[4] == "denied":
                raise Refused("用户在电视上拒绝安装，已停止")
            if fields[1] == "PROGRESS" and fields[4] == "2":
                raise ValueError("电视报告下载或安装失败，未自动重装")
            if fields[1] == "PROGRESS" and fields[4] == "5":
                progress("电视报告安装完成，继续通过 ADB 核对实际 APK")
                return
            state = (fields[1], fields[4])
            if state not in reported:
                reported.add(state)
                progress("请在电视多屏互动提示中选择同意" if fields[4] == "confirm_pending"
                         else "多屏互动阶段：" + fields[4])
        if time.monotonic() - heartbeat >= 5:
            send(connection, "150>>")
            heartbeat = time.monotonic()
    raise ValueError("等待电视安装超时，结果未确认；不自动重复安装")


def target_host(adb, serial, explicit=None):
    # A network ADB target pins the push to exactly that IP. USB needs an on-device address check.
    host = serial.rsplit(":", 1)[0].strip("[]") if ":" in serial else ""
    try:
        address = str(ipaddress.ip_address(host))
    except ValueError:
        address = None
    if explicit:
        explicit = str(ipaddress.ip_address(explicit))
        if address and explicit != address:
            raise ValueError("多屏互动地址与已选网络 ADB 目标不一致")
        if not address:
            addresses = re.findall(r"\binet6?\s+([^\s/]+)", adb.shell("ip", "addr", "show"))
            if explicit not in addresses:
                raise ValueError("指定多屏互动地址未在目标电视上读回")
        return explicit
    if not address:
        raise Unavailable("USB ADB 需提供并核对电视的 --tcast-host 地址")
    return address


def install(adb, args, apk, metadata, journal):
    if args.user != 0:
        raise Unavailable("多屏互动的目标用户只在用户0验证过，改用当前用户的电视端安装")
    package = adb.shell("dumpsys", "package", "com.tcl.MultiScreenInteraction_TV")
    if not re.search(r"\bSYSTEM\b", package) or not re.search(r"User 0:.*\binstalled=true\b", package):
        raise Unavailable("目标电视没有可用的系统多屏互动安装应用")
    host = target_host(adb, args.serial, args.tcast_host)
    try:
        connection = socket.create_connection((host, 6553), timeout=5)
    except OSError as error:
        raise Unavailable("多屏互动端口不可用：" + str(error)) from error
    data = apk.read_bytes()
    if hashlib.sha256(data).hexdigest() != metadata["sha256"]:
        connection.close()
        raise ValueError("推送前 APK 已变化")
    route = "/" + uuid.uuid4().hex + "/QuietTV.apk"

    class Download(BaseHTTPRequestHandler):
        def do_HEAD(self):
            if self.path != route:
                self.send_error(404)
                return False
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            return True

        def do_GET(self):
            if self.do_HEAD():
                self.wfile.write(data)

        def log_message(self, *_):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Download)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    remote = None
    try:
        # Port zero allocates a fresh mapping; never replace or remove a shared reverse rule.
        port = adb.run("reverse", "--no-rebind", "tcp:0", "tcp:" + str(server.server_port))
        if not re.fullmatch(r"[1-9]\d{0,4}", port) or int(port) > 65535:
            raise ValueError("ADB 未返回本次动态下载端口，停止推送")
        remote = "tcp:" + port
        journal.event("tcast_channel", host=host, reverse=remote)
        print("正在通过 TCL 多屏互动安装，请在电视上确认同意。", flush=True)
        transfer(connection, f"http://127.0.0.1:{port}{route}", metadata, len(data), args.install_timeout,
                 lambda message: (print(message, flush=True), journal.event("tcast_progress", message=message)))
    finally:
        connection.close()
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
        if remote:
            try:
                adb.run("reverse", "--remove", remote)
            except (OSError, ValueError) as error:
                journal.event("tcast_reverse_cleanup_unconfirmed", reverse=remote, error=str(error))
                raise ValueError("本次下载通道清理待确认：" + remote) from error
