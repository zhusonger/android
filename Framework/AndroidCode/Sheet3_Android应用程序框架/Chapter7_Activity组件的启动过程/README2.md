## Step19: ActivityThread.main

 __一个程序至少有一个进程,一个进程至少有一个线程__ 
 
简化过程，关键的就是这么几句，prepareMainLooper会创建一个Looper实例，这个进程内的那个初始线程内，就是我们常说的主线程，创建的这个Looper实例，就是在主线程创建的，并作为静态变量保存在ActivityThread中。然后开始循环。这个跟我们自己实现一个线程处理Handler一样，对这方面不了解的。可以再去看一下Handler与Looper的实现。

这里简单说明: 

* 在activity等这些ui类中，创建的Handler在不指定Looper的情况下，调用的当前线程的Looper, 那ui是在主线程创建的，那这个Looper就是主线程的Looper,有消息发送到这个Handler之后，就是在主线程里处理的。

* 在非主线程中，创建的Handler实例的时候，会抛出异常_Can't create handler inside thread that has not called Looper.prepare()_

* Handler的就是个Looper的辅助类，消息处理的核心是在Looper里的，在创建Handler是可以指定Looper的。


```
	public static void main(String[] args) {
        ...

        Looper.prepareMainLooper();

        ActivityThread thread = new ActivityThread();
        thread.attach(false);

        if (sMainThreadHandler == null) {
            sMainThreadHandler = thread.getHandler();
        }

        ...
        Looper.loop();
    }
 ```
 
 到这里为止，我们就把应用程序所在的进程创建起来了。在实例化ActivityThread的时候，有几个参数值得我们关注。
 
```
 final ApplicationThread mAppThread = new ApplicationThread();
 final H mH = new H();
 final ArrayMap<IBinder, ActivityClientRecord> mActivities = new ArrayMap<>();
```

* mAppThread: 应用程序进程的应用端的服务端实现类，在AMS通过这个对象，来远程管理应用程序
* mH: 处理来自AMS的操作，切换到应用程序所在的线程
* mActivities: 记录应用程序的Activity,ActivityClientRecord是应用程序端唯一标识自己的类

 初始化的工作应该在那个attach里，我们继续跟入。
 
 
## Step19: ActivityThread.attach

