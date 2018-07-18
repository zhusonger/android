# 题目

给定一个整数数列，找出其中和为特定值的那两个数。

你可以假设每个输入都只会有一种答案，同样的元素不能被重用。

示例:

给定 nums = [2, 7, 11, 15], target = 9

因为 nums[0] + nums[1] = 2 + 7 = 9
所以返回 [0, 1]

# 解答过程

## 错误1
```
public int[] twoSum(int[] nums, int target) {
        int[] ret = new int[2];
        int n = nums.length;
        for(int i = 0; i < n; i++) {
            int vi = nums[i];
            for (int j = 1; j < n; j++) {
                int vj = nums[j];
                if (vi + vj == target) {
                    ret[0] = i;
                    ret[1] = j;
                    break;
                }
            }
        }
        
        return ret;
    }
```  
在这里，我才发现，我居然不知道break只是跳出一重循环!!! 太多时候通过return来跳出多重循环了。
所以特意去搜了下跳出多重循环的方式。
<https://blog.csdn.net/qq_37107280/article/details/73556419>

## 错误2
 ```
public int[] twoSum(int[] nums, int target) {
        int[] ret = new int[2];
        int n = nums.length;
        here:
        for(int i = 0; i < n; i++) {
            int vi = nums[i];
            for (int j = 1; j < n; j++) {
                int vj = nums[j];
                if (vi + vj == target) {
                    ret[0] = i;
                    ret[1] = j;
                    break here;
                }
            }
        }
        
        return ret;
    }
```
这里是能跳出循环了，但是忽略了一种情况，就是i跟j相同时，正好等于target, 所以还是有问题，题目要的是2个数。

## 正确
```
 public int[] twoSum(int[] nums, int target) {
        int[] ret = new int[2];
        int n = nums.length;
        here:
        for(int i = 0; i < n; i++) {
            int vi = nums[i];
            for (int j = 1; j < n; j++) {
                int vj = nums[j];
                if (vi + vj == target && i != j) {
                    ret[0] = i;
                    ret[1] = j;
                    break here;
                }
            }
        }
        
        return ret;
    }
```  
