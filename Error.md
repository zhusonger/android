# 功能
总结平时开发中遇到的问题，以便下次碰到可以直接找到原因。

## 1. 编译IJKPlayer时, 调用./init-android.sh下载到最后出现的错误
error: RPC failed; curl 56 SSLRead() return error -36 1.34 MiB/s      
fatal: The remote end hung up unexpectedly
fatal: early EOF
fatal: index-pack failed

``
	这个是因为GFW导致的，请科学上网之后再试.
``