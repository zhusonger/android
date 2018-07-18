# 循环
在Java中, 循环的方式有while、for循环。这里我们着重理解下for循环。
for循环在JDK1.5之前，只有传统的for循环遍历 ，在JDK1.5之后，引入了for-each循环，这个语法糖的引入是为了减少在传统for循环定义循环变量的时候，由于粗心引起的错误。

## 传统for循环
```
  for(Iterator<Element> i = c.iterator(); i.hasNext();) {
    doSomething(i.next());
  }
``` 

## for-each循环
```
  for(Element i : c) {
    doSomething(i);
  }
```  
在编译器编译代码的时候，最后会把for-each转换成传统的for循环，所以其实他们之间是等价的，但是for-each更简洁，不容易出错。

---

# 数据类型
在定义一个数据类型的时候，如果是一个数据集合，最好就是实现Iterator接口，来支持for-each循环。

---

# 陷阱
如果只是普通的遍历获取数据，用for-each是一个很好的选择。但是在开发中，经常会更新或者删除数据集的数据，那for-each就不适合了，因为不提供删除的方法。

### 更新数据

  1. 如果是遍历更新数据是更新数据集对象内的数据，即不改变数据对象在数据集合中的对象，那还是可以通过for-each遍历更新对象内的数据。

  2. 如果是需要把数据集合中的对象更改，那还是需要用传统的循环 

### 删数据
如果是删数据，以list为例，需要删除某个元素，需要使用Iterator来删除，而不能使用找到数据索引之后，直接通过源list删除指定索引来实现，因为在循环中，你正在遍历数据，而同时你又在更改数组，那就会导致数组数据不可预测。

* ConcurrentModificationException 异常原因:<https://www.cnblogs.com/dolphin0520/p/3933551.html>

# 循环中删数据
这个需求很常见，针对这个需求，就需要通过Iterator的remove方法，这个类里面的remove方法会更新modCount与expectedModCount，避免出现ConcurrentModificationException。

在多线程的情况下，一种是通过synchronized关键字或者使用线程安全的数据结构，比如CopyOnWriteArrayList，内部实现也是synchronized实现的。