# Beauty API Change Log

## 1.0.6
1. Android：美颜处理时添加EGLBase.lock锁，防止在部分机型退出时出现anr的问题
2. Android：相芯美颜添加一个开关，支持选择实时优先还是流畅优先
3. Android：修复相芯纹理+异步处理在部分中端机上出现回帧的问题
4. Android：将美颜库改成动态下载
5. demo层去掉setBeautyPreset调用


## 1.0.5
1. 添加宇宙美颜
2. Android 优化demo美颜资源加载

## 1.0.4.1
1. Android 修复Video Frame Observer没有释放的问题
2. Android 添加 runOnProcessThread api 用于在美颜处理线程里做一些操作，如美颜效果设置等
3. Android 修复商汤在开关美颜时会黑一下的问题

## 1.0.4
1. 适配火山美颜4.6.0版本
2. 适配相芯美颜8.7.0版本
3. Android修复屏幕自动旋转渲染问题
4. 修复线上其他问题

## 1.0.3
1. 添加美颜参数设置弹窗
2. 添加RTC打点上报

## 1.0.2
1. 添加必要日志，去掉无意义重复的日志
2. 适配商汤9.x版本
3. 适配RTC 4.2.2版本
4. 给相芯美颜加上中高低机型适配，判断机型用相芯提供的判断算法
5. 添加镜像模式配置
