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
	float ops_thres, ops_thres_2;
	int ins_count, del_count, find_count;

	public SkiplistTest(int x, int operations, SkiplistRotating sl, float f, float f2) {
		super();
		this.x = x;
		this.operations = operations;
		this.sl = sl;
		this.ops_thres = f;
		this.ops_thres_2 = f2;
		this.ins_count = 0;
		this.del_count = 0;
		this.find_count = 0;
	}

	public void run() {
		System.out.print("Start thread " + currentThread().getId());
		System.out.println(" (factor: " + x + ")");
		Random r = new Random();
		Random ops_r = new Random();
		// SkiplistRotating sl = new SkiplistRotating();
		if (null == sl) {
			System.out.println("Skiplist not initialized on thread "
					+ currentThread().getId() + " Aborting...");
			return;
		}
		
		/* Random test */
		for (int i = operations * x; i < operations * (x + 1); i++) {
			float ops = ops_r.nextFloat();
			if (ops < ops_thres) {
				sl.insert(i, r.nextInt(100));
				++ins_count;
			}
			else if (ops < ops_thres_2 + ops_thres) {
				sl.contains(i);
				++find_count;
			}
			else {
				sl.delete(i);
				++del_count;
			}
		}
		System.out.println("Thread " + currentThread().getId() + ":");
		System.out.println("insert: " + ins_count);
		System.out.println("search: " + find_count);
		System.out.println("delete: " + del_count);
		
		/* Sanity test */
		/*
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
		*/
		
		/*
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
		System.out.println("Delete compeleted on " + currentThread().getId());*/

		System.out.println("Finish on " + currentThread().getId());
	}
}