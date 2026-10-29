#!/usr/bin/env python3
"""Read-only ADB guidance and open-ended evidence collection for attached devices."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

ANDROID_GUIDE = dict(
    applies='标准 Android TV／Google TV 调试菜单；厂商界面可能不同',
    steps=['已有开发者选项时直接进入；隐藏时在设置的关于中找到 Build／版本号，按确定至提示已启用',
           '返回设置，进入开发者选项，打开实际提供的 USB／ADB 调试',
           '按电视实际提供的 USB、网络调试或无线配对入口连接，确认电脑授权'],
    sources=['https://developer.android.com/training/tv/get-started/create',
             'https://developer.android.com/studio/debug/dev-options'], checked='2026-10-29')
FIRE_GUIDE = dict(
    applies='Android 系 Fire OS；不适用于 Vega OS',
    steps=['设置 → My Fire TV／Device & Software → Developer Options',
           '开启 ADB debugging；开发者菜单隐藏或界面不同时核对实际系统后定向检索',
           '使用电视实际网络信息连接，确认电脑授权'],
    sources=['https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html'], checked='2026-10-29')
# These are observation channels, never a whitelist of peripheral kinds.
CHANNELS = ('input', 'usb', 'bluetooth_manager', 'bluetooth', 'audio', 'hdmi_control',
            'tv_input', 'display', 'media_router', 'mount')


def adb_devices(text):
    return [{'serial': match[1], 'state': match[2]} for line in text.splitlines()
            if (match := re.match(r'^(\S+)\s+(device|offline|unauthorized|no permissions)\b', line))]


def adb_plan(devices, serial=None, system='unknown', brand='', model='', firmware='',
             debug_enabled=False, menu_mismatch=False, connection_error=''):
    state = next((item['state'] for item in devices if item['serial'] == serial), '')
    result = dict(devices=devices, target=serial, system=system, brand=brand, model=model, firmware=firmware)
    if not serial and devices:
        return dict(result, route='select_target', next='让用户确认目标，随后指定 --serial，不自动选择设备')
    if state == 'device':
        return dict(result, route='connected', next='复用已有连接，继续只读检测，不再指导开启 ADB')
    text = connection_error.lower()
    if state == 'unauthorized' or 'unauthorized' in text or 'authentication' in text or 'authenticate' in text:
        return dict(result, route='authorization', next='核对所选电脑授权及配对状态；不重复开启开发者菜单')
    if state == 'offline' or debug_enabled or connection_error or state == 'no permissions':
        cause = ('host_permission' if state == 'no permissions' else 'pairing' if 'pair' in text else
                 'port' if 'refused' in text else 'network' if 'timed out' in text or 'no route' in text else
                 'offline' if state == 'offline' else 'address_port_transport')
        return dict(result, route='connection_diagnosis', cause=cause, debugEnabled=debug_enabled,
                    next='核对调试开关实际状态；已开则查地址、端口、连接方式和授权，未开再按系统指引；不猜端口或重复教程')
    if system == 'vega':
        return dict(result, route='limited', next='Vega 不使用此 Android APK／ADB 流程，记录系统限制')
    guide = ANDROID_GUIDE if system in ('android-tv', 'google-tv') else FIRE_GUIDE if system == 'fire-os' else None
    if guide and not menu_mismatch:
        return dict(result, route='guide', guide=guide, next='用户按匹配的常规指引操作；菜单不符加 --menu-mismatch')
    query = ' '.join(value for value in (brand, model, firmware, system if system != 'unknown' else '',
                                       '电视 ADB 开发者选项 网络调试 官方') if value)
    return dict(result, route='research', query=query,
                next='Agent 先补齐实际型号／系统及菜单差异，再定向检索官方资料；记录来源，不猜隐藏密码')


def preflight(args):
    try:
        result = subprocess.run([args.adb, 'devices', '-l'], capture_output=True, text=True,
                                encoding='utf-8', errors='replace', timeout=10)
        if result.returncode:
            return dict(route='prepare_adb', next='修复电脑 Platform-Tools／ADB 服务后再检测',
                        exit=result.returncode, error=clean(result.stderr or result.stdout).strip(),
                        source='https://developer.android.com/tools/releases/platform-tools')
    except (OSError, subprocess.TimeoutExpired) as error:
        return dict(route='prepare_adb', next='准备电脑官方 Platform-Tools，无需下载完整开发工程',
                    error=clean(str(error)),
                    source='https://developer.android.com/tools/releases/platform-tools')
    return adb_plan(adb_devices(result.stdout), args.serial, args.system, args.brand, args.model,
                    args.firmware, args.debug_enabled, args.menu_mismatch, args.connection_error)


def clean(text):
    # Keep names and topology, not hardware/network identifiers in exported observations.
    text = re.sub(r'(?i)\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\b', '<address>', text)
    return re.sub(r'(?i)((?:mSerialNumber|serialNumber|serial)\s*[:=]\s*)[^,}\]\s]+', r'\1<serial>', text)


def observations(channel, text):
    evidence, devices = [], {}
    lines = text.splitlines()
    for number, line in enumerate(lines, 1):
        if re.search(r'(?i)device|name|product|manufacturer|connected|attached|port|route|display|volume|address|external', line):
            evidence.append(dict(line=number, text=clean(line.strip())))
        patterns = (r'^\s*Device\s+\d+:\s*(.+)', r'\bmProductName\s*=\s*([^,\]]+)',
                    r'(?i)\b(?:name|product name|display_name)\s*[:=]\s*("[^"]+"|[^,}\]]+)')
        for pattern in patterns:
            match = re.search(pattern, line)
            if not match:
                continue
            name = clean(match[1].strip().strip('"'))
            if name.lower() in ('', 'null', 'none', '<none>'):
                break
            key = hashlib.sha256((channel + ':' + str(number) + ':' + name).encode()).hexdigest()[:12]
            context = re.split(r'\n\s*Device\s+\d+:', '\n'.join(lines[number-1:number+7]), maxsplit=1)[0]
            external = re.search(r'(?i)\b(?:IsExternal|External)\s*[:=]\s*(true|false)', context)
            devices[key] = dict(id=key, name=name, channel=channel, line=number,
                                external=external[1].lower() == 'true' if external else None,
                                connection='observed', usability='UNVERIFIED')
            break
    if not evidence:
        evidence = [dict(line=i, text=clean(line.strip())) for i, line in enumerate(lines, 1) if line.strip()]
    return list(devices.values()), evidence[:120], max(0, len(evidence)-120)


def discover(adb, user, package='home.quiet.tv', include=(), services=()):
    if adb.run('get-state') != 'device' or adb.current_user() != user:
        raise ValueError('需已授权的指定设备与当前用户')
    scope = dict(serial=adb.serial, user=user, fingerprint=adb.shell('getprop', 'ro.build.fingerprint'))
    if not scope['fingerprint']:
        raise ValueError('固件指纹未确认，不保存为本机外设现场')
    try:
        listing = adb.shell('service', 'list')
        available = set(re.findall(r'^\s*\d+\s+([^\s:]+):', listing, re.M))
    except (OSError, ValueError, subprocess.TimeoutExpired):
        available = set()

    def read(channel):
        if available and channel in CHANNELS and channel not in available:
            return dict(channel=channel, status='unavailable', devices=[], evidence=[])
        try:
            text = adb.shell('dumpsys', channel)
            if re.search(r"(?i)can't find service|permission denial|permission denied|not found", text):
                return dict(channel=channel, status='unavailable', devices=[], evidence=[])
            devices, evidence, omitted = observations(channel, text)
            return dict(channel=channel, status='observed' if text.strip() else 'unknown',
                        devices=devices, evidence=evidence, omittedLines=omitted)
        except (OSError, ValueError, subprocess.TimeoutExpired) as error:
            return dict(channel=channel, status='unknown', devices=[], evidence=[],
                        error=clean(str(error)), errorType=type(error).__name__)

    for name in services:
        if not re.fullmatch(r'[A-Za-z0-9_][\w./:@-]*', name):
            raise ValueError('附加服务须为已核对的实际系统服务名')
    with ThreadPoolExecutor(max_workers=3) as pool:
        channels = list(pool.map(read, dict.fromkeys((*CHANNELS, *services))))
    if adb.current_user() != user or adb.shell('getprop', 'ro.build.fingerprint') != scope['fingerprint']:
        raise ValueError('采集期间用户或固件改变，不复用本次现场')
    devices = [device for item in channels for device in item['devices']]
    for name in include:
        devices.append(dict(id=hashlib.sha256(('user:' + name).encode()).hexdigest()[:12],
                            name=clean(name), channel='supplement', connection='reported', usability='UNVERIFIED'))
    return dict(schemaVersion=1, serial=adb.serial, package=package, identity=scope,
                createdAt=datetime.now(timezone.utc).isoformat(), devices=devices, channels=channels,
                availableServices=sorted(available), coverage='system_observations_and_user_supplement',
                note='候选可能包含内置设备或同一设备的不同通道；Agent结合现场确认，不按品类排除。未发现不等于不存在，连接与实际好用须分别验收。')


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('action', choices=('adb', 'peripherals'))
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial')
    parser.add_argument('--user', type=int)
    parser.add_argument('--package', default='home.quiet.tv')
    parser.add_argument('--record', type=Path)
    parser.add_argument('--system', choices=('unknown', 'android-tv', 'google-tv', 'fire-os', 'vega'), default='unknown')
    for name in ('brand', 'model', 'firmware', 'connection-error'):
        parser.add_argument('--'+name, default='')
    parser.add_argument('--debug-enabled', action='store_true')
    parser.add_argument('--menu-mismatch', action='store_true')
    parser.add_argument('--include-device', action='append', default=[])
    parser.add_argument('--service', action='append', default=[], help='Agent按现场补充的实际只读系统服务，不限定设备品类')
    args = parser.parse_args()
    try:
        if args.action == 'adb':
            value = preflight(args)
        else:
            if not args.serial or args.user is None or args.user < 0 or not args.record:
                parser.error('peripherals需要明确serial、user和全新的record目录')
            from configure_home_guard import ComputerAdb
            class ReadAdb(ComputerAdb):
                def _result(self, *arguments, timeout=8):
                    return super()._result(*arguments, timeout=min(timeout, 8))
            value = discover(ReadAdb(args.adb, args.serial), args.user, args.package, args.include_device, args.service)
        if args.record:
            args.record.mkdir(mode=0o700, parents=False, exist_ok=False)
            (args.record/'report.json').write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        print(json.dumps(value, ensure_ascii=False, indent=2))
        return 0
    except (OSError, ValueError, subprocess.TimeoutExpired) as error:
        print('现场检查未完成：'+str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
