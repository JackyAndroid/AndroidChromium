##Chromium源码环境分离AndroidUI层，并构建gradle工程

**目的：**

使用AndroidStudio开发环境调试Chromium Android UI层。

**构建思路：**

1.	采用Android Studio作为开发环境，从Chromium for Android抽取chrome模块的源码，加入Android project。
2.	native代码在chromium环境中build，作为so加入Android project
3.	基础模块(base, content, net等)在chromium环境build为jar包，加入Android project
4.	content, chrome, ui等模块的资源文件加入Android library project

**资源文件为什么不能直接都添加到Android project呢？**

因为命名空间的原因，比如content模块的资源的命名空间为org.chromium.content, chrome模块的资源的命名空间为		org.chromium.chrome，所以需要建立不同的Android library project, 指定不同的包名。


**注意事项：**

1.	chromium项目的源码和资源有些是自动生成的，需要到out目录下去复制
2.	pak和dat等文件需要加入到assets目录，而且不能压缩
3.	aidl文件加入到main/aidl下，android studio会自动处理

**建议&注意事项：**

1.	目前构建的chromium版本是48.0.2554.0，内核为官方版本
2.	如果使用的是AndroidStudio 2.0以上版本且开启instant run功能，建议关闭	后再进行调试（instant run会修改首先启动的Application导致chrome provider context 引用错误导致crash）