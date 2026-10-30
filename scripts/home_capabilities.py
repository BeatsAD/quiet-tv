#!/usr/bin/env python3
"""Read-only HOME route advice and preconditions, based on observed firmware capabilities."""
import re
import subprocess

from verify_install import HOME, HOME_ARGS, component, help_text, resolver
import configure_home_guard as guard

PROPERTIES = {'brand': 'ro.product.brand', 'manufacturer': 'ro.product.manufacturer',
              'model': 'ro.product.model', 'device': 'ro.product.device',
              'product': 'ro.product.name', 'display': 'ro.build.display.id'}
SETUP = 'com.google.android.tungsten.setupwraith'
GOOGLE = ('com.google.android.apps.tv.launcherx', 'com.google.android.tvlauncher')
CHECKS = ('physical_home', 'user_reboot', 'cold_boot', 'user_wake', 'recovery')


def real_home(value):
    """A HOME claimant can be a recovery placeholder, settings, factory test or input player."""
    if not value or '/' not in value:
        return False
    package, activity = value.split('/', 1)
    return (package not in ('android', SETUP, 'com.android.settings', 'com.android.tv.settings',
                            'com.android.provision', 'com.jmgo.tv', 'com.jmgo.fallbackhome')
            and not activity.endswith(('FallbackHome', 'RecoveryActivity', 'ResolverActivity'))
            and not re.search(r'(?i)factory|production|(?:^|\.)fac(?:\.|$)', package))


def matching_references(references, identity):
    # References can restrict a risky action or guide investigation, never grant write eligibility.
    return [ref for ref in references if ref.get('match') and
            all(identity.get(key) == value for key, value in ref['match'].items())]


def preserve_reason(identity, packages, original, references, selected=None):
    stock = original.split('/')[0]
    for ref in matching_references(references, identity):
        if stock in ref.get('preservePackages', []):
            return ref['limitations']
    policy = (selected or {}).get('home', {})
    exact = policy.get('disableVerified') and guard.canonical_home(policy.get('component', '')) == original
    if any(name.startswith('com.cvte.') for name in packages) and not exact:
        return '检测到 CVTE 平台且无本固件精确禁用规则；保留原桌面，先验证默认 HOME 和冷启动'
    return ''


def setup_wifi_blocker(text):
    owners = re.findall(r'\bcname=([^\s,\]}]+)', text)
    if not owners or any(not re.fullmatch(r'[A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)+', owner) for owner in owners):
        return '无法确认保存的 Wi-Fi 创建者，不禁用 Google 设置向导'
    if any(SETUP in owner for owner in owners):
        return 'Google 设置向导持有保存的 Wi-Fi；先由用户在系统设置重新配置网络，再重新检查'
    return ''


def collect(adb, device, references=(), selected=None):
    """Only system reads: no broadcasts, launches, installs, discovery, reboots or settings writes."""
    unknown = {}
    def read(name, *args):
        try:
            return adb.shell(*args)
        except (ValueError, OSError, subprocess.TimeoutExpired) as error:
            unknown[name] = str(error)
            return ''

    identity = {key: read(key, 'getprop', value) for key, value in PROPERTIES.items()}
    identity.update(sdk=device['sdk'], fingerprint=device['fingerprint'])
    homes, setter = set(), None
    for tool in (('cmd', 'package'), ('pm',)):
        try:
            usage = help_text(adb, *tool)
            query = guard.user_options(usage, 'query-activities', device['sdk'], device['user'])
        except (ValueError, OSError, subprocess.TimeoutExpired):
            continue
        raw = read('homeCandidates', *tool, 'query-activities', '--brief', *query, *HOME_ARGS)
        if not raw:
            continue
        homes = {value for line in raw.splitlines() if (value := component(line))}
        try:
            options = guard.user_options(usage, 'set-home-activity', device['sdk'], device['user'])
            setter = [*tool, 'set-home-activity', *options]
        except ValueError:
            pass
        break
    if not homes:
        unknown['homeCandidates'] = '未取得当前用户的 HOME 候选'
    try:
        resolved = read('resolvedHome', *resolver(adb, device['sdk'], device['user']))
        actual = component(resolved)
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        actual = ''
        unknown['resolvedHome'] = str(error)
    # Package identities only; do not export the full system package dump in the advice.
    package_dump = read('packages', 'dumpsys', 'package')
    packages = set(re.findall(r'(?m)^\s*Package \[([\w.]+)\]', package_dump))
    if not packages:
        unknown['packages'] = '未取得系统包身份，不能确认厂商启动依赖'
    own = read('quietPackage', 'dumpsys', 'package', 'home.quiet.tv')
    native = bool(setter and HOME in homes and real_home(actual))
    risks = matching_references(references, identity)
    reason = preserve_reason(identity, packages, actual, references, selected)
    wifi = read('wifi', 'dumpsys', 'wifi') if any(h.startswith(SETUP + '/') for h in homes) else ''
    setup_blocker = setup_wifi_blocker(wifi) if any(h.startswith(SETUP + '/') for h in homes) else ''
    vendor = set((identity['brand'] + ' ' + identity['manufacturer']).lower().split())
    properties = {key: read(key, 'getprop', key) for key in ('persist.vt.use_home_pkg', 'persist.sys.home.selectpkg')}
    policy = (selected or {}).get('home', {})
    def route(name, possible, detail):
        return dict(route=name, status='candidate' if possible else 'unconfirmed', detail=detail)
    routes = [route('native', native, '保留原桌面；实际 APK 核验后由 configure_boot_home.py 配置并读回'),
              route('vidaa', bool(device['user'] == 0 and vendor.intersection(('hisense', 'vidaa'))
                                  and any(h.startswith('com.jamdeo.tv.vod/') for h in homes)
                                  and properties['persist.vt.use_home_pkg'] in ('com.jamdeo.tv.vod', 'home.quiet.tv')),
                    '核对实际系统 HOME 和厂商属性；属性可读不代表可写或重启有效'),
              route('jm', bool(device['user'] == 0 and 'com.jm.jmlauncher' in packages),
                    '由专项预览另验 HOME.JM 组件、系统包和原属性，不按 JMGO 品牌直接套用'),
              route('verified', bool(policy.get('disableVerified') and not reason),
                    reason or '仅精确固件规则授予禁用候选资格；证据备注和实体验收另看'),
              route('google', bool(setter and HOME in homes and
                                   (actual.split('/')[0] in GOOGLE or actual.startswith(SETUP + '/') and
                                    len([h for h in homes if h.split('/')[0] in GOOGLE and real_home(h)]) == 1)
                                   and not reason and not setup_blocker and packages),
                    reason or setup_blocker or '先确认系统初始化及恢复基线；可选禁用须明确选择'),
              route('guard', device['sdk'] >= 21 and 'HomeGuardService' in own,
                    '保留原桌面，核验绑定、事件、自启动和实体按键；可能先显示原桌面再跳转')]
    return dict(schemaVersion=1, identity=identity, user=device['user'], home=actual,
                homeCandidates=sorted(homes), realHome=real_home(actual), setter=setter,
                cvte=any(name.startswith('com.cvte.') for name in packages),
                routes=routes, preserveOriginalReason=reason, setupWifiBlocker=setup_blocker,
                knownRisks=risks, unknown=unknown, requiredChecks=list(CHECKS),
                next='沿用有效路线；否则先 native，再按现场评估厂商路由／精确禁用，最后守护／已适配代理；失败不自动换路重试',
                note='candidate 仅为配置候选，不是开机验收。ADB、APK安装、HOME、禁用、首屏及广告分别报告。')


def check_stock_change(adb, device, selected, references, original, targets):
    """Recheck only destructive HOME handover preconditions before any package change."""
    advice = collect(adb, device, references, selected)
    if 'packages' in advice['unknown']:
        raise ValueError(advice['unknown']['packages'])
    reason = preserve_reason(advice['identity'], ['com.cvte.'] if advice['cvte'] else [],
                             original, references, selected)
    if reason:
        raise ValueError('原桌面须保留：' + reason)
    if not real_home(original):
        raise ValueError('恢复基线不是可用原桌面，不能禁用 HOME 候选')
    if SETUP in targets:
        blocker = setup_wifi_blocker(adb.shell('dumpsys', 'wifi'))
        if blocker:
            raise ValueError(blocker)
    return advice
