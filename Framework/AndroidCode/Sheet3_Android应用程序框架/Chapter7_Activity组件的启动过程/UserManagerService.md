# Android多用户系统的说明

参考链接:	

* <http://blog.csdn.net/vshuang/article/details/43639211>

Android系统在4.2开始支持多用户，在我们平时的手机上，可能没看到有多用户的入口，但是在官方系统上，在设置里有个用户栏

 ![](WX20180115-173035.png) 

 ![](WX20180115-173229.png)

在这里可以添加用户，这里有3个概念。

* userid: 用户id，标志一个用户，就是我上面说的用户的概念，比如在UserHandle中定义当前使用的用户为-2,USER_SYSTEM/USER_OWNER为0

* appid: 应用id，唯一标识一个应用，这个在应用安装的时候就确定了。这个值的范围是0～100000，就是说每个用户最多只能安装100000个应用
	
    	public static final int PER_USER_RANGE = 100000;

* uid: 一个应用的uid，唯一表示这个应用，它的值跟用户id有关，计算规则是userid * 100000 + appId