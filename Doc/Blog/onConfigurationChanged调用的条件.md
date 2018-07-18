# onConfigurationChanged调用的条件

* android:screenOrientation未配置
  这个时候需要手机开启自动旋转，然后手机物理转向，才会调用到onConfigurationChanged
  
* android:screenOrientation配置
  手动调用setRequestedOrientation才会调用onConfigurationChanged，手机物理转向不会调用