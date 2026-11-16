# Quiet TV 动态天气组件

此分支仅包含动态天气组件 1.0.0 的资源和打包工具，独立发布于 QuietTV 仓库的 `weather-v1.0.0` 标签。没有 QuietTV 2.0 主程序源码或 APK。

## 使用

组件是天气渲染资源 ZIP，不能直接安装为 Android 应用。需要支持 hostApi1、内置此组件签名公钥并使用独立天气下载地址的 QuietTV 电视客户端。电视须满足 Android 5.0 / API21、GLES3 和非低内存设备条件，实际效果取决于设备 GPU 与固件。下载和校验完成后，用户再选择动态天气壁纸。

## 构建

依赖 Python 3 标准库；签名额外需要 OpenSSL 及仓库外持有的原签名私钥。公开源码不含私钥。

```sh
python3 scripts/build_components.py pack --kind weather --target tv --version 1.0.0 --release-tag weather-v1.0.0 --output output/weather
python3 scripts/build_components.py index --artifact output/weather/QuietTV_tv_weather_1.0.0.json --revision 4 --key <外部私钥路径> --output output/weather/QuietTV_components.json
```

打包过程独占创建文件，不覆盖已有产物。ZIP、逐文件大小与 SHA-256 由同一组资源生成；签名清单仅包含电视天气组件，不包含组网组件。签名公钥位于 `app/src/main/assets/components/public-key.der`。

## 许可

工具源码遵循 [LICENSE](LICENSE)。天气材质来源及许可见 [材质声明](app/src/main/assets/licenses/quiet-weather-materials-CC0.txt) 与 [生成材料](scripts/weather-materials/README.md)。
