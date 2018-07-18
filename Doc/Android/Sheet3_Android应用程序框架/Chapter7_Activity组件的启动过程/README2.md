## Step19: ActivityThread.main

 __一个程序至少有一个进程,一个进程至少有一个线程__ 
 
简化过程，关键的就是这么几句，prepareMainLooper会创建一个Looper实例，这个进程内的那个初始线程内，就是我们常说的主线程，创建的这个Looper实例，就是在主线程创建的，并作为静态变量保存在ActivityThread中。然后开始循环。这个跟我们自己实现一个线程处理Handler一样，对这方面不了解的。可以再去看一下Handler与Looper的实现。

这里简单说明: 

* 在activity等这些ui类中，创建的Handler在不指定Looper的情况下，调用的当前线程的Looper, 那ui是在主线程创建的，那这个Looper就是主线程的Looper,有消息发送到这个Handler之后，就是在主线程里处理的。

* 在非主线程中，创建的Handler实例的时候，会抛出异常 _Can't create handler inside thread that has not called Looper.prepare()_ 

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
在这个方法，我们正式开始走我们熟悉的应用程序启动的一些接口了。这里注意到的是，启动应用程序之后会检测系统的内存使用情况，如果超过了系统的3/4就会开始清理activity了。回去看AMS的调用。

    private void attach(boolean system) {
        sCurrentActivityThread = this;
        // 当前线程不是系统线程，所以是false
        mSystemThread = system;
        if (!system) {
            ViewRootImpl.addFirstDrawHandler(new Runnable() {
                @Override
                public void run() {
                    // 打开jit编译, just in time,即时编译，加快java的执行速度
                    // https://www.ibm.com/developerworks/cn/java/j-lo-just-in-time/
                    ensureJitEnabled();
                }
            });
            android.ddm.DdmHandleAppName.setAppName("<pre-initialized>",
                                                    UserHandle.myUserId());
            RuntimeInit.setApplicationObject(mAppThread.asBinder());
            final IActivityManager mgr = ActivityManagerNative.getDefault();
            try {
                // 这里就是我们经常在Application中重写的方法onCreate方法
                // 以及有时候会调用到的attachBaseContext方法都在这一步调用到
                mgr.attachApplication(mAppThread);
            } catch (RemoteException ex) {
                throw ex.rethrowFromSystemServer();
            }
            // Watch for getting close to heap limit.
            BinderInternal.addGcWatcher(new Runnable() {
                @Override public void run() {
                    if (!mSomeActivitiesChanged) {
                        return;
                    }
                    // 内存超过最大内存的3/4时，就释放一些activity
                    Runtime runtime = Runtime.getRuntime();
                    long dalvikMax = runtime.maxMemory();
                    long dalvikUsed = runtime.totalMemory() - runtime.freeMemory();
                    if (dalvikUsed > ((3*dalvikMax)/4)) {
                        if (DEBUG_MEMORY_TRIM) Slog.d(TAG, "Dalvik max=" + (dalvikMax/1024)
                                + " total=" + (runtime.totalMemory()/1024)
                                + " used=" + (dalvikUsed/1024));
                        mSomeActivitiesChanged = false;
                        try {
                            mgr.releaseSomeActivities(mAppThread);
                        } catch (RemoteException e) {
                            throw e.rethrowFromSystemServer();
                        }
                    }
                }
            });
        } else {
            ...
        }

        // add dropbox logging to libcore
        DropBox.setReporter(new DropBoxReporter());

        // 这个就是我们接触比较少的, onConfigurationChanged的调用，主要是通过根视图的变化来回调
        ViewRootImpl.addConfigCallback(new ComponentCallbacks2() {
            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                synchronized (mResourcesManager) {
                    // We need to apply this change to the resources
                    // immediately, because upon returning the view
                    // hierarchy will be informed about it.
                    if (mResourcesManager.applyConfigurationToResourcesLocked(newConfig, null)) {
                        updateLocaleListFromAppContext(mInitialApplication.getApplicationContext(),
                                mResourcesManager.getConfiguration().getLocales());

                        // This actually changed the resources!  Tell
                        // everyone about it.
                        if (mPendingConfiguration == null ||
                                mPendingConfiguration.isOtherSeqNewer(newConfig)) {
                            mPendingConfiguration = newConfig;

                            sendMessage(H.CONFIGURATION_CHANGED, newConfig);
                        }
                    }
                }
            }
            @Override
            public void onLowMemory() {
            }
            @Override
            public void onTrimMemory(int level) {
            }
        });
    }


## Step20: ActivityManagerService.attachApplication

这里，我们依次调用了绑定应用程序，启动activity组件，启动service组件，启动brocastreceiver组件，以及创建备份功能。我们分别来看。

	@Override
    public final void attachApplication(IApplicationThread thread) {
        synchronized (this) {
            int callingPid = Binder.getCallingPid();
            final long origId = Binder.clearCallingIdentity();
            attachApplicationLocked(thread, callingPid);
            Binder.restoreCallingIdentity(origId);
        }
    }
    
        private final boolean attachApplicationLocked(IApplicationThread thread,
            int pid) {

        ...

        app.makeActive(thread, mProcessStats);
        app.curAdj = app.setAdj = app.verifiedAdj = ProcessList.INVALID_ADJ;
        app.curSchedGroup = app.setSchedGroup = ProcessList.SCHED_GROUP_DEFAULT;
        app.forcingToForeground = null;
        updateProcessForegroundLocked(app, false, false);
        app.hasShownUi = false;
        app.debugging = false;
        app.cached = false;
        app.killedByAm = false;
        
        ...

        try {
            ...

            // 调用应用程序的bindApplication方法
            thread.bindApplication(processName, appInfo, providers, app.instrumentationClass,
                    profilerInfo, app.instrumentationArguments, app.instrumentationWatcher,
                    app.instrumentationUiAutomationConnection, testMode,
                    mBinderTransactionTrackingEnabled, enableTrackAllocation,
                    isRestrictedBackupMode || !normalMode, app.persistent,
                    new Configuration(mConfiguration), app.compat,
                    getCommonServicesLocked(app.isolated),
                    mCoreSettingsObserver.getCoreSettingsLocked());

        } catch (Exception e) {
            ...
            return false;
        }

        ...

        boolean badApp = false;
        boolean didSomething = false;

        // See if the top visible activity is waiting to run in this process...
        if (normalMode) {
            try {
                // 启动activity
                if (mStackSupervisor.attachApplicationLocked(app)) {
                    didSomething = true;
                }
            } catch (Exception e) {
                Slog.wtf(TAG, "Exception thrown launching activities in " + app, e);
                badApp = true;
            }
        }

        // Find any services that should be running in this process...
        if (!badApp) {
            try {
                // 启动service
                didSomething |= mServices.attachApplicationLocked(app, processName);
            } catch (Exception e) {
                Slog.wtf(TAG, "Exception thrown starting services in " + app, e);
                badApp = true;
            }
        }

        // Check if a next-broadcast receiver is in this process...
        if (!badApp && isPendingBroadcastProcessLocked(pid)) {
            try {
                // 启动广播
                didSomething |= sendPendingBroadcastsLocked(app);
            } catch (Exception e) {
                // If the app died trying to launch the receiver we declare it 'bad'
                Slog.wtf(TAG, "Exception thrown dispatching broadcasts in " + app, e);
                badApp = true;
            }
        }

        // Check whether the next backup agent is in this process...
        if (!badApp && mBackupTarget != null && mBackupTarget.appInfo.uid == app.uid) {
            if (DEBUG_BACKUP) Slog.v(TAG_BACKUP,
                    "New app is backup target, launching agent for " + app);
            notifyPackageUse(mBackupTarget.appInfo.packageName,
                             PackageManager.NOTIFY_PACKAGE_USE_BACKUP);
            try {
                // 创建备份相关类
                thread.scheduleCreateBackupAgent(mBackupTarget.appInfo,
                        compatibilityInfoForPackageLocked(mBackupTarget.appInfo),
                        mBackupTarget.backupMode);
            } catch (Exception e) {
                Slog.wtf(TAG, "Exception thrown creating backup agent in " + app, e);
                badApp = true;
            }
        }

        if (badApp) {
            app.kill("error during init", true);
            handleAppDiedLocked(app, false, true);
            return false;
        }

        if (!didSomething) {
            updateOomAdjLocked();
        }

        return true;
    }
    
 
## Step21: ActivityThread.bindApplication
从AMS端调用的绑定应用的接口，会通过H进行线程切换，在主线程中执行。

	public final void bindApplication(String processName, ApplicationInfo appInfo,
                List<ProviderInfo> providers, ComponentName instrumentationName,
                ProfilerInfo profilerInfo, Bundle instrumentationArgs,
                IInstrumentationWatcher instrumentationWatcher,
                IUiAutomationConnection instrumentationUiConnection, int debugMode,
                boolean enableBinderTracking, boolean trackAllocation,
                boolean isRestrictedBackupMode, boolean persistent, Configuration config,
                CompatibilityInfo compatInfo, Map<String, IBinder> services, Bundle coreSettings) {
			
			// 把常用的package,window,alarm服务给应用端，不需要通过远程接口化，就可以直接调用
            if (services != null) {
                // Setup the service cache in the ServiceManager
                ServiceManager.initServiceCache(services);
            }
			
            setCoreSettings(coreSettings);

            AppBindData data = new AppBindData();
            data.processName = processName;
            data.appInfo = appInfo;
            data.providers = providers;
            data.instrumentationName = instrumentationName;
            data.instrumentationArgs = instrumentationArgs;
            data.instrumentationWatcher = instrumentationWatcher;
            data.instrumentationUiAutomationConnection = instrumentationUiConnection;
            data.debugMode = debugMode;
            data.enableBinderTracking = enableBinderTracking;
            data.trackAllocation = trackAllocation;
            data.restrictedBackupMode = isRestrictedBackupMode;
            data.persistent = persistent;
            data.config = config;
            data.compatInfo = compatInfo;
            data.initProfilerInfo = profilerInfo;
            sendMessage(H.BIND_APPLICATION, data);
        }
        
### Step21.1 ActivityThread.handleBindApplication
处理AMS的调用，进程名称修改也是在这一步做的。

	private void handleBindApplication(AppBindData data) {
        // Register the UI Thread as a sensitive thread to the runtime.
        VMRuntime.registerSensitiveThread();
        
        ...
        
        // 修改进程名称
        Process.setArgV0(data.processName);
        android.ddm.DdmHandleAppName.setAppName(data.processName,
                                                UserHandle.myUserId());
        // 添加检测参数
        ...

        // 创建应用context
        final ContextImpl appContext = ContextImpl.createAppContext(this, data.info);
        // 是否去除内存增长限制
        if ((data.appInfo.flags&ApplicationInfo.FLAG_LARGE_HEAP) != 0) {
            dalvik.system.VMRuntime.getRuntime().clearGrowthLimit();
        } else {
            // Small heap, clamp to the current growth limit and let the heap release
            // pages after the growth limit to the non growth limit capacity. b/18387825
            dalvik.system.VMRuntime.getRuntime().clampGrowthLimit();
        }

        // Allow disk access during application and provider setup. This could
        // block processing ordered broadcasts, but later processing would
        // probably end up doing the same disk access.
        final StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskWrites();
        try {
	        // If the app is being launched for full backup or restore, bring it up in
            // a restricted environment with the base application class.
            // 创建应用程序
            Application app = data.info.makeApplication(data.restrictedBackupMode, null);
            mInitialApplication = app;
            // don't bring up providers in restricted mode; they may depend on the
            // app's custom Application class
            if (!data.restrictedBackupMode) {
                if (!ArrayUtils.isEmpty(data.providers)) {
                	// makeApplication会创建好这个provider, 我们这里是为了把应用创建的，传递给AMS
                    installContentProviders(app, data.providers);
                    // For process that contains content providers, we want to
                    // ensure that the JIT is enabled "at some point".
                    mH.sendEmptyMessageDelayed(H.ENABLE_JIT, 10*1000);
                }
            }
            
            ...
            // 调用application的启动过程
            try {
                mInstrumentation.callApplicationOnCreate(app);
            } catch (Exception e) {
                if (!mInstrumentation.onException(app, e)) {
                    throw new RuntimeException(
                        "Unable to create application " + app.getClass().getName()
                        + ": " + e.toString(), e);
                }
            }
        } finally {
            StrictMode.setThreadPolicy(savedPolicy);
        }
    }
    
### Step21.2 LoadedApk.makeApplication
对应用层开发来说，这里就是调用attachBaseContext的时机

	public Application makeApplication(boolean forceDefaultAppClass,
            Instrumentation instrumentation) {
        if (mApplication != null) {
            return mApplication;
        }

        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "makeApplication");

        Application app = null;
        // 这里就是我们定义的应用类了，默认不配置，就是用的Application
        String appClass = mApplicationInfo.className;
        if (forceDefaultAppClass || (appClass == null)) {
            appClass = "android.app.Application";
        }

        try {
            // classloader的创建与加载应用的类都是在这里实现
            java.lang.ClassLoader cl = getClassLoader();
            // 给当前线程是指classloader
            if (!mPackageName.equals("android")) {
                Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                        "initializeJavaContextClassLoader");
                initializeJavaContextClassLoader();
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            }
            // 创建应用的context,resource的创建，contentprovider的创建, 这里创建了很多跟应用相关的内容
            // 不仔细说明，可以自己去大致看一下
            ContextImpl appContext = ContextImpl.createAppContext(mActivityThread, this);
            // 通过classloader创建Application实例，并调用他的attachBaseContext方法
            app = mActivityThread.mInstrumentation.newApplication(
                    cl, appClass, appContext);
            appContext.setOuterContext(app);
        } catch (Exception e) {
            if (!mActivityThread.mInstrumentation.onException(app, e)) {
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                throw new RuntimeException(
                    "Unable to instantiate application " + appClass
                    + ": " + e.toString(), e);
            }
        }
        mActivityThread.mAllApplications.add(app);
        mApplication = app;


        // 这里的最后一段, 会调用[包名.R]的onResourcesLoaded，搜了半天，没找到相关说明
        // R照理来说是aapt生成的，我们也无法定义方法，看注释说这个是library应用里用的
        // Rewrite the R 'constants' for all library apks.
        SparseArray<String> packageIdentifiers = getAssets(mActivityThread)
                .getAssignedPackageIdentifiers();
        final int N = packageIdentifiers.size();
        for (int i = 0; i < N; i++) {
            final int id = packageIdentifiers.keyAt(i);
            if (id == 0x01 || id == 0x7f) {
                continue;
            }

            rewriteRValues(getClassLoader(), packageIdentifiers.valueAt(i), id);
        }

        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);

        return app;
    }

    public ClassLoader getClassLoader() {
        synchronized (this) {
            if (mClassLoader == null) {
                // 具体的就不看了，就是加载apk压缩包，创建一个classloader并加载
                createOrUpdateClassLoaderLocked(null /*addedPaths*/);
            }
            return mClassLoader;
        }
    }


    
### Step21.3 Instrumentation.callApplicationOnCreate

调用应用程序的onCreate方法，到这里为止，我们应用的启动完成了，回头去看AMS后面的调用

    public void callApplicationOnCreate(Application app) {
        app.onCreate();
    }
    
    
    
## Step22: ActivityStackSupervisor.attachApplicationLocked

这里也没什么好看的，就是真正启动我们之前一直在ams中寄存的activity, 名字也挺形象的了，realStartActivityLocked

	boolean attachApplicationLocked(ProcessRecord app) throws RemoteException {
        final String processName = app.processName;
        boolean didSomething = false;
        for (int displayNdx = mActivityDisplays.size() - 1; displayNdx >= 0; --displayNdx) {
            ArrayList<ActivityStack> stacks = mActivityDisplays.valueAt(displayNdx).mStacks;
            for (int stackNdx = stacks.size() - 1; stackNdx >= 0; --stackNdx) {
                final ActivityStack stack = stacks.get(stackNdx);
                if (!isFocusedStack(stack)) {
                    continue;
                }
                ActivityRecord hr = stack.topRunningActivityLocked();
                if (hr != null) {
                    if (hr.app == null && app.uid == hr.info.applicationInfo.uid
                            && processName.equals(hr.processName)) {
                        try {
                        	// 启动根activity
                            if (realStartActivityLocked(hr, app, true, true)) {
                                didSomething = true;
                            }
                        } catch (RemoteException e) {
                            Slog.w(TAG, "Exception in new application when starting activity "
                                  + hr.intent.getComponent().flattenToShortString(), e);
                            throw e;
                        }
                    }
                }
            }
        }
        if (!didSomething) {
            ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
        }
        return didSomething;
    }

## Step23: ActivityStackSupervisor.realStartActivityLocked


    final boolean realStartActivityLocked(ActivityRecord r, ProcessRecord app,
            boolean andResume, boolean checkConfig) throws RemoteException {

        ...
    
        if (andResume) {
            r.startFreezingScreenLocked(app, 0);
            // 显示应用的界面，估计就是在没有显示第一个activity前的那个白屏状态之前的所有窗体显示过程
            // 这个涉及到wms, 先不管
            mWindowManager.setAppVisibility(r.appToken, true);
            // 系统自带统计慢的应用功能，会写入到文件，方便查看，路径是读取的系统环境配置的dalvik.vm.stack-trace-file
            // schedule launch ticks to collect information about slow apps.
            r.startLaunchTickingLocked();
        }

        // Have the window manager re-evaluate the orientation of
        // the screen based on the new activity order.  Note that
        // as a result of this, it can call back into the activity
        // manager with a new orientation.  We don't care about that,
        // because the activity is not currently running so we are
        // just restarting it anyway.
        // 在启动activity之前，会根据activity的配置属性，更新属性
        if (checkConfig) {
            Configuration config = mWindowManager.updateOrientationFromAppTokens(
                    mService.mConfiguration,
                    r.mayFreezeScreenLocked(app) ? r.appToken : null);
            // Deferring resume here because we're going to launch new activity shortly.
            // We don't want to perform a redundant launch of the same record while ensuring
            // configurations and trying to resume top activity of focused stack.
            mService.updateConfigurationLocked(config, r, false, true /* deferResume */);
        }

        ...

        final ActivityStack stack = task.stack;
        try {
            ...
            // 调用应用端去创建activity的类
            app.thread.scheduleLaunchActivity(new Intent(r.intent), r.appToken,
                    System.identityHashCode(r), r.info, new Configuration(mService.mConfiguration),
                    new Configuration(task.mOverrideConfig), r.compat, r.launchedFromPackage,
                    task.voiceInteractor, app.repProcState, r.icicle, r.persistentState, results,
                    newIntents, !andResume, mService.isNextTransitionForward(), profilerInfo);
            ...

        } catch (RemoteException e) {
            ...
        }

        ...

        return true;
    }
    
    
    
## Step24: ActivityThread.scheduleLaunchActivity
AMS调用应用端方法scheduleLaunchActivity，应用端定义了与AMS里的ActivityRecord对应的数据结构ActivityClientRecord。用来在应用端唯一标识一个activity.

    
    // we use token to identify this activity without having to send the
        // activity itself back to the activity manager. (matters more with ipc)
        @Override
        public final void scheduleLaunchActivity(Intent intent, IBinder token, int ident,
                ActivityInfo info, Configuration curConfig, Configuration overrideConfig,
                CompatibilityInfo compatInfo, String referrer, IVoiceInteractor voiceInteractor,
                int procState, Bundle state, PersistableBundle persistentState,
                List<ResultInfo> pendingResults, List<ReferrerIntent> pendingNewIntents,
                boolean notResumed, boolean isForward, ProfilerInfo profilerInfo) {

            updateProcessState(procState, false);

            ActivityClientRecord r = new ActivityClientRecord();

            r.token = token;
            r.ident = ident;
            r.intent = intent;
            r.referrer = referrer;
            r.voiceInteractor = voiceInteractor;
            r.activityInfo = info;
            r.compatInfo = compatInfo;
            r.state = state;
            r.persistentState = persistentState;

            r.pendingResults = pendingResults;
            r.pendingIntents = pendingNewIntents;

            r.startsNotResumed = notResumed;
            r.isForward = isForward;

            r.profilerInfo = profilerInfo;

            r.overrideConfig = overrideConfig;
            updatePendingConfiguration(curConfig);

            sendMessage(H.LAUNCH_ACTIVITY, r);
        }
        
## Step24: ActivityThread.handleLaunchActivity

启动activity，到这里，基本上根Activity的启动其实已经差不多结束了，performLaunchActivity和handleResumeActivity中间还有比较多的细节可以深挖，如果有兴趣可以继续了解。

	private void handleLaunchActivity(ActivityClientRecord r, Intent customIntent, String reason) {
        ...

        // 创建activity实例, 调用onCreate,onStart,onPostCreate过程
        // 在调用onCreate之前，会先创建PhoneWindow，设置主题
        Activity a = performLaunchActivity(r, customIntent);

        if (a != null) {
            r.createdConfig = new Configuration(mConfiguration);
            reportSizeConfigurations(r);
            Bundle oldState = r.state;
            // 调用onStart, onResume, onPostResume
            // 在调用完这一些列方法之后，就会通过WindowManager添加之前创建的PhoneWindow
            // 应用窗口类型为TYPE_BASE_APPLICATION
            handleResumeActivity(r.token, false, r.isForward,
                    !r.activity.mFinished && !r.startsNotResumed, r.lastProcessedSeq, reason);
            ...
        } else {
            ...
        }
    }
    
   
 ---- 
## 结语 
本来还想再写子Activity的启动过程，不过跟根Activity基本是一样的，只是中间少了创建进程，过程反而更简单，就懒得再看了，自己看吧。

在分析这个Activity的启动过程源码的过程中，接触了以前没接触过的知识，比如fork，多用户等与linux相关的内容，task, stack, display等android管理系统组件的方式，在分析的同时，补充中间涉及到的知识点，不求多深入了解，至少有个概念，以后需要深入了解的时候，不会一脸懵逼。

这块东西，最重要的应该是对应用程序进程的创建过程的了解了，以前对这个确实没什么概念，现在通过分析，知道每个应用程序进程都是zygote进程fork出来的，以前对Zygote感到很神秘，敬而远之，其实就是一个一直在android中运行的进程，为了方便创建其它进程的一个东西而已。

这些在之前的分析过程中，我都有单独整理一点点知识点。

* [桌面启动应用的过程](./LaunchApps.md)
* [Android多用户系统](./UserManagerService.md)
* [Android应用进程启动过程](./StartPorcess.md)