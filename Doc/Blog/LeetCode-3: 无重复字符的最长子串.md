# 题目
给定一个字符串，找出不含有重复字符的 最长子串 的长度。

示例：

给定 "abcabcbb" ，没有重复字符的最长子串是 "abc" ，那么长度就是3。

给定 "bbbbb" ，最长的子串就是 "b" ，长度是1。

给定 "pwwkew" ，最长子串是 "wke" ，长度是3。请注意答案必须是一个子串，"pwke" 是 子序列 而不是子串。



# 分析
判断是否重复，第一个想到的数据结构是set跟map,他们可以判断是否存在重复，set更纯粹，所以我们这里用set来做重复判断，这里没有要求输出结果，只要求个数，所以用什么set无所谓，否则就用LinkedHashSet，这个数据集合是一个链表的形式保存的数据，所以可以记录有序的数据。

从头开始遍历字符串，碰到不重复的，就往set里添加，否则就清空，并使用开始计算是否重复的下标重新开始遍历，知道字符串结尾。

## 错误1
```
public int lengthOfLongestSubstring(String s) {
        char[] array = s.toCharArray();
        Set<Character> set = new LinkedHashSet<>();
        int maxSize = 0;
        for (int i = 0, len = array.length, start = 0; i < len; i++) {
            char v = array[i];
            if (set.contains(v)) {
                maxSize = Math.max(maxSize, set.size());
                set.clear();
                start++;
                i = start;
            } else {
                set.add(v);
            }
        }
        return maxSize;
    }
```  

这里忽略了只有1个字符的情况，所以最后再做一步与set的大小比较，取大值

## 错误2
```
public int lengthOfLongestSubstring(String s) {
        char[] array = s.toCharArray();
        Set<Character> set = new LinkedHashSet<>();
        int maxSize = 0;
        for (int i = 0, len = array.length, start = 0; i < len; i++) {
            char v = array[i];
            if (set.contains(v)) {
                maxSize = Math.max(maxSize, set.size());
                set.clear();
                start++;
                i = start;
            } else {
                set.add(v);
            }
        }
        maxSize = Math.max(maxSize, set.size());
        return maxSize;
    }
```  

这里在碰到重复的时候，我的本意是i更新为从start的位置开始，确忽略了最后一步i会递增, 导致i是在start+1之后开始遍历，导致不正确的结果。调换一下顺序，让遍历下标通过i++来更新。

## 正确
```
public int lengthOfLongestSubstring(String s) {
        char[] array = s.toCharArray();
        Set<Character> set = new LinkedHashSet<>();
        int maxSize = 0;
        for (int i = 0, len = array.length, start = 0; i < len; i++) {
            char v = array[i];
            if (set.contains(v)) {
                maxSize = Math.max(maxSize, set.size());
                set.clear();
                i = start;
                start++;
            } else {
                set.add(v);
            }
        }
        maxSize = Math.max(maxSize, set.size());
        return maxSize;
    }
```  

