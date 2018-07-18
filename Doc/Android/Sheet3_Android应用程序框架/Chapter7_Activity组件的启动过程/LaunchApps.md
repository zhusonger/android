# LaunchAppService

### Step1: LaunchApps.startMainActivity
LauncherApps是一个系统服务在应用端的本地代理，调用的mService就是LauncherAppsService就是这个系统服务，它的创建过程与AMS一样，在SystemServer里构造出来的。
 
 		 public void startMainActivity(ComponentName component, UserHandle user, Rect sourceBounds,
            Bundle opts) {
        if (DEBUG) {
            Log.i(TAG, "StartMainActivity " + component + " " + user.getIdentifier());
        }
        try {
            mService.startActivityAsUser(component, sourceBounds, opts, user);
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
    }


### Step2: LauncherAppsService.startActivityAsUser (SystemServer)

我们忽略细节，抓住关键的几个点，Intent的组装，这里就应证了我们根节点的配置的说明，需要_Intent.ACTION_MAIN_ 和 _Intent.CATEGORY_LAUNCHER_, 同时它添加flag _Intent.FLAG_ACTIVITY_NEW_TASK_, 为了让启动的根activity在一个新的task，现在多了一个_Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED_的flag，还没有去研究过，先把握主体，放一放。

	@Override
        public void startActivityAsUser(String callingPackage,
                ComponentName component, Rect sourceBounds,
                Bundle opts, UserHandle user) throws RemoteException {
            if (!canAccessProfile(callingPackage, user, "Cannot start activity")) {
                return;
            }
            if (!isUserEnabled(user)) {
                throw new IllegalStateException("Cannot start activity for disabled profile "  + user);
            }

            Intent launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.setSourceBounds(sourceBounds);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            launchIntent.setPackage(component.getPackageName());

            final int callingUid = injectBinderCallingUid();
            long ident = Binder.clearCallingIdentity();
            try {
                final PackageManagerInternal pmInt =
                        LocalServices.getService(PackageManagerInternal.class);
                ActivityInfo info = pmInt.getActivityInfo(component,
                        PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                        callingUid, user.getIdentifier());
                if (!info.exported) {
                    throw new SecurityException("Cannot launch non-exported components "
                            + component);
                }

                // Check that the component actually has Intent.CATEGORY_LAUCNCHER
                // as calling startActivityAsUser ignores the category and just
                // resolves based on the component if present.
                List<ResolveInfo> apps = pmInt.queryIntentActivities(launchIntent,
                        PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                        callingUid, user.getIdentifier());
                final int size = apps.size();
                for (int i = 0; i < size; ++i) {
                    ActivityInfo activityInfo = apps.get(i).activityInfo;
                    if (activityInfo.packageName.equals(component.getPackageName()) &&
                            activityInfo.name.equals(component.getClassName())) {
                        // Found an activity with category launcher that matches
                        // this component so ok to launch.
                        launchIntent.setComponent(component);
                        mContext.startActivityAsUser(launchIntent, opts, user);
                        return;
                    }
                }
                throw new SecurityException("Attempt to launch activity without "
                        + " category Intent.CATEGORY_LAUNCHER " + component);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }


在第一步中，我们看到启动了Activity,但是启动的mContext是什么，找到LauncherAppsService中mContext赋值的地方，发现是LauncherAppsService构造方法的时候，那我们去找LauncherAppsService，没有找到直接new LauncherAppsService的地方，但是发现在系统服务SystemServer的startOtherServices方法最后有这么两行代码

		traceBeginAndSlog("StartLauncherAppsService");
		mSystemServiceManager.startService(LauncherAppsService.class);
		
那我猜测，可能是跟我重构代码的ModuleServiceManager一样，使用了newInstance来构造了它的方法，果然如此，代码跟进去，SystemServiceManager的startService代码里进行了构造

		Constructor<T> constructor = serviceClass.getConstructor(Context.class);
        service = constructor.newInstance(mContext);
        
可是到了这里，mContext还是不知道是哪来的。。。 看赋值的地方，又是SystemServiceManager的构造方法，刚才有瞄到一眼，是在SystemServer里new出来的，回去看吧。。。

在SystemServer的run方法里

		mSystemServiceManager = new SystemServiceManager(mSystemContext);
		
功夫不负有心人，终于快找到了，因为我看到了熟悉的ActivityThread
		 
		ActivityThread activityThread = ActivityThread.systemMain();
		mSystemContext = activityThread.getSystemContext();
	
找到ActivityThread的getSystemContext方法

	public ContextImpl getSystemContext() {
        synchronized (this) {
            if (mSystemContext == null) {
                mSystemContext = ContextImpl.createSystemContext(this);
            }
            return mSystemContext;
        }
    }
 
又看到了一个熟悉的东西，ContextImpl, 这个后面也会讲到，每个Activity创建的时候，都会新建一个ContextImpl, 用来索引应用资源，这总该是最后一步了，看到ContextImpl的createSystemContext方法，里面做了什么我们先不管，至少知道最开始startActivityAsUser是谁的方法了，没错，就是ContextImpl，我们继续根Activity的启动分析。

	static ContextImpl createSystemContext(ActivityThread mainThread) {
        LoadedApk packageInfo = new LoadedApk(mainThread);
        ContextImpl context = new ContextImpl(null, mainThread,
                packageInfo, null, null, 0, null, null, Display.INVALID_DISPLAY);
        context.mResources.updateConfiguration(context.mResourcesManager.getConfiguration(),
                context.mResourcesManager.getDisplayMetrics());
        return context;
    }
 
### Step3: ContextImpl.startActivityAsUser(App Thread)
我们看到ContextImpl的startActivityAsUser方法，最后执行的过程是

		try {
            ActivityManagerNative.getDefault().startActivityAsUser(
                mMainThread.getApplicationThread(), getBasePackageName(), intent,
                intent.resolveTypeIfNeeded(getContentResolver()),
                null, null, 0, Intent.FLAG_ACTIVITY_NEW_TASK, null, options,
                user.getIdentifier());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
      
ActivityManagerNative.getDefault()这个我们先不再细看了，就是应用程序持有一个AMS本地代理，通过它，我们可以调用系统服务AMS的方法。其他的餐素好就是一些启动信息了，不过有个mMainThread比较引人注意，它是个ActivityThread，它的赋值是在ContextImpl构造方法以及Activity的attach方法内，那这个ActivityThread缘起何处呢，这个就是在Activity的启动过程中会涉及到，我们先不在这里说，因为过程略微有点多。
