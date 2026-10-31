# ADB入口资料：品牌、型号与固件

[匹配与使用规则](adb.md)  [连接分流](connect.md)。本页是调查资料，不是执行规则。先按匹配指引核对条件，再只读取对应品牌段；不能逐条试码。资料采集于2026-10-31，58条记录中电视47条、投影仪7条、机顶盒4条；既含成功描述，也保留缺步骤和受阻报告，不是58种已验证方法。

**Quiet TV验证：本页全部未验证。** A／B／C表示来源层级，入口状态是上游描述，不代表本机验证。单个目录列出多个型号不算多次独立复验；一个品牌指引不构成全品牌兼容结论。未知固件不能当通配；同SoC、相近型号及不同地区固件不自动继承。引用资料不获得执行、安装第三方工具、禁用应用或HOME接管资格。

有可靠品牌／系统指引、步骤完整且现场页面吻合时，可按[匹配指引](adb.md)直接指导用户；“Quiet TV未验证”不等于不能提供步骤。型号／固件缺项只影响依赖这些条件的专项方法，不阻塞不要求这些字段的普通品牌路径。已有本机反例仍优先，不能因通用路径存在而忽略。

## 电视本体

<a id="xiaomi"></a>

### 小米／Redmi

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T01 小米<br>MiTV-ASTP0 | Android 9；完整固件号未给 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 设置→关于→产品型号或版本号连续确认5–7次；账号与安全→ADB调试→允许。<br>TCP 5555；首次电视RSA授权。 | 该报告重启会复位ADB开关，不能套用其他小米“永久开启”的结论。<br>[来源1](https://github.com/wangrongding/tv-fresh/blob/master/README.md) |
| T02 小米／Redmi<br>品牌指引，未限定型号 | MIUI TV等适用固件，未限定版本 | B 品牌／平台指引<br>完整入口<br>Quiet TV未验证 | 设置→关于产品→产品型号连续确认至开发者提示；账号与安全→ADB调试。另一分支为关于电视→版本号→更多设置→开发者选项。<br>端口及授权按固件核对。 | 未逐型号验证；允许未知来源安装与开启ADB是不同设置。<br>[来源1](https://github.com/quicktvui/quicktvui-next/blob/10a21cc05118d396833ccebee5828f0c0de0ba48/docs/zh-CN/guide/device/xiaomi.md) |

<a id="tcl"></a>

### TCL／雷鸟

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T03 TCL<br>T7K；tcl_mt5879_cn／MT9952 | Android 11；完整固件号未给 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 设置→关于→本机信息→上、下、左、右→底部更多→ADB。<br>TCP 5555；需电视授权。 | 该工具箱仅此TCL机型有完整实测，其他品牌参考表不能一并算实测。<br>[来源1](https://github.com/VictorTran1023/android-tv-toolkit/blob/main/docs/brands.md) |
| T04 TCL<br>55Q10G Pro | Android 11；RQ3A.210705.001／2026012200 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 关于本机→本机信息→上、下、左、右→底部ADB开关。<br>TCP 5555。 | 报告有时需重复2–3轮；该机重启后保持。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/examples/tcl-q10g-pro/debloat.txt) |
| T05 TCL／雷鸟<br>MT9655_4K_CN／Smart TV Pro；零售型号未明确 | Android 14／API 34；UTT2.250416.001 AU01 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 通用设置→更多→ADB；网络TLS无配对页。上游通过电视支持的USB调试接口接电脑，电视USB授权后执行adb tcpip 5555。<br>先USB授权，再TCP 5555。 | 原记录电脑USB3口、电视USB2口与双A线组合；不是通用接线方案，须确认设备端调试接口，勿任意连接两个USB-A主机口。重启须重新USB启用TCP。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/tcl-mt9655-cn-android14.md) |
| T06 TCL<br>98C12L | Android 14；UTT2.250416.001 | A 上游型号报告<br>连接受阻<br>Quiet TV未验证 | 关于→本机信息→上、下、左、右可打开ADB开关，但仅随机TLS端口，没有可用配对码入口。<br>查阅时Issue尚未获得可用ADB会话。 | USB仅为建议；机主最后回复尚未试，计划10月31日尝试。其他MT9655的成功不能算此型号成功。<br>[来源1](https://github.com/Gathin23/cleanyourtv/issues/101) |
| T07 TCL<br>75QM851G／2024 QM8／G08 | Android 14；V8-T653T02-LF1V321；UTT2.250416.001 AU14 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 设置→系统→关于→Android TV OS Build7次；开发者选项同时开启USB debugging与Wireless debugging，再开配对码页面。<br>adb pair配对端口，然后adb connect连接端口。 | 该固件需两开关都开但无需USB线。仓库未找到许可证，作为补充报告。<br>[来源1](https://github.com/Trigun1127/TCL-Chromecast-and-Search-Bar-Fix/blob/main/README.md) |
| T08 雷鸟<br>55S595C Ultra／MT9655 | V8-0015T11-LF1V487 | A 上游型号报告<br>连接受阻<br>Quiet TV未验证 | ADB开关可开，但随机TLS端口缺少配对页面。<br>原记录未完成正常网络ADB授权。 | 同页其他鹤6 Ultra的USB成功不能自动移植到该型号和固件。<br>[来源1](https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-mt9655-cn.md) |

<a id="hisense"></a>

### 海信／Vidda／Hisense Fire TV

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T09 海信／Vidda<br>2017款50英寸；VIDAA_TV／MSD6A828；零售型号未给 | Android 5.1.1；LVY48C 01.02.40.01；2017-05-22 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 声音设置→声音平衡→交替确定和菜单→工厂选项To Fac U改M→真正重启→安卓原生设置→关于→连续点版本号→开发者选项→USB调试。<br>TCP 5555；首次RSA授权。 | 作者自测；同代LED55N3000U只是其他文章旁证，不是这台50英寸的型号。待机不等于重启；记录To Fac原值。<br>[来源1](https://github.com/sanshengai/sansheng-tv-adb/blob/main/data/brands/hisense.json) |
| T10 海信<br>55E5Q-PRO／MT9653 | Android 11；02.02.70.01；UI V0000.00.01A.Q0702 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 声音平衡→交替确定与设置／菜单→Options→ToFac U改M→重启；版本／设备信息页按菜单5次；USB鼠标操作原生关于→Build连续点击→开发者选项→USB调试。<br>TCP 5555；按电视提示授权。 | 原生设置必须用USB鼠标；平衡灰色时先关对应杜比效果。完成后ToFac恢复U并重启，原报告ADB仍可用；M模式会影响OTA检查。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/examples/hisense-e5q-pro/README.md) |
| T11 海信／Vidda<br>85V7N-Ultra | 原文未记录系统／固件号 | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | 声音平衡→交替菜单和确定→工厂To Fac U改M→重启→设置→关于→本机信息唤出原生设置。<br>Issue称安装成功，未逐项写出调试与授权界面。 | 仅能确认隐藏设置入口，不补写未记录的点击次数与后续菜单。<br>[来源1](https://github.com/pengchujin/aerial-tv/issues/120) |
| T12 海信<br>65D62QH／MT9653 | Android 11；JuUI 9.0.10.845；RP1A.200720.011 | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | 原报告确认USB调试开关会开放网络5555；另有无线调试配对页。<br>USB调试→5555无授权框；另一条路径为动态端口配对。 | 未完整写出如何解锁开发者选项；USB物理口在该机不枚举ADB。该机ro.adb.secure=0不能套用到其他海信。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/hisense-65d62qh.md) |
| T13 海信<br>HiSmartTV A4 | Android 9；固件未给 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 设置→系统→关于→Build连续点击；开发者选项→USB／网络调试。<br>TCP 5555；首次RSA授权。 | 该报告电视USB-A为主机端，不可据此推断USB ADB可用。仓库未找到许可证。<br>[来源1](https://github.com/harshmandan/hisense-a4/blob/main/README.md) |
| T37 海信 Fire TV<br>55QD65NF；AFTHA004 | Fire OS 7.7.1.7／Android 9；PS7717.5741N | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 设置→My Fire TV→About→设备名称7次→Developer Options→ADB Debugging。<br>TCP 5555；首次电视授权。 | Fire OS分支，不与国内VIDAA入口混用。<br>[来源1](https://github.com/humblemuzzu/dumbtv/blob/755991b17a66e753e82f7252368c8850c08fdc71/catalog/devices/fireos-hisense-aftha004.yaml) |

<a id="skyworth"></a>

### 创维／酷开

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T14 创维／酷开<br>14K／8S61／sky628_8s61 | Android 4.4.2；018.010.130；2018-10-13 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 本机信息→`↑↑↓↓←→←→`→停约2秒→工厂菜单其他设置→Adbd Enable由0改1。<br>网络ADB，无授权框。 | 报告重启后保持；准确方向为左右左右，不是左左右右。 原报告adb reboot会关机，不能当作正常重启。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/coocaa-14k-8s61.md) |
| T15 创维／酷开<br>K5C／8R40_K5C | Android 8.0；OPR6.170623.012 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 设置→基本设置→设备信息→更多信息→`↑↑↓↓←→←→`→工厂ADB开关。<br>TCP 5555。 | 没有普通开发者选项；关于页及厂商设置组件是入口的一部分。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/skyworth-8r40-k5c.md) |
| T17 创维／酷开<br>品牌指引，未限定型号 | 适用固件未限定 | B 品牌／平台指引<br>完整入口<br>Quiet TV未验证 | 本机信息页缓慢按`↑↑↓↓←→←→`；另一分支为关于→软件版本7次→开发者选项→USB调试。<br>端口及授权依固件。 | 不同界面分支，不保证每台都适用。<br>[来源1](https://github.com/quicktvui/quicktvui-next/blob/10a21cc05118d396833ccebee5828f0c0de0ba48/docs/zh-CN/guide/device/skyworth.md) |
| T47 创维<br>7T861_A23 | Android 10／API 29；MT9638；QQ2A.200305.004.A1／eng.nobody.20241225.120706 | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | 来源用过网络ADB，未完整记录开启菜单。<br>TCP 5555，无授权框。 | 只保留连接证据，不提高其他创维暗码的验证等级。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/skyworth-7t861-a23.md) |

<a id="prima"></a>

### 夏华 PRIMA

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T16 夏华 PRIMA<br>4S875_Z25X | Android 8；2020-11-17固件 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 本机信息→`↑↑↓↓←→←→`→工厂通用设置→ADB开启，返回退出。<br>报告立即可连，无需重启，无授权框。 | Issue提交者报告，未由本次研究或维护者复现。<br>[来源1](https://github.com/sanshengai/sansheng-tv-adb/issues/2) |

<a id="konka"></a>

### 康佳／KKTV

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T18 康佳／KKTV<br>品牌指引，未限定型号 | 未限定 | B 品牌／平台指引<br>入口不完整<br>Quiet TV未验证 | TV界面按菜单→输入2008→Factory Menu。<br>原开发者文档未给出ADB子菜单。 | 进入工厂模式不能直接视为ADB已开启。<br>[来源1](https://github.com/quicktvui/quicktvui-next/blob/10a21cc05118d396833ccebee5828f0c0de0ba48/docs/zh-CN/guide/device/konka.md) |
| T19 康佳<br>B50U；YIUI 7／8线索 | 完整固件未给 | C 汇总线索<br>待验证线索<br>Quiet TV未验证 | 汇总库称模拟电视→菜单→2008→远程调试→同时开启ADB和ADBD。<br>端口按实际固件确认。 | 来自汇总库所引论坛／博客，未找到同型号完整一手复现。<br>[来源1](https://github.com/sanshengai/sansheng-tv-adb/blob/main/data/brands/konka.json) |
| T20 康佳<br>75R5；me柚OS线索 | 完整固件未给 | C 汇总线索<br>待验证线索<br>Quiet TV未验证 | 汇总库称模拟电视按菜单→蓝牙键盘输入2008→其他选项→ADB。<br>未独立核实。 | 键盘替代方案只是单源线索，不保证适用其他康佳。<br>[来源1](https://github.com/sanshengai/sansheng-tv-adb/blob/main/data/brands/konka.json) |

<a id="changhong"></a>

### 长虹／CHiQ

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T21 长虹／CHiQ<br>品牌指引，未限定型号 | Android适用固件 | B 品牌／平台指引<br>入口不完整<br>Quiet TV未验证 | 菜单→设置→关于电视→版本号／Build连续点击，通常7次，解锁开发者选项。<br>文档没写最终调试开关及端口。 | 只确认文档提供了解锁开发者选项说明。<br>[来源1](https://github.com/quicktvui/quicktvui-next/blob/10a21cc05118d396833ccebee5828f0c0de0ba48/docs/zh-CN/guide/device/ch.md) |
| T22 长虹<br>旧款CHiQ系列线索 | 型号／固件未独立核实 | C 汇总线索<br>待验证线索<br>Quiet TV未验证 | 汇总库有0816工厂入口，以及从情景模式标准项进入软键盘的分支。<br>尚缺完整的型号、固件及ADB开关证据。 | 只找到暗码不等于可获得ADB。<br>[来源1](https://github.com/sanshengai/sansheng-tv-adb/blob/main/data/brands/changhong.json) |
| T23 长虹／CHiQ<br>58D4P／ZLM97GiT | Android 9；PPR2.180905.006.A1／V2.00203；ZLM97GiT-V2.00203-EMMC，2025-09-15 | A 上游型号报告<br>连接受阻<br>Quiet TV未验证 | 未找到可复现的开发者或工厂ADB入口；网络ADB有AUTH响应但授权未闭环。<br>来源称网络授权受阻。 | 同篇后续shell内容与初始授权之间有衔接缺口，不据此宣称能从未开启状态连通。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/changhong-58d4p.md) |

<a id="letv"></a>

### 乐视

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T24 乐视<br>Q85／CV560-C75 | 菜单报告Android 12/S；2024-11-12 16:40:48 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 按信号源并停留选择浮窗→2580→工厂其他选项→ADB调试ON→断电重启。<br>TCP 5555连接成功。 | 浮窗状态有效，HDMI全屏输入无效。贡献者实机报告，维护者明确未复现。<br>[来源1](https://github.com/sanshengai/sansheng-tv-adb/blob/main/references/case-letv-q85.md) |
| T25 乐视<br>品牌指引，未限定型号 | 未限定 | B 品牌／平台指引<br>完整入口<br>Quiet TV未验证 | 信号源键→2580→工程界面其他选项→底部ADB调试开启→重启。<br>端口按固件。 | 不能外推所有旧乐视。<br>[来源1](https://github.com/quicktvui/quicktvui-next/blob/10a21cc05118d396833ccebee5828f0c0de0ba48/docs/zh-CN/guide/device/le.md) |

<a id="haier"></a>

### 海尔／模卡 MOOKA

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T26 海尔／模卡 MOOKA<br>品牌指引，未限定型号 | 定制Android等适用固件 | B 品牌／平台指引<br>完整入口<br>Quiet TV未验证 | 按电视机身信号源键，菜单出现后遥控器`←←↑→`→海尔工厂菜单→ADB调试；部分为Other Option→adb debug。<br>端口／授权依实机。 | 须区分机身信号源键与遥控器操作上下文。<br>[来源1](https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/adb-by-brand.md) [来源2](https://github.com/quicktvui/quicktvui-next/blob/10a21cc05118d396833ccebee5828f0c0de0ba48/docs/zh-CN/guide/device/haier.md) |
| T27 海尔／模卡 MOOKA<br>M638_MOOKA_UD；U55A5M1P1固件标识 | YunOS／Android 4.4.4；L_MST6A638_MOOKA_U55A5M1P1；2020-08-03 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 设置→关于→Build number连续点击→海尔工厂菜单→ADB调试开关。<br>TCP 5555；ro.adb.secure=0，无授权框。 | 原文没给精确点击次数，采证时未重新进入工厂菜单；保留为拥有者提供的路径。YunOS不能一概判为无ADB。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/haier-m638-mooka-ud-yunos44.md) |
| T46 海尔<br>HRA962X2_2G_ALI6G_A，中国版 | Android 9／API 28；9.0.0-RS-20230315.0915；PPR1.180610.011.20220620 | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | 原记录已开启ADB，未提供初始菜单路径。<br>TCP 5555，无授权框。 | 不能把品牌通用方向键路线当成本机实测。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/haier-hra962x2-ali6g-cn.md) |

<a id="huawei"></a>

### 华为智慧屏

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T28 华为智慧屏<br>开发者文档未限定型号 | 存在USB调试和启用ADB选项的固件；版本边界不明 | B 品牌／平台指引<br>完整入口<br>Quiet TV未验证 | 设置→关于本机→版本号7次→系统和更新→开发人员选项→USB调试→启用ADB。<br>原页未给端口与授权过程。 | 不能外推所有智慧屏、MateTV或荣耀。HDB／HDC不等于ADB。<br>[来源1](https://github.com/quicktvui/quicktvui-next/blob/10a21cc05118d396833ccebee5828f0c0de0ba48/docs/zh-CN/guide/device/huawei.md) |

<a id="oppo"></a>

### OPPO

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T29 OPPO<br>S1；其他型号仅品牌参考 | ColorOS TV；固件未记录 | B 品牌／平台指引<br>完整入口<br>Quiet TV未验证 | 设置→系统→关于本机→设备型号→`↑↑→→`后确定，按提示完成→系统开发者选项→USB／ADB调试。<br>IP见网络设置，按实际开关连接。 | Aerial来自用户反馈，未由维护者逐机验证；K9安装成功不证明开启路径相同。<br>[来源1](https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/adb-by-brand.md) |

<a id="sony"></a>

### 索尼 BRAVIA

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T30 索尼 BRAVIA<br>Android TV通用指引，未限定型号 | 部分补充报告为Android 9 | B 品牌／平台指引<br>完整入口<br>Quiet TV未验证 | 设置→设备偏好设置→关于→内部版本号Build7次→开发者选项→USB调试或网络调试。<br>只有无线调试配对入口时用配对与连接两个端口。 | 点Build而非Android版本；新固件不能默认直连5555。<br>[来源1](https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/adb-by-brand.md) [来源2](https://github.com/therealhoodboy/skinny-bravia/blob/main/README.md) |
| T31 索尼 BRAVIA<br>K-75XR50／AE2，中国版 | Android 14；UKN4.250422.001／603100 | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | 原记录已有无线配对；用当前mDNS端口连接，已授权会话中可执行tcpip 5555。<br>配对保留，端口每次重启改变；重启后5555关闭。 | 未逐项记录从原始设置开始的解锁菜单。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/sony-k75xr50-ae2-cn.md) |
| T32 索尼 BRAVIA<br>K-85XR70／AE2 | Android 14／API 34，中国CNA；114.602.170.1 | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | 已有无线配对成功记录；需使用当前连接端口。<br>报告配对保留，但每次重启需重新取得连接端口。 | 具体解锁开发者选项过程未完整提供。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/sony-k85xr70-ae2.md) |

<a id="philips"></a>

### 飞利浦／TP Vision

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T33 飞利浦／TP Vision<br>55OLED705/12、43PUS7304/12、55PUS7334/12；TPM191E | Android 12；PH9M_EA_5599:12/STT2.230526.001/4215 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 所有设置→Android设置→设备偏好设置→关于→Build7次→开发者选项→USB debugging。<br>TCP 5555；首次电视授权；该记录无需配对码。 | 目录列出这组型号，菜单标签未逐屏重新核对，不是三台独立复验。不适用Saphi／Titan。<br>[来源1](https://github.com/humblemuzzu/dumbtv/blob/755991b17a66e753e82f7252368c8850c08fdc71/catalog/devices/androidtv-philips-tpm191e.yaml) |
| T34 飞利浦<br>65OLED873；TPM171E，2018款 | Android TV；完整固件未给 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 关于页Build连续点击至开发者模式，作者记录5次；开启USB调试。<br>首次授权有时不弹，原作者连接成功不稳定。 | 旧固件个人笔记，仓库未找到许可证。Home Assistant另记部分Philips首次授权须Wi-Fi，未指明本型号。<br>[来源1](https://github.com/yath/tpm171e/blob/master/README.md) [来源2](https://github.com/home-assistant/home-assistant.io/blob/current/source/_integrations/androidtv.markdown) |

<a id="toshiba"></a>

### 东芝／东芝 Fire TV

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T35 东芝<br>55Z500QF，中国海信平台 | Android 11／API 30；T963D4；RD2A.211001.002／07.02.70.202503211952 | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | 原用户通过U盘安装自制辅助APK，唤出原生开发设置后开启调试。<br>TCP 5555；RSA授权。 | APK未公开，多个Intent中哪个成功未记录，不能承诺通用开启器可替代。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/toshiba-55z500qf-cn.md) |
| T36 东芝 Fire TV<br>50LF621U19、55LF711U20、43C350NU；AFTKMST12 | Fire OS；目录NS6708／NS6711等 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 设置→Device & Software→About→Your TV7次→返回Developer Options→ADB Debugging。<br>TCP 5555；首次电视授权。 | 这些是目录型号范围，不是三台独立复验；不适用于国行其他系统东芝。<br>[来源1](https://github.com/humblemuzzu/dumbtv/blob/755991b17a66e753e82f7252368c8850c08fdc71/catalog/devices/fireos-toshiba-aftkmst12.yaml) |

<a id="insignia"></a>

### Insignia Fire TV

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T38 Insignia Fire TV<br>NS-50F301NA24／F30；AFTDCT31 | Fire OS；PS7717.5741N | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | Device & Software→About→设备名称连续点击，报告已是开发者，仍需另找并开启ADB Debugging。<br>目录有网络ADB使用记录。 | 未完整记录开关位置。Home Assistant说部分F30先登录Amazon才出现开发者选项，不代表全系。<br>[来源1](https://github.com/humblemuzzu/dumbtv/blob/755991b17a66e753e82f7252368c8850c08fdc71/catalog/devices/fireos-insignia-aftdct31.yaml) [来源2](https://github.com/home-assistant/home-assistant.io/blob/current/source/_integrations/androidtv.markdown) |

<a id="oneplus"></a>

### OnePlus

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T39 OnePlus<br>TV Y Series／shibuya | Android TV 11；RTO9.231122.002 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | Settings→Device Preferences→About→Build，按OK至开发者提示，报告为5次→Developer options→USB debugging。<br>Wi-Fi TCP 5555；首次Allow USB debugging授权。 | 以屏幕提示为准，固定7次不是唯一成功标准。<br>[来源1](https://github.com/humblemuzzu/dumbtv/blob/755991b17a66e753e82f7252368c8850c08fdc71/catalog/devices/androidtv-oneplus-y-series.yaml) |

<a id="sharp"></a>

### 夏普／旧联想平台

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T40 夏普<br>2T-C32GH3000I，印度尼西亚 | Google TV 14；UKN6.260116.061 | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | 记录只有Wireless debugging入口，先配对连接，之后可选tcpip 5555。<br>初始5555关闭；配对与连接各用自己的端口。 | 初始解锁菜单没逐项记录；不能移植国行旧夏普路径。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/examples/sharp-2t-c32gh3000i/debloat.txt) |
| T41 夏普／联想平台<br>LCD-80LX850A；ideatv | Android 4.0.4；固件未完整记录 | A 上游型号报告<br>默认开启<br>Quiet TV未验证 | 作者记录出厂网络ADB已开启，无需找开关。<br>TCP 5555，无授权框。 | 只对应该台；能连不等于能改系统，也不能证明所有联想默认开。<br>[来源1](https://github.com/sanshengai/sansheng-tv-adb/blob/main/data/brands/sharp-cn.json) [来源2](https://github.com/sanshengai/sansheng-tv-adb/blob/main/data/brands/lenovo.json) |

<a id="super-general"></a>

### Super General

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T42 Super General<br>55英寸；TP.SK708D.PC821／Amlogic T972；零售型号未给 | Android 9；CVTE板卡 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 先选TV或HDMI输入→MENU→迅速输入1147→Factory DEBUG→ADB ENABLE设ON→Back。<br>TCP 5555；报告重启后保持。 | 不是所有1147工厂菜单都有ADB。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/examples/super-general-cvte-t972/README.md) |

<a id="pptv"></a>

### PPTV

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T43 PPTV<br>p34a4p | Android 5.1.1／API 22；LMY47V.20251117 | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | 报告要求每次完整开机后重新开启network debugging。<br>TCP 5555，无授权框。 | 缺少从设置首页到开关的完整路径。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/pptv-p34a4p.md) |

<a id="whaley"></a>

### 微鲸

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T44 微鲸<br>WTV55K1 | Android 5.0.2／API 21；HELIOSR-02.08.03.1849508；WUI 02.08.03.21，2018-12-07 | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | 来源已有网络ADB会话，未记录初次开启步骤。<br>TCP 5555。 | 只能证明可用ADB，不编造微鲸通用暗码。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/whaley-wtv55k1.md) |

<a id="panasonic"></a>

### 松下

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| T45 松下 Panasonic<br>4KTV-3MUP，台湾版 | Google TV 14；UKR1.260308 | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | 有网络ADB证据，未完整记录解锁。<br>需按实机核对是否配对。 | 松下有多种系统，不能只按品牌选路线。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/examples/panasonic-4ktv-3mup/README.md) |

<a id="projectors"></a>

## 投影仪：极米、坚果、Wanbo、当贝

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| P01 极米<br>Z7X高亮版；另有指定GMUI固件报告 | Android 9；GMUI 6.11.101.01_499／PG0017V6.12.68 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | U盘安装code-ant/z7x-adb工具，打开并启用ADB。<br>适用固件启动adbd并开放5555。 | 作者验证Z7X；工具写系统属性的能力依固件权限，非通用Android能力。不开开机恢复时，重启不会自动保持。<br>[来源1](https://github.com/code-ant/z7x-adb/blob/main/README.md) [来源2](https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/xgimi-z7x.md) |
| P02 极米<br>H3／m7221 ironman | Android 8；v1.15.93 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 手机下载z7x-adb APK，经极米手机App上传安装到投影仪，打开工具。<br>报告TCP 5555成功。 | Aerial于2026-10-26有该台成功记录；工具作者表中H3仍待验证，不能概括全系H3成功。<br>[来源1](https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/xgimi-h3.md) [来源2](https://github.com/code-ant/z7x-adb/blob/main/README.md) |
| P03 极米<br>H3S、Z9X、M1、Z6、Z6X、Z6X Pro、Z8X | 未逐型号给出固件 | B 品牌／平台指引<br>待验证线索<br>Quiet TV未验证 | z7x-adb兼容表标注用户反馈可用，采用安装工具开启路线。<br>按实际固件与工具核对。 | 不是逐固件复验记录；H2S／Z4X等原表待验证型号不算成功。<br>[来源1](https://github.com/code-ant/z7x-adb/blob/main/README.md) |
| P04 坚果 JMGO<br>N5s Pro／K990CN | LunaOS／Android 11；1.1.31.7；RP1A.200720.011 | A 上游型号报告<br>完整入口<br>Quiet TV未验证 | 设置→关于本机→系统版本→反复确定，最多约12次，直到ADB提示。<br>TCP 5555，无授权框。 | 旁边console调试不是网络ADB必需项；该固件不适用普通Build7次路线。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/jmgo-n5s-pro.md) |
| P05 Wanbo<br>T6R Max | Android 9；TVOS-04.16.031.01.12 | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | 原记录开启USB与网络调试，但没完整写菜单层级。<br>端口5877，无授权框。 | 不是5555，不能推广其他Wanbo型号。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/wanbo-t6r-max.md) |
| P06 当贝<br>F5 | 原反馈未限定固件 | B 品牌／平台指引<br>待验证线索<br>Quiet TV未验证 | Aerial用户反馈使用BuddhaYi/android-tv-developer-enabler开启调试。<br>缺型号固件与工具分支完整执行记录。 | 工具多为跳转已有设置页，部分分支需额外权限，不是通用绕过。不能先依赖ADB安装它再声称解决初次ADB。<br>[来源1](https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/adb-by-brand.md) [来源2](https://github.com/BuddhaYi/android-tv-developer-enabler/blob/main/src/com/gelab/devenab/MainActivity.java) |
| P07 坚果 JMGO<br>G9S | Android 9；1.1.52 | A 上游型号报告<br>入口不完整<br>Quiet TV未验证 | 原记录已有ADB，未完整给开启入口。<br>TCP 5555，无授权框。 | 不能直接移植N5s Pro连按次数。<br>[来源1](https://github.com/Gathin23/cleanyourtv/blob/57c00a40dafe040b0be1f32086e96c05a1a37054/docs/devices/jmgo-g9s.md) |

<a id="boxes"></a>

## 机顶盒：天猫、腾讯、Shield、Google／onn

| 编号／型号或平台 | 系统／固件范围 | 来源与验证状态 | 上游开启描述／连接行为 | 限制与出处 |
| --- | --- | --- | --- | --- |
| B01 天猫魔盒<br>文档未限定型号 | 未限定系统／固件 | B 品牌／平台指引<br>完整入口<br>Quiet TV未验证 | 我的→设置→通用设置→开发者模式→开启。<br>文档称启用ADB；端口／授权未给。 | 不代表所有天猫魔盒及YunOS固件。<br>[来源1](https://github.com/quicktvui/quicktvui-next/blob/10a21cc05118d396833ccebee5828f0c0de0ba48/docs/zh-CN/guide/device/tmall.md) |
| B02 腾讯极光<br>文档未限定型号 | Android；版本未给 | B 品牌／平台指引<br>入口不完整<br>Quiet TV未验证 | 设置→关于本机→安卓原生设置→关于→连续点击Android TV操作系统版本对应条目→开发者选项→启用。<br>原文没补齐USB调试及连接步骤。 | 中文版本条目需以实机Build项核对；启用开发者选项不自动等于开启ADB。<br>[来源1](https://github.com/quicktvui/quicktvui-next/blob/10a21cc05118d396833ccebee5828f0c0de0ba48/docs/zh-CN/guide/device/tencent.md) |
| B03 NVIDIA Shield<br>TV／TV Pro系列 | Android TV；未逐固件限定 | C 汇总线索<br>待验证线索<br>Quiet TV未验证 | 汇总库：关于→Build连续点击→开发者选项→Network debugging。<br>端口按页面。 | 本次未逐台核查2015／2017／2019型号，保留平台线索。<br>[来源1](https://github.com/sanshengai/sansheng-tv-adb/blob/main/data/brands/shield.json) |
| B04 Google／onn<br>Chromecast with Google TV、Google TV Streamer、onn 4K | Google TV；未逐型号固件限定 | C 汇总线索<br>待验证线索<br>Quiet TV未验证 | 汇总库：关于→Android TV OS build连续点击→开发者选项，再按实机使用USB调试或无线配对。<br>固定5555或动态配对端口由固件决定。 | 系列目录不是逐机验证。<br>[来源1](https://github.com/sanshengai/sansheng-tv-adb/blob/main/data/brands/googletv-devices.json) |

<a id="other-systems"></a>

## 其他系统与未取得完整入口的范围

| 范围 | 资料结论和边界 |
| --- | --- |
| 三星Tizen | 使用SDB，不是ADB；开发模式不让Android APK流程适用。[Tizen开发说明](https://github.com/flutter-tizen/flutter-tizen/blob/main/doc/configure-device.md) |
| LG webOS | 使用Developer Mode应用、webOS CLI／SSH，不是ADB。[LG官方说明](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app) |
| Roku、SmartCast、Linux VIDAA、Philips Saphi／Titan、松下非Android系统 | 与同品牌Android／Google TV／Fire OS设备分开。本次未纳入可复现ADB方法；先确认系统，不能只按品牌排除或套用。 |
| 荣耀智慧屏／华为MateTV | 本次未取得型号、固件及成功开启步骤齐全的ADB报告。不是“永远无法开启”；不把HDC／HDB记作ADB。 |
| KTC、Realme、Nokia、Thomson等未单列品牌 | 本次没有足以单列的型号与完整开启过程，不编造暗码；若是Android／Google TV，仍须核对具体固件菜单。 |
| 非智能电视／显示器 | 外接盒子的ADB属于盒子，不等于电视本体提供ADB。 |

## 补充线索及来源限制

[sansheng-tv-adb入口库](https://github.com/sanshengai/sansheng-tv-adb/tree/main/data/brands)在本次采集时含23个品牌／平台分组、83个系列、95条入口记录。它包含地区变体、机顶盒、非ADB系统、默认开启和不完整步骤；型号示例不是逐机实测，95条记录不等于95种可用方法。本页选用其中有明确用途的报告或线索并保留层级，其余仅作定向检索入口，不整体转成匹配／执行规则。

主要逐机资料来自[cleanyourtv](https://github.com/Gathin23/cleanyourtv)、[dumbtv](https://github.com/humblemuzzu/dumbtv)，品牌说明来自[QuickTVUI](https://github.com/quicktvui/quicktvui-next)、[Aerial](https://github.com/pengchujin/aerial-tv)等；每条仍以行内出处为准。固定commit链接保留所读版本；分支页与Issue按采集日期解释，使用前核对后续更新。转载或衍生项目的同一内容不算独立交叉验证。

表内T07、T13、T34以及T30的skinny-bravia补充链接属于未找到许可证的公开资料，不能把“公开可读”等同开源授权；Home Assistant文档为CC BY-NC-SA 4.0。这里只归纳入口事实并给出处，不引入这些仓库源码或安装包。其他已核对项目的MIT／Apache许可也不代表设备兼容保证。
