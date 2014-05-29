package skiplist;

import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import util.MyConcurrentSkipListMap;
import util.SkiplistRotating;
import java.lang.Thread;
//import java.util.concurrent.ConcurrentSkipListMap;

public class SkiplistTest extends Thread {

    int x;

    public SkiplistTest(int x) {
        super();
        this.x = x;
    }
	
	public void run() {
        System.out.println("Start thread " + currentThread().getId());
        System.out.println(" " + x);
        SkiplistRotating sl = new SkiplistRotating();
        sl.insert(3 + x, "A");
        sl.insert(2 + x, "B");
        sl.insert(1 + x, "C");
        sl.insert(5 + x, "D");
        sl.insert(4 + x, "E");

        for (int i = 1; i < 6; i++)  {
            int idx = i + x + 2;
            int ret = sl.contains(idx);
            System.out.println("test if contains" + idx);
            if (i < 4) assert (ret == 1);
            else assert(ret == 0);
        }
        System.out.println("Finish on " + currentThread().getId());
	}
}