package util;

import util.ThreadState;
import util.MyConcurrentSkipListMap.Node;

import java.util.*;
import java.lang.Thread;
import java.lang.ThreadLocal;
import java.lang.reflect.Field;


public class ThreadStateManager {
	@SuppressWarnings("unused")
	public static ThreadLocal<ThreadState> perthrkey = new ThreadLocal<ThreadState>() {
		public ThreadState initialValue() {
			return null;
		}
	};
	
	//public static List<ThreadState> thrst_list = new ArrayList<ThreadState>();
	public static ThreadState thrst_list = null;
	static int next_id;
	
	/* - Private function definitions - */

	/**
	 * ptst_destructor - reclaim a recently released per-thread state
	 * @ptst: the per-thread state to reclaim
	 */
	static void ptst_destructor(ThreadState thrst)
	{
		thrst.count = 0;
	}
	
	/**
	 * enter/leave a critical section - a thread gets a state handle
	 * for use during critical regions 
	 */

	private static ThreadState thrst_first(){
		return thrst_list;
	}

	public static ThreadState thrst_critical_enter() {
		ThreadState thrst, next;
        int id;

        thrst = perthrkey.get();
        
        if (null == thrst) {
        	thrst = thrst_first();
        	for ( ; null != thrst; thrst = thrst.next) {
        		if (0 == thrst.count) {
        			/* originally a CAS op */
        			if (thrst.count == 0) {
        				thrst.count = 1;
        				break;
        			}
        		}
        	}

        	if (null == thrst) {
        		thrst = new ThreadState();
        		/*
        		memset(ptst, 0, sizeof(*ptst));
        		ptst->gc = gc_init();
        		ptst->count = 1;*/
        		thrst.count = 1;
        		id = next_id;
        		/*while ((!CAS(&next_id, id, id+1)))
        			id = next_id;*/
        		while (next_id != id) {
        			id = next_id;
        		}
        		next_id = id + 1;

        		thrst.id = id;
        		
        		/*
        		do {
        			next = thrst_list;
        			thrst->next = next;
        		} while (!CAS(&ptst_list, next, ptst));
        		*/
        		do {
        			next = thrst_list;
        			thrst.next = next;
        		}
        		while (thrst_list != next);
        		thrst_list = thrst;	
        	}
        	//pthread_setspecific(ptst_key, ptst);
        	perthrkey.set(thrst);
        }

        //gc_enter(ptst);
        thrst.count++;
        // BARRIER()
        return thrst;
    }

	public static void thrst_critical_exit(ThreadState thrst) {
		//gc_exit(ptst);
		// BARRIER()
		thrst.count--;
	}
	
	/**
	 * ptst_subsystem_init - initialise the ptst subsystem
	 *
	 * Note: This should only happen once at the start of the application.
	 */
	static void ptst_subsystem_init()
	{
		synchronized(ThreadStateManager.class) {
			thrst_list = null;
			next_id = 0;
		}
		
		/* thrst_key initialized already, but destructor(ptst_destructor) ignored!!*/
		/*
			BARRIER();
			if (pthread_key_create(&ptst_key,
				(void (*)(void *))ptst_destructor)) {
			perror("pthread_key_create: ptst_subsystem_init\n");
			exit(1);
		}
		*/
	}
}
