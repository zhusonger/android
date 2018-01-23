#Fork说明

参考文章:

* <http://blog.csdn.net/jason314/article/details/5640969>
* <http://www.cnblogs.com/biyeymyhjob/archive/2012/07/20/2601655.html>

在linux中，如果想要复制一个当前进程，用的最多的就是fork,中译“分叉”
![](WX20180122-180956.png =400x)

		#include <unistd.h>
		#include <stdio.h> 
		int main () 
		{ 
			pid_t fpid; //fpid表示fork函数返回的值
			int count=0;
			fpid=fork(); 
			if (fpid < 0) 
				printf("error in fork!"); 
			else if (fpid == 0) {
				printf("i am the child process, my process id is %d/n",getpid()); 
				printf("我是爹的儿子/n");//对某些人来说中文看着更直白。
				count++;
			}
			else {
				printf("i am the parent process, my process id is %d/n",getpid()); 
				printf("我是孩子他爹/n");
				count++;
			}
			printf("统计结果是: %d/n",count);
			return 0;
		}
		
		
这个是参考文章里截取的一部分测试代码