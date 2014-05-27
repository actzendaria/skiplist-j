package util;

import util.ThreadState;
import util.MyConcurrentSkipListMap.Node;

import java.util.*;
import java.lang.Thread;
import java.lang.ThreadLocal;
import java.lang.reflect.Field;


public class ThreadStateManager {
	
	/* enter/leave a critical section - a thread gets a state handle
	 * for use during critical regions 
	 */
	public static ThreadState thrst_critical_enter() {};
	
	@SuppressWarnings("unused")
	public static ThreadLocal<Integer> perthrkey = new ThreadLocal<Integer>() {
		public Integer initialValue() {
			return 0;
		}
	};
	
	public static List<ThreadState> thrst_list = null;
	public static void thrst_subsys_init() {};
	
	/* Initialize the global variable once and only once */
    static {
        try {
        	thrst_list = new ArrayList<ThreadState>();
        } catch (Exception e) {
     	   e.printStackTrace();
           throw new Error(e);
        }
    }
}
