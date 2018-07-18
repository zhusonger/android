# Android 项目maven私服使用指南
## 1. 地址:
* maven浏览器地址: <http://192.168.16.174:7071/nexus/index.html>

## 2. 帐号
* 触手tv: android_chushou/123456
* 触手录: android_chushoulu/123456
* 触手sdk: android_chushousdk/123456

	_Tips:密码默认为123456,请自己修改_

## 3.配置
* 在根项目目录修改gradle.properties，添加
	MAVEN_URL=http://192.168.16.174:7071/nexus/content/repositories/release_android/
	
	MAVEN_SNAPSHOT_URL=http://192.168.16.174:7071/nexus/content/repositories/snapshots_android/
	
	NEXUS_USERNAME=android_chushoulu
	
	NEXUS_PASSWORD=123456
	
* 拷贝upload.gradle到根项目目录
* 在module下新建gradle.properties文件添加上传属性,根据自己的项目信息修改,version后缀加上-SNAPSHOT就是上传到快照库中

	GROUPID=com.chushoulu.maven
	
	ARTIFACTID=test
	
	VERSION=1.0.2
	
	DESCRIPTION=maven test
* 在上传module的build.gradle添加应用,apply from : "$project.rootDir/upload.gradle"
* 双击执行对应modlule下的task，task位于other分组下的buildAndUploadRepo

	__Tips:涉及文件在同级包内__
	
## 4.使用
* 在根项目目录的build.gradle，修改

```
allprojects {
    repositories {
        jcenter()
        maven { url MAVEN_URL }
    }
}
```
* 在module项目中,使用compile ‘com.chushoulu.maven:test:1.0.2’的方式加载库




