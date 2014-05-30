主要源代码文件为：
  SkiplistRotating.java 实现了No Hotspot Skip List。Public 接口为 insert(key, value); contains(key); delete(key).
  对于一个Skiplist实例，有一个后台线程（background thread）对Skiplist进行维护，包括删除无用节点，提升或降低Skiplist层数。Background thread的运行间隔可以通过bg_sleep_time进行调整，对于降低Skiplist层数，也可以调整相关阈值参数。
  Skiplist的具体实现中，每个节点（Node）都维护其所有后继者(Successor/Succ）列表。在这种设计下，Skiplist的索引（Index）是隐式的，Index操作通过Node中记录的信息来维护。
  为了保证Skiplist在多个线程下操作的正确性，在删除Node的时候，会添加一个额外辅助Node（Marker），使得background thread在后台维护Skiplist的时候可以保证前台的workers在操作Nodes时的正确性。在进行Node的插入和删除，以及提升或降低Skiplist层数操作时，通过CAS（compare and swap）原语保证操作的原子性，同时避免锁（Lock）的使用。

主要测试源代码为
  SkiplistTest.java
  Main.java
  对Skip List的随机插入、查找和删除的操作组合进行性能测试以及正确性测试。可以调节参数为：
    threadNum：worker线程数
    totalops/ops：（所有/每个）线程总共执行的操作数
    insert_chance：当前随机操作为insert的概率
    search_chance：当前随机操作为constains的概率
    (delete_chance = 100% - insert_chance - search_chance）