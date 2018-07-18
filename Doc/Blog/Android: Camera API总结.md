#一.Camera API
Camera的初始化需要使用静态方法通过API calledCamera.open提供并初始化相机对象

Camera mCamera =  Camera.open();
简单看下Camera类提供的方法

* getCameraInfo(int cameraId, Camera.CameraInfo cameraInfo) 它返回一个特定摄像机信息
* getNumberOfCameras() 它返回限定的可用的设备上的照相机的整数
* lock()它被用来锁定相机，所以没有其他应用程序可以访问它
* release() 它被用来释放在镜头锁定，所以其他应用程序可以访问它
* open(int cameraId) 它是用来打开特定相机时，支持多个摄像机
* enableShutterSound(boolean enabled) 它被用来使能/禁止图像俘获的默认快门声音
* startPreview() 开始预览
* startFaceDetection() 此功能启动人脸检测相机
* stopFaceDetection() 它是用来阻止其通过上述功能启用的脸部检测
* startSmoothZoom(int value) 这需要一个整数值，并调整摄像机的焦距非常顺畅的值
* stopSmoothZoom() 它是用来阻止摄像机的变焦
* stopPreview() 它是用来阻止相机的预览给用户
* takePicture(Camera.ShutterCallback shutter, Camera.PictureCallback
raw, Camera.PictureCallback jpeg) 它被用来使能/禁止图像拍摄的默认快门声音