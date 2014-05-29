package skiplist;

public class Main {

    static final int threadNum = 6;

	public static void main(String[] args) throws InterruptedException {
        SkiplistTest[] ts = new SkiplistTest[threadNum];
        long startTime = System.currentTimeMillis();
        int ops = 100;
        for (int i = 0; i < threadNum; i++) {
            ts[i] = new SkiplistTest(i, ops);
            ts[i].start();
        }
        for (int i = 0; i < threadNum; i++) {
            ts[i].join();
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Congratulations! time = " + (endTime - startTime) + " ms");
	}
}
