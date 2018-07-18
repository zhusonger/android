# 题目
给定两个非空链表来代表两个非负整数，位数按照逆序方式存储，它们的每个节点只存储单个数字。将这两数相加会返回一个新的链表。

你可以假设除了数字 0 之外，这两个数字都不会以零开头。

示例：

输入：(2 -> 4 -> 3) + (5 -> 6 -> 4)
输出：7 -> 0 -> 8
原因：342 + 465 = 807

# 分析
在开始的时候，涉及到数值运算，尝试使用与对应位数的进制相乘，最后再相除获取到每一位的值，后来一想，计算虽然是按照位数计算的，但是这里其实就是每一个位数相加就行了。

## 需要考虑的点
1. 2个链表长度不同的情况
2. 每一位相加之后超过个位数的情况
3. 考虑到超过2位数是会到下一个节点的，所以不需要考虑相加之后是3位数的情况

# 错误1
```
public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
        if (l1 == null || l2 == null) {
            return null;
        }
        ListNode previous = null;
        ListNode first = null;
        int outNum = 0;
        while(null != l1 || l2 != null || outNum > 0) {
            int l1Val = null != l1 ? l1.val : 0;
            int l2Val = null != l2 ? l2.val : 0;
            int v = l1Val + l2Val + outNum;
            if (v > 9) {
                v = v % 10;
                outNum = v / 10;
            } else {
                outNum = 0;
            }
            ListNode node = new ListNode(v);
            if (null == first) {
                first = node;
            }
            if (null != previous) {
                previous.next = node;
            }
            previous = node;
            l1 = null != l1 ? l1.next : null;
            l2 = null != l2 ? l2.next : null;
        }
        return first;
    }
```  

一开始没找到错误，总是在测试例子里第三位没有加上溢出的数值，debug之后发现这里把v的值改掉了，调换下顺序即可
```
v = v % 10;
outNum = v / 10;
```

# 正确
```
public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
        if (l1 == null || l2 == null) {
            return null;
        }
        ListNode previous = null;
        ListNode first = null;
        int outNum = 0;
        while(null != l1 || l2 != null || outNum > 0) {
            int l1Val = null != l1 ? l1.val : 0;
            int l2Val = null != l2 ? l2.val : 0;
            int v = l1Val + l2Val + outNum;
            if (v > 9) {
                outNum = v / 10;
                v = v % 10;
            } else {
                outNum = 0;
            }
            ListNode node = new ListNode(v);
            if (null == first) {
                first = node;
            }
            if (null != previous) {
                previous.next = node;
            }
            previous = node;
            l1 = null != l1 ? l1.next : null;
            l2 = null != l2 ? l2.next : null;
        }
        return first;
    }
```