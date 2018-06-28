# AndroidChromium
![](https://github.com/JackyAndroid/AndroidChromium/blob/master/app/src/main/res/mipmap-xhdpi/app_icon.png)

### 简介
* 谷歌浏览器安卓版源码项目
* 本项目是世界级的安卓架构
* 理清本项目业务逻辑完全可以胜任国内一线公司工程师
* 本项目会长期跟进并升级谷歌浏览器内核版本，欢迎star

### 注意事项
如果使用的是AndroidStudio 2.0以上版本且开启instant run功能，建议关闭后再进行调试（instant run会修改首先启动的Application导致chrome provider context 引用错误导致crash）

### 效果图
![](https://github.com/JackyAndroid/AndroidChromium/blob/master/screenshots/screenshot1.jpg)  ![](https://github.com/JackyAndroid/AndroidChromium/blob/master/screenshots/screenshot2.jpg)  ![](https://github.com/JackyAndroid/AndroidChromium/blob/master/screenshots/screenshot3.jpg)

![](https://github.com/JackyAndroid/AndroidChromium/blob/master/screenshots/screenshot4.jpg)  ![](https://github.com/JackyAndroid/AndroidChromium/blob/master/screenshots/screenshot5.jpg)  ![](https://github.com/JackyAndroid/AndroidChromium/blob/master/screenshots/screenshot6.png)

### 以下为升级Chromium内核步骤
---

### 准备

参考[官方](https://chromium.googlesource.com/chromium/src/+/master/docs/android_build_instructions.md)及其他教程编译通过chromium源码，并能生成chrome.apk

### 目的

使用AndroidStudio开发环境调试Chromium Android UI层。

### 构建思路

1.	采用Android Studio作为开发环境，从Chromium for Android抽取chrome模块的源码，加入Android project。
2.	native代码在chromium环境中build，作为so加入Android project
3.	基础模块(base, content, net等)在chromium环境build为jar包，加入Android project
4.	content, chrome, ui等模块的资源文件加入Android library project

### 资源文件为什么不能直接都添加到Android project呢？

因为命名空间的原因，比如content模块的资源的命名空间为org.chromium.content, chrome模块的资源的命名空间为		org.chromium.chrome，所以需要建立不同的Android library project, 指定不同的包名。

### 本项目和源码目录对应关系

app/libs ----------- chromium/src/out/gnbuild/lib.java

app/src/main/aidl ----------- chromium/src/chrome/android/java/src/android/support/customtabs/*.aidl

app/src/main/assets ----------- chromium/src/out/gnbuild/

app/src/main/java ------------ chromium/src/chrome/android/java/src

app/src/main/jniLibs ----------- chromium/src/out/gnbuild/

app/src/main/res（app module 初始化资源）

libraries/androidmedia_res ----------- chromium/src/third_party/android_media/java/res

libraries/chrome_res ----------- chromium/src/chrome/android/java/res && chromium/src/chrome/android/java/res_chromium

libraries/content_res ----------- chromium/src/content/public/android/java/res

libraries/datausagechart_res --------- chromium/src/third_party/android_data_chart/java/res

libraries/ui_res ---------- chromium/src/ui/android/java/res

### 内核升级注意事项

1. pak和dat等文件需要加入到assets目录，而且不能压缩
2. aidl文件加入到main/aidl下
3. 目前构建的chromium版本是55.0.2883.99
4. 因为某些java文件是通过C文件编译生成，只存在chromium/src/out 目录下或存在于out下jar包中。如果按以上对应关系升级版本缺失文件，请到out/目录下去搜索，根据命名空间添加相应文件。还有一些临时生成的xml资源文件也需要从out/目录下去拷贝到相应的资源模块。

### 感谢

本项目灵感来自于365browser

### License

    Copyright 2016 Jacky Wang<jacky.android@foxmail.com>

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
