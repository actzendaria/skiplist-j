package skiplist;

import util.SkiplistRotating;

public class Main {

    static final int threadNum = 6;

	public static void main(String[] args) throws InterruptedException {
        SkiplistRotating[] ss = new SkiplistRotating[threadNum];
        SkiplistTest[] ts = new SkiplistTest[threadNum];
        long startTime = System.currentTimeMillis();
        int ops = 100000;
        for (int i = 0; i < threadNum; i++) {
            ss[i] = new SkiplistRotating();
        }
        for (int i = 0; i < threadNum; i++) {
            ts[i] = new SkiplistTest(i, ops, ss[i]);
            ts[i].start();
        }
        for (int i = 0; i < threadNum; i++) {
            ts[i].join();
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Congratulations! time = " + (endTime - startTime) + " ms");
	}
}
