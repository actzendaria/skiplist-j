package skiplist;

import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import util.MyConcurrentSkipListMap;
import util.SkiplistRotating;
import java.lang.Thread;
import java.util.Random;
//import java.util.concurrent.ConcurrentSkipListMap;

public class SkiplistTest extends Thread {

    int x;
    int operations;
    SkiplistRotating sl;

    public SkiplistTest(int x, int operations, SkiplistRotating sl) {
        super();
        this.x = x;
        this.operations = operations;
        this.sl = sl;
    }
	
	public void run() {
        System.out.println("Start thread " + currentThread().getId());
        System.out.println(" " + x);
        Random r = new Random();
        SkiplistRotating sl = new SkiplistRotating();
        for (int i = operations * x; i < operations * (x + 1); i++)
            sl.insert(i, r.nextInt(100));
        System.out.println("Insert completed on " + currentThread().getId());
        for (int i = operations * x; i < operations * (x + 1); i++) {
            int ret = sl.contains(i);
            if (ret != 1) {
                throw new RuntimeException();
            }
        }
        System.out.println("Contains completed on " + currentThread().getId());
        for (int i = operations * x; i < operations * (x + 1); i++) {
            int ret = sl.delete(i);
            if (ret != 1)
                throw new RuntimeException();
        }
        for (int i = operations * x; i < operations * (x + 1); i++) {
            int ret = sl.contains(i);
            if (ret != 0) {
                throw new RuntimeException();
            }
        }
        System.out.println("Delete compeleted on " + currentThread().getId());

        System.out.println("Finish on " + currentThread().getId());
	}
}