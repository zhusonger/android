# 一、源码编译
----

## 1. FQ
 第一步是自备梯子, 完。


## 2. repo
* 下载google写的一个脚本 <https://storage.googleapis.com/git-repo-downloads/repo>
* 设置脚本执行权限 chmod +x repo
* 如果想以后用起来方便，在.bash_profile配置一下

## 3. 创建目录
* 在你想放置android源码的地方创建文件夹, mkdir $WORK_DIRECTORY
* cd到创建的文件夹目录后, 下载android源码列表, 我现在下载的是2.3.1的android稳定版本，学习的那本书是以这个为基础的，-b指定要同步的android源码的版本号,不添加默认为开发中的那个分支。``repo init -u https://android.googlesource.com/platform/manifest -b android-2.3.1_r1 --repo-url=https://gerrit-google.tuna.tsinghua.edu.cn/git-repo``
* 下载源码, 这个比较费时间, 挂着吧。``repo sync``

__使用2.3.1的另外一个原因是新的系统太大啦，学习的话，学习到精髓就好了,所以，我选择空间小的，哈哈哈哈__

> 这个的时间会比较久，前面的6个章节都是关于系统底层c代码的，所以，在我们等待下载的时候，可以直接从第7章开始看

## 4. 编译
在下载目录执行make,因为用的是比较老的android系统，当时脚本对应的编译环境也是相对较老的，所以在编译的过程中会需要你安装很多老的环境，下面是我碰到了一些问题：

1. _You are building on a case-insensitive filesystem._ 系统大小写不敏感，我们创建一个大小写敏感的空间
	
	* 创建大小写敏感的分区:``hdiutil create -type SPARSE -fs 'Case-sensitive Journaled HFS+' -size 40g ~/android.dmg``
	
	* 调整分区大小:``hdiutil resize -size <new-size-you-want>g ~/android.dmg.sparseimage``
	
	* 加载映像:``function mountAndroid { hdiutil attach ~/android.dmg.sparseimage -mountpoint /Volumes/Android; }``

	* 卸载映像:``function umountAndroid() { hdiutil detach /Volumes/Android; }``
	
	* 进入映像目录:``cd /Volumes/Android/``
	
	_Tips: 可以把加载/卸载映像添加到.bash_profile里，方便我们加载与卸载_
	
	更多详细说明:<https://source.android.com/source/initializing#setting-up-a-mac-os-x-build-environment>

2. __You are attempting to build with the incorrect version of java.__ 编译的jdk版本不对，我的是jdk1.8, 它需要的是jdk1.6,所以我们再安装一个jdk1.6
	* 下载jdk1.6安装包: <http://support.apple.com/kb/DL1572>
	* 在.bash_profile配置jdk切换命令: 
	
			#jdk
			export JDK6_HOME=/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home  
			export JDK8_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_101.jdk/Contents/Home
			#alias命令动态切换JDK版本  
			alias jdk6="export JAVA_HOME=$JDK6_HOME"
			alias jdk8="export JAVA_HOME=$JDK8_HOME"
	* 在终端运行jdk6, 那当前终端就是jdk1.6的环境了
	
	更多详细说明:<http://chessman-126-com.iteye.com/blog/2162466>

3. __Please install the 10.5 SDK on this machine at /Developer/SDKs/MacOSX10.5.sdk__
	* 安装xcode3 & xcode4
	
	更多详细说明:<http://blog.csdn.net/dragon1225/article/details/7061076>
