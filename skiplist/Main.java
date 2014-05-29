package skiplist;

public class Main {
	public static void main(String[] args) {
        for (int i = 0; i < 6; i++) {
            SkiplistTest t = new SkiplistTest(i * 10);
            t.start();
        }
        System.out.println("Congratulations!");
	}
}
