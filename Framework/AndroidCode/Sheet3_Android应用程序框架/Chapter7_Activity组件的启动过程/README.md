# 简介
Activity的启动过程是每个Android开发者都想要理解的东西，但是大部分应用开发并不清楚它的启动过程，想要在当前水平继续提升，阅读源码的Activity的启动过程是个不错的提升方式。这一章节主要关注Activity的启动过程，AIDL的实现原理那部分先放一边。简单理解成直接调用另外一个类的方法。

__本文档是基于android7.1(targetSdk 25)的源码分析的。__

参考的网站:

* Android Stack与Task : <https://www.jianshu.com/p/82f3af2135a8>
* Android N进程启动流程: <http://blog.csdn.net/yun_hen/article/details/78428767#t9>
* Android“强行停止”详解: <http://www.rogerblog.cn/2016/03/05/sourcecode-about-broadcast-receiver-stopstate/>

---

# 一、根Activity与子Activity
## 根Activity
作为Android的应用开发工程师，一定知道，开发一个应用会有一个启动的Activity, 配置特殊的intent-filter。
		
	<intent-filter>
		<action android:name="android.intent.action.MAIN"/>
		<category android:name="android.intent.category.LAUNCHER"/>
	</intent-filter>
	
这个就是__根Activity__, 在Android系统桌面应用Launcher点击一个icon，就是打开这个Activity.打开这个根Activity有些特殊，会创建一个新的应用__进程__。

## 子Activity
有了根Activity之后，我们在启动其他Activity就是通过startActivity来启动的，这些除了根Activity之外的Activity, 就是子Activity,在不指定子Activity的process的情况下，它们与根Activity都是在同一个应用进程中的。指定process会在创建新的应用__进程__。在应用进程的更抽象的一个概念是Task任务栈, 在不指定taskAffinity的情况下，它们是在同一个任务栈中的，任务栈就是一个数据结构TaskRecord, Activity是在一个列表中先进后出的原则添加在列表中的，有新的Activity会通过Activity的task插入到列表的合适位置(倒序遍历Activity列表)。


# 二、根Activity的启动过程
我们看到的桌面也是一个应用程序，只不过是系统的应用程序，在系统启动的时候，开启这个应用进程，然后通过PackageManager查询已经安装的应用，把根Activity显示在桌面。

### Step0: AMS的启动 (SystemServer)
在android系统启动的过程中，zygote会去启动SystemServer，执行它的main函数，然后AMS也在这个过程中启动起来了。

	 // Activity manager runs the show.
        traceBeginAndSlog("StartActivityManager");
        mActivityManagerService = mSystemServiceManager.startService(
                ActivityManagerService.Lifecycle.class).getService();
        mActivityManagerService.setSystemServiceManager(mSystemServiceManager);
        mActivityManagerService.setInstaller(installer);
        traceEnd();
关于mSystemServiceManager，在我之前查看启动过程中（Step1），SystemServer里的系统服务，跟我重构的代码一样，都是通过构造方法的newInstance来构造实例的。

### Step1: Launcher.startActivitySafely
那启动一个根Activity的过程，就是一个启动Activity的过程，在桌面应用程序点击icon的时候，找到它的点击事件，执行了startActivitySafely方法，其实就是调用了startActivity方法

	public void onClick(View v) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).
        ...
        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            // Open shortcut
            final Intent intent = ((ShortcutInfo) tag).intent;
            int[] pos = new int[2];
            v.getLocationOnScreen(pos);
            intent.setSourceBounds(new Rect(pos[0], pos[1],
                    pos[0] + v.getWidth(), pos[1] + v.getHeight()));

            boolean success = startActivitySafely(v, intent, tag);

            ...
        } else if (tag instanceof FolderInfo) {
            ...
        } else if (v == mAllAppsButton) {
            ...
        }
    }
    
    boolean startActivitySafely(View v, Intent intent, Object tag) {
        boolean success = false;
        try {
            success = startActivity(v, intent, tag);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + tag + " intent=" + intent, e);
        }
        return success;
    }
   
 找到重载)的startActivity方法, 添加了Intent.FLAG_ACTIVITY_NEW_TASK，为了让新启动的应用属于自己独立的Task, user在这里就是null，直接调用父类的startActivity,这里有2种启动方式，另外一种是当前用户与启动这个activity的用户不属于同一个用户的时候，因为android是基于linux开发的，所以也存在多用户的情况，一般情况下，就是当前用户了，但也存在其他可能，所以当出现跨用户调用activity的时候，使用系统的一个app服务来启动。这部分在文件[LaunchApps](./LaunchApps.md)说明.
 	
 	boolean startActivity(View v, Intent intent, Object tag) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            // Only launch using the new animation if the shortcut has not opted out (this is a
            // private contract between launcher and may be ignored in the future).
            boolean useLaunchAnimation = (v != null) &&
                    !intent.hasExtra(INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION);
            UserHandle user = (UserHandle) intent.getParcelableExtra(ApplicationInfo.EXTRA_PROFILE);
            LauncherApps launcherApps = (LauncherApps)
                    this.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (useLaunchAnimation) {
                ActivityOptions opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0,
                        v.getMeasuredWidth(), v.getMeasuredHeight());
                if (user == null || user.equals(android.os.Process.myUserHandle())) {
                    // Could be launching some bookkeeping activity
                    startActivity(intent, opts.toBundle());
                } else {
                    launcherApps.startMainActivity(intent.getComponent(), user,
                            intent.getSourceBounds(),
                            opts.toBundle());
                }
            } else {
                if (user == null || user.equals(android.os.Process.myUserHandle())) {
                    startActivity(intent);
                } else {
                    launcherApps.startMainActivity(intent.getComponent(), user,
                            intent.getSourceBounds(), null);
                }
            }
            return true;
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity. "
                    + "tag="+ tag + " intent=" + intent, e);
        }
        return false;
    }
 

### Step2: Activity.startActivity

调用到Activity的startActivity方法，最终调用的还是startActivityForResult,只是requestCode为-1, 所以我们在调用startActivityForResult的request都要求大于等于0, 这个原因在后面会看到。
	
	@Override
    public void startActivity(Intent intent, @Nullable Bundle options) {
        if (options != null) {
            startActivityForResult(intent, -1, options);
        } else {
            // Note we want to go through this call for compatibility with
            // applications that may have overridden the method.
            startActivityForResult(intent, -1);
        }
    }

	public void startActivityForResult(@RequiresPermission Intent intent, int requestCode,
            @Nullable Bundle options) {
        if (mParent == null) {
            options = transferSpringboardActivityOptions(options);
            Instrumentation.ActivityResult ar =
                mInstrumentation.execStartActivity(
                    this, mMainThread.getApplicationThread(), mToken, this,
                    intent, requestCode, options);
            ...
        } else {
            ....
        }
    }
    

### Step3: Instrumentation.execStartActivity

Instrumentation这个类在自动化测试经常会看到，这个类的作用就是允许您监视系统与应用程序之间的所有交互。在这里，我们用它来启动activity，最后调用的ActivityManagerNative的startActivity方法，这个ActivityManagerNative是AMS在客户端的本地代理，通过aidl实现，aidl是android提供的一个跨进程通信的机制。具体的在其他地方再解读。checkStartActivityResult是检测AMS调用结果，异常检测。
	
	public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {
        IApplicationThread whoThread = (IApplicationThread) contextThread;
        ...
        if (mActivityMonitors != null) {
            synchronized (mSync) {
                // 这里是监测测试用的代码，不管
                ...
            }
        }
        try {
            intent.migrateExtraStreamToClipData();
            intent.prepareToLeaveProcess(who);
            int result = ActivityManagerNative.getDefault()
                .startActivity(whoThread, who.getBasePackageName(), intent,
                        intent.resolveTypeIfNeeded(who.getContentResolver()),
                        token, target != null ? target.mEmbeddedID : null,
                        requestCode, 0, null, options);
            checkStartActivityResult(result, intent);
        } catch (RemoteException e) {
            throw new RuntimeException("Failure from system", e);
        }
        return null;
    }


    public static void checkStartActivityResult(int res, Object intent) {
        if (res >= ActivityManager.START_SUCCESS) {
            return;
        }

        switch (res) {
        	// Activity找不到
            case ActivityManager.START_INTENT_NOT_RESOLVED:
            case ActivityManager.START_CLASS_NOT_FOUND:
                if (intent instanceof Intent && ((Intent)intent).getComponent() != null)
                    throw new ActivityNotFoundException(
                            "Unable to find explicit activity class "
                            + ((Intent)intent).getComponent().toShortString()
                            + "; have you declared this activity in your AndroidManifest.xml?");
                throw new ActivityNotFoundException(
                        "No Activity found to handle " + intent);
            
            // 调用者没有权限调用该Activity
            case ActivityManager.START_PERMISSION_DENIED:
                throw new SecurityException("Not allowed to start activity "
                        + intent);
            // 调用这添加了FLAG_ACTIVITY_FORWARD_RESULT标记了，但是还调用startActivityForResult
            case ActivityManager.START_FORWARD_AND_REQUEST_CONFLICT:
                throw new AndroidRuntimeException(
                        "FORWARD_RESULT_FLAG used while also requesting a result");
            case ActivityManager.START_NOT_ACTIVITY:
                throw new IllegalArgumentException(
                        "PendingIntent is not an activity");
            case ActivityManager.START_NOT_VOICE_COMPATIBLE:
                throw new SecurityException(
                        "Starting under voice control not allowed for: " + intent);
            case ActivityManager.START_VOICE_NOT_ACTIVE_SESSION:
                throw new IllegalStateException(
                        "Session calling startVoiceActivity does not match active session");
            case ActivityManager.START_VOICE_HIDDEN_SESSION:
                throw new IllegalStateException(
                        "Cannot start voice activity on a hidden session");
            case ActivityManager.START_CANCELED:
                throw new AndroidRuntimeException("Activity could not be started for "
                        + intent);
            default:
                throw new AndroidRuntimeException("Unknown error code "
                        + res + " when starting " + intent);
        }
    }


> Tips: 1-3步是在应用端调用的
    
### Step4: ActivityManagerService.startActivity（SystemServer）
那我们就直接看AMS的代码吧, 对应AMS的startActivity, 调用了startActivityAsUser方法， mUserController.handleIncomingUser会检测调用Activity的userId, Android4.2开始支持多用户系统，关于Android中多用户的说明，请参考[UserManagerService](./UserManagerService.md). 继续我们的启动activity，google工程师还给了个注释，__Switch to user app stacks here.__, 就是这里，我们要开启用户应用的任务栈，这个mActivityStarter应该是android系统源码重构的工具类吧，本书中好像不是这个。不需要管，我们直接去看这个方法调用的内容就行了。
	
	@Override
    public final int startActivityAsUser(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
            int startFlags, ProfilerInfo profilerInfo, Bundle bOptions, int userId) {
        // 检测是否是沙箱环境，沙箱环境就抛出异常
        enforceNotIsolatedCaller("startActivity");
        // 检测调用者与将要调用的是否是相通用户，不是相同用户首现会进行一系列检测，看是否允许执行
        // 不允许的情况下会抛出异常
        userId = mUserController.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(),
                userId, false, ALLOW_FULL_ONLY, "startActivity", null);
        // TODO: Switch to user app stacks here.
        return mActivityStarter.startActivityMayWait(caller, -1, callingPackage, intent,
                resolvedType, null, null, resultTo, resultWho, requestCode, startFlags,
                profilerInfo, null, null, bOptions, false, userId, null, null);
    }

### Step5: ActivityStarter.startActivityMayWait（SystemServer）

这里中间代码很多，找到几行关键代码，这里我就不再细跟了，就是通过PMS获取匹配的ActivityInfo信息。

	ResolveInfo rInfo = mSupervisor.resolveIntent(intent, resolvedType, userId);
	ActivityInfo aInfo = mSupervisor.resolveActivity(intent, rInfo, startFlags, profilerInfo);

最后调用startActivityLocked继续Activity的启动。在执行完startActivityLocked之后，会锁住AMS,等待的执行结果。所以方法名也取的很有意味，MayWait.

	int res = startActivityLocked(caller, intent, ephemeralIntent, resolvedType,
                    aInfo, rInfo, voiceSession, voiceInteractor,
                    resultTo, resultWho, requestCode, callingPid,
                    callingUid, callingPackage, realCallingPid, realCallingUid, startFlags,
                    options, ignoreTargetSecurity, componentSpecified, outRecord, container,
                    inTask);
                    
### Step6: ActivityStarter.startActivityLocked（SystemServer）

首先获取调用者所在的线程数据对象，现在caller就是桌面应用程序，因为这个桌面应用程序是已经启动的，所以这里会返回桌面应用程序的进程对象。

		ProcessRecord callerApp = null;
        if (caller != null) {
            callerApp = mService.getRecordForAppLocked(caller);
            if (callerApp != null) {
                callingPid = callerApp.pid;
                callingUid = callerApp.info.uid;
            } else {
                Slog.w(TAG, "Unable to find app for caller " + caller
                        + " (pid=" + callingPid + ") when starting: "
                        + intent.toString());
                err = ActivityManager.START_PERMISSION_DENIED;
            }
        }
因为桌面应用程序启动应用，中间过程先忽略，然后就新建了ActivityRecord对象，在AMS中唯一标志一个Activity, 这里的r表示的就是我们即将启动的根Activity,并调用startActivityUnchecked继续下一步

	ActivityRecord r = new ActivityRecord(mService, callerApp, callingUid, callingPackage,
                intent, resolvedType, aInfo, mService.mConfiguration, resultRecord, resultWho,
                requestCode, componentSpecified, voiceSession != null, mSupervisor, container,
                options, sourceRecord);
                
 	try {
            mService.mWindowManager.deferSurfaceLayout();
            err = startActivityUnchecked(r, sourceRecord, voiceSession, voiceInteractor, startFlags,
                    true, options, inTask);
        } finally {
            mService.mWindowManager.continueSurfaceLayout();
        }
        
        
### Step7: ActivityStarter.startActivityUnchecked（SystemServer）
这个方法的一开始，进行了一些初始化与更新标志值的过程,setInitialState更新要启动的Activity到全局常量mStartActivity等。

	private int startActivityUnchecked(final ActivityRecord r, ActivityRecord sourceRecord,
            IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
            int startFlags, boolean doResume, ActivityOptions options, TaskRecord inTask) {

        setInitialState(r, options, inTask, doResume, startFlags, sourceRecord, voiceSession,
                voiceInteractor);

        computeLaunchingTaskFlags();

        computeSourceStack();

        mIntent.setFlags(mLaunchFlags);
        
        mReusedActivity = getReusableIntentActivity();
        ......
	   }
   
__setInitialState__里设置了一些参数，其中有个函数__sendNewTaskResultRequestIfNeeded__, 这里会检测新的activity是否属于新的任务栈，如果是的，就直接回调onActivityResult。
		
	private void sendNewTaskResultRequestIfNeeded() {
        if (mStartActivity.resultTo != null && (mLaunchFlags & FLAG_ACTIVITY_NEW_TASK) != 0
                && mStartActivity.resultTo.task.stack != null) {
            // For whatever reason this activity is being launched into a new task...
            // yet the caller has requested a result back.  Well, that is pretty messed up,
            // so instead immediately send back a cancel and let the new task continue launched
            // as normal without a dependency on its originator.
            Slog.w(TAG, "Activity is launching as a new task, so cancelling activity result.");
            mStartActivity.resultTo.task.stack.sendActivityResultLocked(-1, mStartActivity.resultTo,
                    mStartActivity.resultWho, mStartActivity.requestCode, RESULT_CANCELED, null);
            mStartActivity.resultTo = null;
        }
    }
    

      
getReusableIntentActivity是获取是否可以复用的Activity,因为activity的启动模式为singelTask/singleInstance，如果启动的Activity存在，会存在复用的情况，在这里，我们没有复用，所以跳过这个判断。 
如果即将启动的Activity与已经启动的Activity是同一个，并且模式是singelTask/singleInstance／singleTop，就会调用那个Activity的onNewIntent。

		// If the activity being launched is the same as the one currently at the top, then
        // we need to check if it should only be launched once.
        final ActivityStack topStack = mSupervisor.mFocusedStack;
        final ActivityRecord top = topStack.topRunningNonDelayedActivityLocked(mNotTop);
        final boolean dontStart = top != null && mStartActivity.resultTo == null
                && top.realActivity.equals(mStartActivity.realActivity)
                && top.userId == mStartActivity.userId
                && top.app != null && top.app.thread != null
                && ((mLaunchFlags & FLAG_ACTIVITY_SINGLE_TOP) != 0
                || mLaunchSingleTop || mLaunchSingleTask);
        if (dontStart) {
           ...
            return START_DELIVERED_TO_TOP;
        }

这里我们也不是，之后执行到这里，在这里我，我们的条件符合，就会执行创建一个新的任务栈TaskRecord

		boolean newTask = false;
        final TaskRecord taskToAffiliate = (mLaunchTaskBehind && mSourceRecord != null)
                ? mSourceRecord.task : null;

        // Should this be considered a new task?
        if (mStartActivity.resultTo == null && mInTask == null && !mAddingToTask
                && (mLaunchFlags & FLAG_ACTIVITY_NEW_TASK) != 0) {
            newTask = true;
            setTaskFromReuseOrCreateNewTask(taskToAffiliate);

            if (mSupervisor.isLockTaskModeViolation(mStartActivity.task)) {
                Slog.e(TAG, "Attempted Lock Task Mode violation mStartActivity=" + mStartActivity);
                return START_RETURN_LOCK_TASK_MODE_VIOLATION;
            }
            if (!mMovedOtherTask) {
                // If stack id is specified in activity options, usually it means that activity is
                // launched not from currently focused stack (e.g. from SysUI or from shell) - in
                // that case we check the target stack.
                updateTaskReturnToType(mStartActivity.task, mLaunchFlags,
                        preferredLaunchStackId != INVALID_STACK_ID ? mTargetStack : topStack);
            }
        }
        
在setTaskFromReuseOrCreateNewTask方法中，创建了一个新的TaskRecord实例和ActivityStack实例

	private void setTaskFromReuseOrCreateNewTask(TaskRecord taskToAffiliate) {
        mTargetStack = computeStackFocus(mStartActivity, true, mLaunchBounds, mLaunchFlags,
                mOptions);

        if (mReuseTask == null) {
            final TaskRecord task = mTargetStack.createTaskRecord(
                    mSupervisor.getNextTaskIdForUserLocked(mStartActivity.userId),
                    mNewTaskInfo != null ? mNewTaskInfo : mStartActivity.info,
                    mNewTaskIntent != null ? mNewTaskIntent : mIntent,
                    mVoiceSession, mVoiceInteractor, !mLaunchTaskBehind /* toTop */);
            mStartActivity.setTask(task, taskToAffiliate);
            if (mLaunchBounds != null) {
                final int stackId = mTargetStack.mStackId;
                if (StackId.resizeStackWithLaunchBounds(stackId)) {
                    mService.resizeStack(
                            stackId, mLaunchBounds, true, !PRESERVE_WINDOWS, ANIMATE, -1);
                } else {
                    mStartActivity.task.updateOverrideConfiguration(mLaunchBounds);
                }
            }
            if (DEBUG_TASKS) Slog.v(TAG_TASKS,
                    "Starting new activity " +
                            mStartActivity + " in new task " + mStartActivity.task);
        } else {
            mStartActivity.setTask(mReuseTask, taskToAffiliate);
        }
    }
    
 computeStackFocus返回一个mTargetStack，这个ActivityStack是为即将启动的Activity的容器所在的Activity任务栈.

这里涉及到一个概念，ActivityStack的分为几大类
> Stack的分类
> 
> ActivityStack 分为以下五类
> 
> 0 HOME_STACK_ID Home应用以及recents app所在的栈
> 
> 1 FULLSCREEN_WORKSPACE_STACK_ID 一般应用所在的栈
> 
> 2 FREEFORM_WORKSPACE_STACK_ID 类似桌面操作系统
> 
> 3 DOCKED_STACK_ID 分屏
> 
> 4 PINNED_STACK_ID 画中画栈
> 
> 这五种栈称为静态栈，如果rom厂商需要定制更多的栈，那后面的就是动态栈，从5开始

现在，我们再来看computeStackFocus，这里就是获取我们即将启动的Activity所需要的栈,最后我们获取到的FULLSCREEN_WORKSPACE_STACK_ID的ActivityStack任务栈, 这个任务栈是存放全屏的Activity的任务栈，FREEFORM_WORKSPACE_STACK_ID这个任务栈是可以缩放大小的Activity，这个我的s8+上有个功能就是可以缩小界面，看这个注释应该就是这类。

		private ActivityStack computeStackFocus(ActivityRecord r, boolean newTask, Rect bounds,
            int launchFlags, ActivityOptions aOptions) {
        final TaskRecord task = r.task;
        // task为null,isApplicationActivity为true，不符合
        if (!(r.isApplicationActivity() || (task != null && task.isApplicationTask()))) {
            return mSupervisor.mHomeStack;
        }
		// 这个是分屏的任务栈，不符合
        ActivityStack stack = getLaunchStack(r, launchFlags, task, aOptions);
        if (stack != null) {
            return stack;
        }
		// task为null， 不符合
        if (task != null && task.stack != null) {
            stack = task.stack;
            if (stack.isOnHomeDisplay()) {
                if (mSupervisor.mFocusedStack != stack) {
                    if (DEBUG_FOCUS || DEBUG_STACK) Slog.d(TAG_FOCUS,
                            "computeStackFocus: Setting " + "focused stack to r=" + r
                                    + " task=" + task);
                } else {
                    if (DEBUG_FOCUS || DEBUG_STACK) Slog.d(TAG_FOCUS,
                            "computeStackFocus: Focused stack already="
                                    + mSupervisor.mFocusedStack);
                }
            }
            return stack;
        }
		// 创建r的时候，没有赋值mInitialActivityContainer，不符合
        final ActivityStackSupervisor.ActivityContainer container = r.mInitialActivityContainer;
        if (container != null) {
            // The first time put it on the desired stack, after this put on task stack.
            r.mInitialActivityContainer = null;
            return container.mStack;
        }
		
		// 这里的意思是如果存在全屏的Activity栈，我们就直接用，其他的2个类型符合某种条件，也可以
		// 不过这里我们的栈是桌面，HOME_STACK_ID，所以不符合
        // The fullscreen stack can contain any task regardless of if the task is resizeable
        // or not. So, we let the task go in the fullscreen task if it is the focus stack.
        // If the freeform or docked stack has focus, and the activity to be launched is resizeable,
        // we can also put it in the focused stack.
        final int focusedStackId = mSupervisor.mFocusedStack.mStackId;
        final boolean canUseFocusedStack = focusedStackId == FULLSCREEN_WORKSPACE_STACK_ID
                || (focusedStackId == DOCKED_STACK_ID && r.canGoInDockedStack())
                || (focusedStackId == FREEFORM_WORKSPACE_STACK_ID && r.isResizeableOrForced());
        if (canUseFocusedStack && (!newTask
                || mSupervisor.mFocusedStack.mActivityContainer.isEligibleForNewTasks())) {
            if (DEBUG_FOCUS || DEBUG_STACK) Slog.d(TAG_FOCUS,
                    "computeStackFocus: Have a focused stack=" + mSupervisor.mFocusedStack);
            return mSupervisor.mFocusedStack;
        }
		
		// 首先去取动态栈，有的话就直接返回
        // We first try to put the task in the first dynamic stack.
        final ArrayList<ActivityStack> homeDisplayStacks = mSupervisor.mHomeStack.mStacks;
        for (int stackNdx = homeDisplayStacks.size() - 1; stackNdx >= 0; --stackNdx) {
            stack = homeDisplayStacks.get(stackNdx);
            if (!ActivityManager.StackId.isStaticStack(stack.mStackId)) {
                if (DEBUG_FOCUS || DEBUG_STACK) Slog.d(TAG_FOCUS,
                        "computeStackFocus: Setting focused stack=" + stack);
                return stack;
            }
        }
		
		// 没有符合条件的动态栈，我们在获取静态栈
        // If there is no suitable dynamic stack then we figure out which static stack to use.
        final int stackId = task != null ? task.getLaunchStackId() :
                bounds != null ? FREEFORM_WORKSPACE_STACK_ID :
                        FULLSCREEN_WORKSPACE_STACK_ID;
        stack = mSupervisor.getStack(stackId, CREATE_IF_NEEDED, ON_TOP);
        if (DEBUG_FOCUS || DEBUG_STACK) Slog.d(TAG_FOCUS, "computeStackFocus: New stack r="
                + r + " stackId=" + stack.mStackId);
        return stack;
    }

#### Step7.1: ActivityStackSupervisor.getStack

我们通过这个方法来获取应用所需要的栈，我们要的是FULLSCREEN_WORKSPACE_STACK_ID，mActivityContainers是一个SparseArray<ActivityContainer>队列，ActivityContainer与stackId一一对应，如果我们已经存在一个FULLSCREEN_WORKSPACE_STACK_ID的栈，那就直接返回了，否则就通过createStackOnDisplay创建一个，并添加到系统的默认显示屏上。

	ActivityStack getStack(int stackId, boolean createStaticStackIfNeeded, boolean createOnTop) {
        ActivityContainer activityContainer = mActivityContainers.get(stackId);
        if (activityContainer != null) {
            return activityContainer.mStack;
        }
        if (!createStaticStackIfNeeded || !StackId.isStaticStack(stackId)) {
            return null;
        }
        return createStackOnDisplay(stackId, Display.DEFAULT_DISPLAY, createOnTop);
    }
    
    ActivityStack createStackOnDisplay(int stackId, int displayId, boolean onTop) {
        ActivityDisplay activityDisplay = mActivityDisplays.get(displayId);
        if (activityDisplay == null) {
            return null;
        }

        ActivityContainer activityContainer = new ActivityContainer(stackId);
        mActivityContainers.put(stackId, activityContainer);
        activityContainer.attachToDisplayLocked(activityDisplay, onTop);
        return activityContainer.mStack;
    }

#### Step7.2: ActivityStarter.setTaskFromReuseOrCreateNewTask
得到了我们activity需要的栈之后呢，我们会到setTaskFromReuseOrCreateNewTask，mReuseTask是我们启动根Activity的时候传入的inTask，不好意思，是个null，说明我们没有TaskRecord啊，请给我们创建一个，于是，这里，我们为即将启动的Activity创建了一个TaskRecord, 并更新即将启动的Activity的任务栈为创建的那个新的任务栈，并添加到ActivityStack的mTaskHistory全局变量中。

__回忆一下平时Android系统的使用习惯，打开应用程序，那我们可以会切换到后台，然后再开启一个应用程序，那么它们都是用的FULLSCREEN_WORKSPACE_STACK_ID，那么所有的Activity都是在FULLSCREEN_WORKSPACE_STACK_ID的mTaskHistory队列里面，那为什么在按返回键，到应用程序根Activity就会回到桌面，而不是继续回到上一个TaskRecord的Activity, 我猜测这是TaskRecord的作用，在处理关闭Activity的过程中，检测到根Activity就回到桌面，这个涉及到关闭Activity的过程，同时startActivityForResult的返回结果，也是在这个过程中，下一篇再分析下这个。__

__到这里为止，我们创建了即将启动的Activity的任务栈__


### Step8: ActivityStack.startActivityLocked(SystemServer)

在这个方法里面，我么初次把App的第一个Activity的Window添加到系统界面上。

### Step9: ActivityStarter.startActivityUnchecked
又回到了ActivityStarter，mDoResume的值在前面的函数赋值，是true,进入代码块
                        

	 if (mDoResume) {
            if (!mLaunchTaskBehind) {
                // TODO(b/26381750): Remove this code after verification that all the decision
                // points above moved targetStack to the front which will also set the focus
                // activity.
                mService.setFocusedActivityLocked(mStartActivity, "startedActivity");
            }
            final ActivityRecord topTaskActivity = mStartActivity.task.topRunningActivityLocked();
            if (!mTargetStack.isFocusable()
                    || (topTaskActivity != null && topTaskActivity.mTaskOverlay
                    && mStartActivity != topTaskActivity)) {
                // If the activity is not focusable, we can't resume it, but still would like to
                // make sure it becomes visible as it starts (this will also trigger entry
                // animation). An example of this are PIP activities.
                // Also, we don't want to resume activities in a task that currently has an overlay
                // as the starting activity just needs to be in the visible paused state until the
                // over is removed.
                mTargetStack.ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
                // Go ahead and tell window manager to execute app transition for this activity
                // since the app transition will not be triggered through the resume channel.
                mWindowManager.executeAppTransition();
            } else {
                mSupervisor.resumeFocusedStackTopActivityLocked(mTargetStack, mStartActivity,
                        mOptions);
            }
        } else {
            mTargetStack.addRecentActivityLocked(mStartActivity);                                    
        }
        
        
#### Step9.1 ActivityManagerService.setFocusedActivityLocked

setFocusedActivityLocked会设置当前焦点ActivityStack为将要启动的activity所在的任务栈，把当前焦点Activity   (mFocusedActivity)更新为正在启动的activity

	boolean setFocusedActivityLocked(ActivityRecord r, String reason) {
        ...
        mFocusedActivity = r;
        ...
        if (mStackSupervisor.moveActivityStackToFront(r, reason + " setFocusedActivity")) {
            mWindowManager.setFocusedApp(r.appToken, true);
        }
        ...
        return true;
    }
        
        
#### Step 9.2 ActivityStackSupervisor.moveActivityStackToFront 

moveActivityStackToFront会把正在启动的Activity所在的ActivityStack更新为mFocusedStack

	boolean moveActivityStackToFront(ActivityRecord r, String reason) {
        if (r == null) {
            // Not sure what you are trying to do, but it is not going to work...
            return false;
        }
        final TaskRecord task = r.task;
        if (task == null || task.stack == null) {
            Slog.w(TAG, "Can't move stack to front for r=" + r + " task=" + task);
            return false;
        }
        task.stack.moveToFront(reason, task);
        return true;
    }
    
 
#### Step 9.3 ActivityStack.moveToFront
在ActivityStack，如果目前的显示屏是DEFAULT_DISPLAY,我们就更新焦点ActivityStack为当前的，也就是我们要启动的那个Activity所在的ActivityStack
	
	void moveToFront(String reason, TaskRecord task) {
        ...

        // TODO(multi-display): Needs to also work if focus is moving to the non-home display.
        if (isOnHomeDisplay()) {
            mStackSupervisor.setFocusStackUnchecked(reason, this);
        }
        ...
        
        if (task != null) {
            mWindowManager.moveTaskToTop(task.taskId);
        }
    }
    
#### Step 9.4 ActivityStackSupervisor.setFocusStackUnchecked

这里，我们将焦点转移到了准备启动的Activity所在的ActivityStack栈中。

	/** NOTE: Should only be called from {@link ActivityStack#moveToFront} */
    void setFocusStackUnchecked(String reason, ActivityStack focusCandidate) {
        if (!focusCandidate.isFocusable()) {
            // The focus candidate isn't focusable. Move focus to the top stack that is focusable.
            focusCandidate = focusCandidate.getNextFocusableStackLocked();
        }

        if (focusCandidate != mFocusedStack) {
            mLastFocusedStack = mFocusedStack;
            mFocusedStack = focusCandidate;

            EventLogTags.writeAmFocusedStack(
                    mCurrentUser, mFocusedStack == null ? -1 : mFocusedStack.getStackId(),
                    mLastFocusedStack == null ? -1 : mLastFocusedStack.getStackId(), reason);
        }

        final ActivityRecord r = topRunningActivityLocked();
        if (!mService.mDoingSetFocusedActivity && mService.mFocusedActivity != r) {
            // The focus activity should always be the top activity in the focused stack.
            // There will be chaos and anarchy if it isn't...
            mService.setFocusedActivityLocked(r, reason + " setFocusStack");
        }

        if (mService.mBooting || !mService.mBooted) {
            if (r != null && r.idle) {
                checkFinishBootingLocked();
            }
        }
    }

  
### Step10: ActivityStackSupervisor.resumeFocusedStackTopActivityLocked

接着执行resumeFocusedStackTopActivityLocked，第一个条件我们就符合，目标stack就是焦点stack,所以我们调用目标stack的resumeTopActivityUncheckedLocked方法。

		boolean resumeFocusedStackTopActivityLocked(
            ActivityStack targetStack, ActivityRecord target, ActivityOptions targetOptions) {
        if (targetStack != null && isFocusedStack(targetStack)) {
            return targetStack.resumeTopActivityUncheckedLocked(target, targetOptions);
        }
        final ActivityRecord r = mFocusedStack.topRunningActivityLocked();
        if (r == null || r.state != RESUMED) {
            mFocusedStack.resumeTopActivityUncheckedLocked(null, null);
        }
        return false;
    }

#### Step10.1: ActivityStack.resumeTopActivityUncheckedLocked
如果正在启动顶部activity,就不再继续，这里我们当然没有正在resume顶部activity,所以继续执行resumeTopActivityInnerLocked	
	
	boolean resumeTopActivityUncheckedLocked(ActivityRecord prev, ActivityOptions options) {
        if (mStackSupervisor.inResumeTopActivity) {
            // Don't even start recursing.
            return false;
        }

        boolean result = false;
        try {
            // Protect against recursion.
            mStackSupervisor.inResumeTopActivity = true;
            if (mService.mLockScreenShown == ActivityManagerService.LOCK_SCREEN_LEAVING) {
                mService.mLockScreenShown = ActivityManagerService.LOCK_SCREEN_HIDDEN;
                mService.updateSleepIfNeededLocked();
            }
            result = resumeTopActivityInnerLocked(prev, options);
        } finally {
            mStackSupervisor.inResumeTopActivity = false;
        }
        return result;
    }

#### Step10.2 ActivityStack.resumeTopActivityInnerLocked
 这个方法内，第一次进入是暂停之前的那个activity,这里就是我们的launcher了，我们会执行mStackSupervisor.pauseBackStacks，但是不会执行 if (mResumedActivity != null)内的暂停，因为当前的ActivityStack是即将启动的那个activity所在的，并没有已经启动的activiyt.

    private boolean resumeTopActivityInnerLocked(ActivityRecord prev, ActivityOptions options) {
        // 一起其他状态的处理
        ....

        // 第一次进入执行的
        // 首先我们会暂停之前的那个Activity,也就是Launcher，FLAG_RESUME_WHILE_PAUSING这个值很有意思
        // 开发android的基本都知道，我们是在执行完onPause之后，才会执行下一个activity的onResume
        // 但是如果在启动activity的时候加入这个标志值，就可以不等待前一个activity pause结束，而直接执行下一个activity
        // 5.0之后支持 系统桌面与最近使用都添加了该标志值
        // We need to start pausing the current activity so the top one can be resumed...
        final boolean dontWaitForPause = (next.info.flags & FLAG_RESUME_WHILE_PAUSING) != 0;
        boolean pausing = mStackSupervisor.pauseBackStacks(userLeaving, next, dontWaitForPause);
        if (mResumedActivity != null) {
            if (DEBUG_STATES) Slog.d(TAG_STATES,
                    "resumeTopActivityLocked: Pausing " + mResumedActivity);
            pausing |= startPausingLocked(userLeaving, false, next, dontWaitForPause);
        }
        if (pausing) {
            if (DEBUG_SWITCH || DEBUG_STATES) Slog.v(TAG_STATES,
                    "resumeTopActivityLocked: Skip resume: need to start pausing");
            // At this point we want to put the upcoming activity's process
            // at the top of the LRU list, since we know we will be needing it
            // very soon and it would be a waste to let it get killed if it
            // happens to be sitting towards the end.
            if (next.app != null && next.app.thread != null) {
                mService.updateLruProcessLocked(next.app, true, null);
            }
            if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
            return true;
        } else if (mResumedActivity == next && next.state == ActivityState.RESUMED &&
                mStackSupervisor.allResumedActivitiesComplete()) {
            // It is possible for the activity to be resumed when we paused back stacks above if the
            // next activity doesn't have to wait for pause to complete.
            // So, nothing else to-do except:
            // Make sure we have executed any pending transitions, since there
            // should be nothing left to do at this point.
            mWindowManager.executeAppTransition();
            mNoAnimActivities.clear();
            ActivityOptions.abort(options);
            if (DEBUG_STATES) Slog.d(TAG_STATES,
                    "resumeTopActivityLocked: Top activity resumed (dontWaitForPause) " + next);
            if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
            return true;
        }
        第二次进入执行的
        ...
        return true;
    }

	
#### Step 10.3 ActivityStackSupervisor.pauseBackStacks
它会遍历主屏上所有的ActivityStack,找到存在已经启动的activity mResumedActivity，这里就是HOME_STACK_ID的栈

	boolean pauseBackStacks(boolean userLeaving, ActivityRecord resuming, boolean dontWait) {
        boolean someActivityPaused = false;
        for (int displayNdx = mActivityDisplays.size() - 1; displayNdx >= 0; --displayNdx) {
            ArrayList<ActivityStack> stacks = mActivityDisplays.valueAt(displayNdx).mStacks;
            for (int stackNdx = stacks.size() - 1; stackNdx >= 0; --stackNdx) {
                final ActivityStack stack = stacks.get(stackNdx);
                if (!isFocusedStack(stack) && stack.mResumedActivity != null) {
                    if (DEBUG_STATES) Slog.d(TAG_STATES, "pauseBackStacks: stack=" + stack +
                            " mResumedActivity=" + stack.mResumedActivity);
                    someActivityPaused |= stack.startPausingLocked(userLeaving, false, resuming,
                            dontWait);
                }
            }
        }
        return someActivityPaused;
    }


#### Step 10.4 ActivityStack.startPausingLocked
在这里，这个stack又是launcher所在的栈了，这里，我们会调用Activity的生命周期onPause, 回到应用端来执行。同时，我们会发送一个超时消息，这就是我们平时开始的时候，经常说的，onPause里面不要做耗时的操作(500ms)，否则我们会直接执行后面的步骤，但是这个过程就
会让用户页面停顿在那。

	final boolean startPausingLocked(boolean userLeaving, boolean uiSleeping,
            ActivityRecord resuming, boolean dontWait) {
        // 当前launcher栈没有正在暂停的 跳过
        if (mPausingActivity != null) {
            Slog.wtf(TAG, "Going to pause when pause is already pending for " + mPausingActivity
                    + " state=" + mPausingActivity.state);
            if (!mService.isSleepingLocked()) {
                // Avoid recursion among check for sleep and complete pause during sleeping.
                // Because activity will be paused immediately after resume, just let pause
                // be completed by the order of activity paused from clients.
                completePauseLocked(false, resuming);
            }
        }
        // launcher就是这个prev,不为null
        ActivityRecord prev = mResumedActivity;
        if (prev == null) {
            if (resuming == null) {
                Slog.wtf(TAG, "Trying to pause when nothing is resumed");
                mStackSupervisor.resumeFocusedStackTopActivityLocked();
            }
            return false;
        }
        // 不太清楚, 先不管了
        if (mActivityContainer.mParentActivity == null) {
            // Top level stack, not a child. Look for child stacks.
            mStackSupervisor.pauseChildStacks(prev, userLeaving, uiSleeping, resuming, dontWait);
        }

        if (DEBUG_STATES) Slog.v(TAG_STATES, "Moving to PAUSING: " + prev);
        else if (DEBUG_PAUSE) Slog.v(TAG_PAUSE, "Start pausing: " + prev);
        // 更新launcer为正在pause的activity
        mResumedActivity = null;
        mPausingActivity = prev;
        mLastPausedActivity = prev;

        // 这个值用来更新"最近"里显示的activty,我们有时候需要启动新的task, 又不希望它在最近里面显示，就会添加一个属性
        // excludeFromRecents, 这个属性就是添加这个标志值，表示不在最近里面显示
        mLastNoHistoryActivity = (prev.intent.getFlags() & Intent.FLAG_ACTIVITY_NO_HISTORY) != 0
                || (prev.info.flags & ActivityInfo.FLAG_NO_HISTORY) != 0 ? prev : null;
        prev.state = ActivityState.PAUSING;
        prev.task.touchActiveTime();
        clearLaunchTime(prev);
        final ActivityRecord next = mStackSupervisor.topRunningActivityLocked();
        if (mService.mHasRecents
                && (next == null || next.noDisplay || next.task != prev.task || uiSleeping)) {
            prev.mUpdateTaskThumbnailWhenHidden = true;
        }
        stopFullyDrawnTraceIfNeeded();

        mService.updateCpuStats();

        // 暂停launcer组件，这里我们将要回到应用端了
        if (prev.app != null && prev.app.thread != null) {
            if (DEBUG_PAUSE) Slog.v(TAG_PAUSE, "Enqueueing pending pause: " + prev);
            try {
                EventLog.writeEvent(EventLogTags.AM_PAUSE_ACTIVITY,
                        prev.userId, System.identityHashCode(prev),
                        prev.shortComponentName);
                mService.updateUsageStats(prev, false);
                prev.app.thread.schedulePauseActivity(prev.appToken, prev.finishing,
                        userLeaving, prev.configChangeFlags, dontWait);
            } catch (Exception e) {
                // Ignore exception, if process died other code will cleanup.
                Slog.w(TAG, "Exception thrown during pause", e);
                mPausingActivity = null;
                mLastPausedActivity = null;
                mLastNoHistoryActivity = null;
            }
        } else {
            mPausingActivity = null;
            mLastPausedActivity = null;
            mLastNoHistoryActivity = null;
        }

        // If we are not going to sleep, we want to ensure the device is
        // awake until the next activity is started.
        if (!uiSleeping && !mService.isSleepingOrShuttingDownLocked()) {
            mStackSupervisor.acquireLaunchWakelock();
        }

        // launcher组件正在暂停，所以进入这里
        if (mPausingActivity != null) {
            // Have the window manager pause its key dispatching until the new
            // activity has started.  If we're pausing the activity just because
            // the screen is being turned off and the UI is sleeping, don't interrupt
            // key dispatch; the same activity will pick it up again on wakeup.
            if (!uiSleeping) {
                prev.pauseKeyDispatchingLocked();
            } else if (DEBUG_PAUSE) {
                 Slog.v(TAG_PAUSE, "Key dispatch not paused for screen off");
            }

            if (dontWait) {
                // If the caller said they don't want to wait for the pause, then complete
                // the pause now.
                completePauseLocked(false, resuming);
                return false;

            } else {
                // 发送一个暂停超时检测消息，如果暂停执行了了超过PAUSE_TIMEOUT的时间(500ms)
                // 就会直接显示下一个activity,因为一般情况下，都是在执行完onPause之后才显示下一个activity的
                // Schedule a pause timeout in case the app doesn't respond.
                // We don't give it much time because this directly impacts the
                // responsiveness seen by the user.
                Message msg = mHandler.obtainMessage(PAUSE_TIMEOUT_MSG);
                msg.obj = prev;
                prev.pauseTime = SystemClock.uptimeMillis();
                mHandler.sendMessageDelayed(msg, PAUSE_TIMEOUT);
                if (DEBUG_PAUSE) Slog.v(TAG_PAUSE, "Waiting for pause to complete...");
                return true;
            }

        } else {
            // This activity failed to schedule the
            // pause, so just treat it as being paused now.
            if (DEBUG_PAUSE) Slog.v(TAG_PAUSE, "Activity not running, resuming next.");
            if (resuming == null) {
                mStackSupervisor.resumeFocusedStackTopActivityLocked();
            }
            return false;
        }
    }
    
    
  
> Tips: 4-10步骤是在SystemServer执行的。


### Step 11: ApplicationThread.schedulePauseActivity
我们的launcher并没有finish，所以消息类型是H.PAUSE_ACTIVITY，我们看H的PAUSE_ACTIVITY处理，H是ActivityThread内部的一个Handler，用来处理SystemServer与本地应用的消息

	public final void schedulePauseActivity(IBinder token, boolean finished,
                boolean userLeaving, int configChanges, boolean dontReport) {
            int seq = getLifecycleSeq();
            if (DEBUG_ORDER) Slog.d(TAG, "pauseActivity " + ActivityThread.this
                    + " operation received seq: " + seq);
            sendMessage(
                    finished ? H.PAUSE_ACTIVITY_FINISHING : H.PAUSE_ACTIVITY,
                    token,
                    (userLeaving ? USER_LEAVING : 0) | (dontReport ? DONT_REPORT : 0),
                    configChanges,
                    seq);
                                        
        }

### Step 12: H.handleMessage
H处理PAUSE_ACTIVITY的消息，开始调用activity的方法handlePauseActivity
	
		case PAUSE_ACTIVITY: {
                    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "activityPause");
                    SomeArgs args = (SomeArgs) msg.obj;
                    handlePauseActivity((IBinder) args.arg1, false,
                            (args.argi1 & USER_LEAVING) != 0, args.argi2,
                            (args.argi1 & DONT_REPORT) != 0, args.argi3);
                    maybeSnapshot();
                    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                } break;
                
                
### Step 13: ActivityThread.handlePauseActivity
这个ActivityThread是怎么创建的，我现在也不知道，因为我们即将启动的activity也是创建一个ActivityThread，但是还没到我们启动呢，后面的源码分析会有看到。在这里，我们就是调用activity的一些方法了。ActivityClientRecord是在客户端唯一标记一个activity的数据结构，通过ams里的ActivityRecord作为键。performXXX就是对应activity的生命周期，都是通过Instrumentation调用对应activity的方法。

	private void handlePauseActivity(IBinder token, boolean finished,
            boolean userLeaving, int configChanges, boolean dontReport, int seq) {
        ActivityClientRecord r = mActivities.get(token);
        if (DEBUG_ORDER) Slog.d(TAG, "handlePauseActivity " + r + ", seq: " + seq);
        if (!checkAndUpdateLifecycleSeq(seq, r, "pauseActivity")) {
            return;
        }
        if (r != null) {
            //Slog.v(TAG, "userLeaving=" + userLeaving + " handling pause of " + r);
            
            if (userLeaving) {
                performUserLeavingActivity(r);
            }

            r.activity.mConfigChangeFlags |= configChanges;
            // 第二个参数是saveState，在暂停activity的时候，如果目标系统是小于3.0的
            // 就执行onSaveInstanceState,否则不执行
            performPauseActivity(token, finished, r.isPreHoneycomb(), "handlePauseActivity");

            // Make sure any pending writes are now committed.
            if (r.isPreHoneycomb()) {
                QueuedWork.waitToFinish();
            }

            // Tell the activity manager we have paused.
            if (!dontReport) {
                try {
                    ActivityManagerNative.getDefault().activityPaused(token);
                } catch (RemoteException ex) {
                    throw ex.rethrowFromSystemServer();
                }
            }
            mSomeActivitiesChanged = true;
        }
    }
    
    // 在Activity的对应的方法
	// performUserLeavingActivity
    final void performUserLeaving() {
        onUserInteraction();
        onUserLeaveHint();
    }
    
#### Step 13.1: ActivityThread.callCallActivityOnSaveInstanceState

发现一个有趣的东西，所以再分一小节说一下，我们都知道一个在某些情况下，系统会调用onSaveInstanceState方法，但是，我们都不是很清楚，这有些时候是什么时候，这里，我搜索了一下涉及到的几个地方。

* performPauseActivity,在执行onPause的时候，如果是3.0以下的系统，会执行
* performStopActivityInner, 在执行onStop的时候，都会执行
* handleSleeping, 在系统进入睡眠状态，且系统目标版本大于等于3.0,正在前台显示的, 同时状态state数据为null的情况下
* handleRelaunchActivity, 重新加载activity的时候，状态state数据为null的情况下，系统大于等于3.0，并且当前的activity在前台显示


#### Step 13.2 ActivityThread.performPauseActivity

暂停activity,又发现个新东西，针对onPause，原来有单独的回调注册，不过执行过一次之后就删除了，如果需要监听，需要重新注册。我们现在经常会用到监测整个应用的生命周期的回调ActivityLifecycleCallbacks，也是在onPause等对应的函数里回调的。


	final Bundle performPauseActivity(ActivityClientRecord r, boolean finished,
            boolean saveState, String reason) {
        if (r.paused) {
            if (r.activity.mFinished) {
                // If we are finishing, we won't call onResume() in certain cases.
                // So here we likewise don't want to call onPause() if the activity
                // isn't resumed.
                return null;
            }
            RuntimeException e = new RuntimeException(
                    "Performing pause of activity that is not resumed: "
                    + r.intent.getComponent().toShortString());
            Slog.e(TAG, e.getMessage(), e);
        }
        if (finished) {
            r.activity.mFinished = true;
        }

        // Next have the activity save its current state and managed dialogs...
        if (!r.activity.mFinished && saveState) {
            callCallActivityOnSaveInstanceState(r);
        }
		// 调用activiyt onPause方法
        performPauseActivityIfNeeded(r, reason);

        // Notify any outstanding on paused listeners
        ArrayList<OnActivityPausedListener> listeners;
        synchronized (mOnPauseListeners) {
            listeners = mOnPauseListeners.remove(r.activity);
        }
        int size = (listeners != null ? listeners.size() : 0);
        for (int i = 0; i < size; i++) {
            listeners.get(i).onPaused(r.activity);
        }

        return !r.activity.mFinished && saveState ? r.state : null;
    }

	
	// 最后在Activity执行的方法
	final void performPause() {
        mDoReportFullyDrawn = false;
        mFragments.dispatchPause();
        mCalled = false;
        onPause();
        mResumed = false;
        if (!mCalled && getApplicationInfo().targetSdkVersion
                >= android.os.Build.VERSION_CODES.GINGERBREAD) {
            throw new SuperNotCalledException(
                    "Activity " + mComponent.toShortString() +
                    " did not call through to super.onPause()");
        }
        mResumed = false;
    }
    

#### Step 13.3: ActivityThread.handlePauseActivity

在执行完onPause之后，我们就会通知AMS，我们暂停结束了，你可以继续了。

	// Tell the activity manager we have paused.
    if (!dontReport) {
        try {
            ActivityManagerNative.getDefault().activityPaused(token);
        } catch (RemoteException ex) {
             throw ex.rethrowFromSystemServer();
        }
     }
            
            
> Tips: 11-13步是在应用端执行的。

### Step 14: ActivityManagerService.activityPaused
通过token，这个token就是launcher组件

	@Override
    public final void activityPaused(IBinder token) {
        final long origId = Binder.clearCallingIdentity();
        synchronized(this) {
            ActivityStack stack = ActivityRecord.getStackLocked(token);
            if (stack != null) {
                stack.activityPausedLocked(token, false);
            }
        }
        Binder.restoreCallingIdentity(origId);
    }
    
### Step 15: ActivityStack.activityPausedLocked
这里的r就是我们的launcher组件，找到组件，我们remove掉之前添加的超时检测的消息，当前这个ActivityStack就是HOME_STACK_ID, 当前正在pasuing的activity就是launcher组件，所以，我们执行completePauseLocked

	final void activityPausedLocked(IBinder token, boolean timeout) {
        if (DEBUG_PAUSE) Slog.v(TAG_PAUSE,
            "Activity paused: token=" + token + ", timeout=" + timeout);

        final ActivityRecord r = isInStackLocked(token);
        if (r != null) {
            mHandler.removeMessages(PAUSE_TIMEOUT_MSG, r);
            if (mPausingActivity == r) {
                if (DEBUG_STATES) Slog.v(TAG_STATES, "Moving to PAUSED: " + r
                        + (timeout ? " (due to timeout)" : " (pause complete)"));
                completePauseLocked(true, null);
                return;
            } else {
                EventLog.writeEvent(EventLogTags.AM_FAILED_TO_PAUSE,
                        r.userId, System.identityHashCode(r), r.shortComponentName,
                        mPausingActivity != null
                            ? mPausingActivity.shortComponentName : "(none)");
                if (r.state == ActivityState.PAUSING) {
                    r.state = ActivityState.PAUSED;
                    if (r.finishing) {
                        if (DEBUG_PAUSE) Slog.v(TAG,
                                "Executing finish of failed to pause activity: " + r);
                        finishCurrentActivityLocked(r, FINISH_AFTER_VISIBLE, false);
                    }
                }
            }
        }
        mStackSupervisor.ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
    }

#### Step 15.1 ActivityStack.completePauseLocked
这一步也包含了onStop的调用过程，比较类似，就不再分析了，感兴趣的可以自己跟进去看一下，这里我们第二次进入resumeFocusedStackTopActivityLocked方法。

	private void completePauseLocked(boolean resumeNext, ActivityRecord resuming) {
        ActivityRecord prev = mPausingActivity;
        if (DEBUG_PAUSE) Slog.v(TAG_PAUSE, "Complete pause: " + prev);

        if (prev != null) {
            final boolean wasStopping = prev.state == ActivityState.STOPPING;
            // 更新launcher组件为paused状态
            prev.state = ActivityState.PAUSED;
            if (prev.finishing) {
                ...
            } else if (prev.app != null) {
                ...
                if (prev.deferRelaunchUntilPaused) {
                    ...
                } else if (wasStopping) {
                    ...
                } else if ((!prev.visible && !hasVisibleBehindActivity())
                        || mService.isSleepingOrShuttingDownLocked()) {
                    // If we were visible then resumeTopActivities will release resources before
                    // stopping.
                    // 执行这里，最后会发送一个消息，调用应用端的onStop，这里就不再分析这个了
                    // 由于onStop是发了一个消息，所以这里不会阻塞执行，所以这里可以稍微做点耗时的操作
                    // 但是也不适宜过长，系统给了10s的超时时间处理。
                    // 所以在下一个启动的activity的生命周期开始之前，前一个的onStop不一定执行完成，
                    // 下一个activity最好不要依赖前一个activity的onStop的操作结果
                    addToStopping(prev, true /* immediate */);
                }
            } else {
                ...
            }
            // 释放屏幕资源
            // It is possible the activity was freezing the screen before it was paused.
            // In that case go ahead and remove the freeze this activity has on the screen
            // since it is no longer visible.
            if (prev != null) {
                prev.stopFreezingScreenLocked(true /*force*/);
            }
            mPausingActivity = null;
        }

        // true, 开始启动我们的应用
        if (resumeNext) {
            // topStack为应用所在的stack，prev是launcher组件
            final ActivityStack topStack = mStackSupervisor.getFocusedStack();
            if (!mService.isSleepingOrShuttingDownLocked()) {
                // 执行这里
                mStackSupervisor.resumeFocusedStackTopActivityLocked(topStack, prev, null);
            } else {
                ...
            }
        }
        ...
        mStackSupervisor.ensureActivitiesVisibleLocked(resuming, 0, !PRESERVE_WINDOWS);
    }


### Step 16: ActivityStackSupervisor.resumeFocusedStackTopActivityLocked
再次回到这个函数，第二次进入这个函数，这次的主要工作就是开启正在启动的应用程序了。

#### Step16.1: ActivityStack.resumeTopActivityInnerLocked
最后执行的是ActivityStack的resumeTopActivityInnerLocked函数，我们直接看这个函数,由于没有这个将要启动的activity没有自己的进程，所以之后我们需要创建自己的__进程了__。

  	private boolean resumeTopActivityInnerLocked(ActivityRecord prev, ActivityOptions options) {
        ...

        // Launching this app's activity, make sure the app is no longer
        // considered stopped.
        // 这里涉及到Android的另外一个概念，停止状态
        // 在应用安装完之后，系统默认是处于停止状态的
        // 在启动应用的四大组件之后，应用就会设置应用处于非停止状态
        // 这是activity里的，其它四大组件的启动过程也有这个过程
        try {
            AppGlobals.getPackageManager().setPackageStoppedState(
                    next.packageName, false, next.userId); /* TODO: Verify if correct userid */
        } catch (RemoteException e1) {
        } catch (IllegalArgumentException e) {
            Slog.w(TAG, "Failed trying to unstop package "
                    + next.packageName + ": " + e);
        }

        ...
        // 获取到最上面的任务栈
        // 要启动的activity没有自己的县城，所以走else分支
        ActivityStack lastStack = mStackSupervisor.getLastStack();
        if (next.app != null && next.app.thread != null) {
            ...
        } else {
            // Whoops, need to restart this activity!
            if (!next.hasBeenLaunched) {
                next.hasBeenLaunched = true;
            } else {
                if (SHOW_APP_STARTING_PREVIEW) {
                    next.showStartingWindow(null, true);
                }
                if (DEBUG_SWITCH) Slog.v(TAG_SWITCH, "Restarting: " + next);
            }
            if (DEBUG_STATES) Slog.d(TAG_STATES, "resumeTopActivityLocked: Restarting " + next);
            mStackSupervisor.startSpecificActivityLocked(next, true, true);
        }

        if (DEBUG_STACK) mStackSupervisor.validateTopActivitiesLocked();
        return true;
    }

### Step 17: ActivityStackSupervisor.startSpecificActivityLocked
在这里，首先判断是否进程是否存在，存在的话，就直接启动这个activity, 否则就开始我们的创建进程,mService就是ActivityManagerService

	void startSpecificActivityLocked(ActivityRecord r,
            boolean andResume, boolean checkConfig) {
        // Is this activity's application already running?
        ProcessRecord app = mService.getProcessRecordLocked(r.processName,
                r.info.applicationInfo.uid, true);

        r.task.stack.setLaunchTime(r);

        if (app != null && app.thread != null) {
            ...
        }
        // 开始创建应用进程
        mService.startProcessLocked(r.processName, r.info.applicationInfo, true, 0,
                "activity", r.intent.getComponent(), false, false, true);
    }	
    
### Step 18: ActivityManagerService.startProcessLocked

#### Step 18.1: ActivityManagerService.startProcessLocked
在这个方法里，由于我们是第一次启动的，我们首先会创建一个ProcessRecord对象。

	final ProcessRecord startProcessLocked(String processName, ApplicationInfo info,
            boolean knownToBeDead, int intentFlags, String hostingType, ComponentName hostingName,
            boolean allowWhileBooting, boolean isolated, int isolatedUid, boolean keepIfLarge,
            String abiOverride, String entryPoint, String[] entryPointArgs, Runnable crashHandler) {
        long startTime = SystemClock.elapsedRealtime();
        ProcessRecord app;
        
        ...
        
        // 新启动的没有进程, 进入该分支
        if (app == null) {
            checkTime(startTime, "startProcess: creating new process record");
            // 创建一个ProcessRecord进程对象，用来标识应用对应的进程
            app = newProcessRecordLocked(info, processName, isolated, isolatedUid);
            if (app == null) {
                Slog.w(TAG, "Failed making new process record for "
                        + processName + "/" + info.uid + " isolated=" + isolated);
                return null;
            }
            app.crashHandler = crashHandler;
            checkTime(startTime, "startProcess: done creating new process record");
        } else {
            // If this is a new package in the process, add the package to the list
            app.addPackage(info.packageName, info.versionCode, mProcessStats);
            checkTime(startTime, "startProcess: added package to existing proc");
        }

        ...
        // 真正创建进程的方法
        startProcessLocked(
                app, hostingType, hostingNameStr, abiOverride, entryPoint, entryPointArgs);

        return (app.pid != 0) ? app : null;
    }

#### Step 18.2: ActivityManagerService.newProcessRecordLocked

在这里，我们又可以看到我们众所周知的一个知识点，就是如果组件没有声明进程，就是在同一个进程中，也就是跟应用包名相同的进程。我们创建了一个ProcessRecord对象，用来唯一标识一个应用对应的进程

	final ProcessRecord newProcessRecordLocked(ApplicationInfo info, String customProcess,
            boolean isolated, int isolatedUid) {
        // 没有自定义包名，就使用应用的包名创建进程
        String proc = customProcess != null ? customProcess : info.processName;
        BatteryStatsImpl stats = mBatteryStatsService.getActiveStatistics();
        final int userId = UserHandle.getUserId(info.uid);
        int uid = info.uid;
        if (isolated) {
            ...
        }
        final ProcessRecord r = new ProcessRecord(stats, info, proc, uid);
        if (!mBooted && !mBooting
                && userId == UserHandle.USER_SYSTEM
                && (info.flags & PERSISTENT_MASK) == PERSISTENT_MASK) {
            r.persistent = true;
        }
        addProcessNameLocked(r);
        return r;
    }

#### Step 18.3: ActivityManagerService.addProcessNameLocked
uid在android系统中对应的就是一个应用，mActiveUids获取到是否已经激活过这个应用，没有就创建一个，新建的进程更新uid属性，表示这个进程是属于哪个应用的，更新UidRecord运行的进程数。

	private final void addProcessNameLocked(ProcessRecord proc) {
        // We shouldn't already have a process under this name, but just in case we
        // need to clean up whatever may be there now.
        ProcessRecord old = removeProcessNameLocked(proc.processName, proc.uid);
        if (old == proc && proc.persistent) {
            // We are re-adding a persistent process.  Whatevs!  Just leave it there.
            Slog.w(TAG, "Re-adding persistent process " + proc);
        } else if (old != null) {
            Slog.wtf(TAG, "Already have existing proc " + old + " when adding " + proc);
        }
        UidRecord uidRec = mActiveUids.get(proc.uid);
        // 应用没有启动过, 新建标识应用的对象
        if (uidRec == null) {
            uidRec = new UidRecord(proc.uid);
            // This is the first appearance of the uid, report it now!
            if (DEBUG_UID_OBSERVERS) Slog.i(TAG_UID_OBSERVERS,
                    "Creating new process uid: " + uidRec);
            mActiveUids.put(proc.uid, uidRec);
            noteUidProcessState(uidRec.uid, uidRec.curProcState);
            enqueueUidChangeLocked(uidRec, -1, UidRecord.CHANGE_ACTIVE);
        }
        proc.uidRecord = uidRec;

        // Reset render thread tid if it was already set, so new process can set it again.
        proc.renderThreadTid = 0;
        // 更新应用包含的进程数
        uidRec.numProcs++;
        mProcessNames.put(proc.processName, proc.uid, proc);
        if (proc.isolated) {
            mIsolatedProcesses.put(proc.uid, proc);
        }
    }

#### Step 18.4: ActivityManagerService.startProcessLocked
此startProcessLocked非彼startProcessLocked，这里是第一小步里重载的方法，在这里创建真正的进程。这个在代码行3671的位置，到这里为止，我们启动的应用程序创建的了自己的进程。这里的创建过程涉及到linux的fork的理解，系统如何创建进程的过程，我单独在[StartPorcess](StartPorcess.md)中有详细说明。

	    private final void startProcessLocked(ProcessRecord app, String hostingType,
            String hostingNameStr, String abiOverride, String entryPoint, String[] entryPointArgs) {
        ...

        try {
            try {
                final int userId = UserHandle.getUserId(app.uid);
                AppGlobals.getPackageManager().checkPackageStartable(app.info.packageName, userId);
            } catch (RemoteException e) {
                throw e.rethrowAsRuntimeException();
            }

            int uid = app.uid;
            int[] gids = null;
            int mountExternal = Zygote.MOUNT_EXTERNAL_NONE;
            // 更新应用需要的权限gids
            if (!app.isolated) {
                int[] permGids = null;
                try {
                    checkTime(startTime, "startProcess: getting gids from package manager");
                    final IPackageManager pm = AppGlobals.getPackageManager();
                    // permGids表示用户权限分组
                    permGids = pm.getPackageGids(app.info.packageName,
                            MATCH_DEBUG_TRIAGED_MISSING, app.userId);
                    // 存储空间服务，获取外置sd卡状态
                    MountServiceInternal mountServiceInternal = LocalServices.getService(
                            MountServiceInternal.class);
                    mountExternal = mountServiceInternal.getExternalStorageMountMode(uid,
                            app.info.packageName);
                } catch (RemoteException e) {
                    throw e.rethrowAsRuntimeException();
                }

                /*
                 * Add shared application and profile GIDs so applications can share some
                 * resources like shared libraries and access user-wide resources
                 */
                if (ArrayUtils.isEmpty(permGids)) {
                    gids = new int[2];
                } else {
                    gids = new int[permGids.length + 2];
                    System.arraycopy(permGids, 0, gids, 2, permGids.length);
                }
                //
                gids[0] = UserHandle.getSharedAppGid(UserHandle.getAppId(uid));
                gids[1] = UserHandle.getUserGid(UserHandle.getUserId(uid));
            }
            // 配置一系列调试参数
            int debugFlags = 0;
            ...

            // 配置应用的权限，架构，虚拟机参数
            app.gids = gids;
            app.requiredAbi = requiredAbi;
            app.instructionSet = instructionSet;

            // Start the process.  It will either succeed and return a result containing
            // the PID of the new process, or else throw a RuntimeException.
            boolean isActivityProcess = (entryPoint == null);
            // 启动应用进程的类
            if (entryPoint == null) entryPoint = "android.app.ActivityThread";
            // 启动进程，启动进程就是通过zygote进程创建一个新的应用进程
            Process.ProcessStartResult startResult = Process.start(entryPoint,
                    app.processName, uid, uid, gids, debugFlags, mountExternal,
                    app.info.targetSdkVersion, app.info.seinfo, requiredAbi, instructionSet,
                    app.info.dataDir, entryPointArgs);
        
            // 电池状态加入当前应用程序
            mBatteryStatsService.noteProcessStart(app.processName, app.info.uid);

            synchronized (mPidsSelfLocked) {
                this.mPidsSelfLocked.put(startResult.pid, app);
                if (isActivityProcess) {
                    Message msg = mHandler.obtainMessage(PROC_START_TIMEOUT_MSG);
                    msg.obj = app;
                    mHandler.sendMessageDelayed(msg, startResult.usingWrapper
                            ? PROC_START_TIMEOUT_WITH_WRAPPER : PROC_START_TIMEOUT);
                }
            }
            checkTime(startTime, "startProcess: done updating pids map");
        } catch (RuntimeException e) {
            // 强制停止应用
            forceStopPackageLocked(app.info.packageName, UserHandle.getAppId(app.uid), false,
                    false, true, false, false, UserHandle.getUserId(app.userId), "start failure");
        }
    }
    
   
### Step 19: ActivityThread.main



