package util;
import java.lang.Thread;
import java.lang.ThreadLocal;

public class ThreadState {
	int id;
	
	/* state management */
	ThreadState next;
	int count;
	
	/* utility structures */
	/* garbage_collection states */
	
	
	public ThreadState() {
		id = 0;
		next = null;
		count = 0;
	}
}
