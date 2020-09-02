# AndroidChromium

[中文文档](https://github.com/JackyAndroid/AndroidChromium/blob/master/README-CN.md)

![](https://github.com/JackyAndroid/AndroidChromium/blob/master/app/src/main/res/mipmap-xhdpi/app_icon.png)

### Brief Introduction
* Google browser android version of the source program
* This project is a world-class android architecture
* Clarify the project business logic can completely fit for domestic company engineer
* This project will follow up and update Google browser kernel version for a long time, welcome to the star

### Notice
If you are using AndroidStudio above 2.0 version and open instant run function, suggested to debug after closing (instant run will modify on the Application of the result in chrome provider context reference error)

### Screenshots
![](https://github.com/JackyAndroid/AndroidChromium/blob/master/screenshots/screenshot1.jpg)  ![](https://github.com/JackyAndroid/AndroidChromium/blob/master/screenshots/screenshot2.jpg)  ![](https://github.com/JackyAndroid/AndroidChromium/blob/master/screenshots/screenshot3.jpg)

![](https://github.com/JackyAndroid/AndroidChromium/blob/master/screenshots/screenshot4.jpg)  ![](https://github.com/JackyAndroid/AndroidChromium/blob/master/screenshots/screenshot5.jpg)  ![](https://github.com/JackyAndroid/AndroidChromium/blob/master/screenshots/screenshot6.png)

### The upgrade Chromium kernel steps are as follows
---

### Preparation

Refer to [official](https://chromium.googlesource.com/chromium/src/+/master/docs/android_build_instructions.md) and other tutorial compile chromium source code, and can generate chrome apk

### purpose

Using AndroidStudio debugging Chromium Android

### The build process

1.	Android Studio as a development environment, from Chromium for Android from chrome module source code, to join the Android project.
2.	Native code in the chromium environment to build, as so file to join the Android project
3.	Basic module (base, the content, net, etc.) in the chromium environment to build into a jar package, then add to the Android project
4.	content, chrome, UI modules such as resource file to join an Android library project

### Why can't a resource file are directly added to the Android project?

Because namespace problems, such as the content of the module resources namespace is org.chromium.content,the chrome module namespace is org.chromium.chrome, so need to build different Android library project, specify different package name.

### Directory corresponding relation

app/libs ----------- chromium/src/out/gnbuild/lib.java

app/src/main/aidl ----------- chromium/src/chrome/android/java/src/android/support/customtabs/*.aidl

app/src/main/assets ----------- chromium/src/out/gnbuild/

app/src/main/java ------------ chromium/src/chrome/android/java/src

app/src/main/jniLibs ----------- chromium/src/out/gnbuild/

app/src/main/res（app module init res）

libraries/androidmedia_res ----------- chromium/src/third_party/android_media/java/res

libraries/chrome_res ----------- chromium/src/chrome/android/java/res & chromium/src/chrome/android/java/res_chromium

libraries/content_res ----------- chromium/src/content/public/android/java/res

libraries/datausagechart_res --------- chromium/src/third_party/android_data_chart/java/res

libraries/ui_res ---------- chromium/src/ui/android/java/res

### The kernel upgrade matters needing attention

1.	Pak and dat files need to be added to the assets directory, and cannot be compressed
2.	Aidl files added to the main/aidl
3. The current build chromium version is 55.0.2883.99
4. Because some Java file is through the C compiler generated, there is the chromium/src/out directory or exist in the jars.If according to corresponding relation between the above updated version missing files, please go to the out/directory search, add corresponding files according to the namespace.There are some temporary generated XML resource file also need the out/directory on copy to the corresponding resource module.

### Thanks

The project is inspiration from the 365 browser

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
