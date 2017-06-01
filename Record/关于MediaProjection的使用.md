1. MediaProjection的创建，对于单个应用程序，可以持有一个实例,它本身并不是很占用系统资源
	可以通过adb shell-> top -m 5 -d 1 -s cpu查看cpu的占用,/system/bin/surfaceflinger就是录屏的系统进程

2. 在同一个应用中，可以通过createVirtualDisplay创建多个虚拟display

3. 当虚拟display所关联的surface被销毁时, 需要调用虚拟display的release, 否则会报错，如下

06-01 18:01:13.844 647-647/? E/SurfaceFlinger: eglSwapBuffers(0x1, 0x7faeaa3bc0) failed with 0x0000300d
06-01 18:01:13.854 647-647/? E/BufferQueueProducer: [SurfaceView] dequeueBuffer: BufferQueue has been abandoned

虽然不会导致程序崩溃

4. 当其他应用程序请求新的MediaProjection之后，且调用了createVirtualDisplay并未释放，其他的MediaProjection都会失效,
需要处理这个情况，即使对象非空，在调用MediaProjection相关的接口时，需要捕获异常，异常是如下

java.lang.SecurityException: Invalid media projection

会导致程序崩溃

原有MediaProjection创建的虚拟display绑定的surface，不会再有数据刷入。

5. 对于有效的MediaProjection，是可以多次调用createVirtualDisplay来创建多个虚拟display的。

