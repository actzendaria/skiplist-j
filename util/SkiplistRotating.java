package util;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;
import java.lang.Thread;

import sun.misc.Unsafe;
import util.ThreadState;
import util.ThreadStateManager;
import util.CASTest.Node;
import util.MyConcurrentSkipListMap.Index;

public class SkiplistRotating {

	private static final long MAX_LVLS = 20;
	private static final long NUM_SZS = 1;
	private static final long NODE_SZ = 0;
	public volatile static long sl_zero; /* the zero index */
	
	/* for gc in c ZZZ */
	/*
	 * 	static int gc_id[NUM_SIZES];
		static int curr_id;
	 */
	public static int curr_id;
	
	static long idx(long i, long z) {
		return ((z+i) % SkiplistRotating.MAX_LVLS);
	}
	
	static final class Node {
		/*ZZZ review here*/
		/* To ensure BARRIER (probably in background..), 
		 * Does long level; and Node[] succs need to be volatile???
		 */
		volatile long level;      //?? volatile?
		volatile Node prev;
		volatile Node next;
		long key;
		volatile Object value;
		
		// MAX_LVLS successors;
		volatile Node[] succs; //?? volatile?
		long marker;
		long raise_or_remove;
		
		private static Unsafe UNSAFE;
		private static long raise_or_remove_offset;
		private static long next_offset;
		private static long prev_offset;
		private static long value_offset;
		static {
			try {
				Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");  
				field.setAccessible(true);  
				UNSAFE = (sun.misc.Unsafe) field.get(null); 
				 
				Class k = Node.class;
				raise_or_remove_offset = UNSAFE.objectFieldOffset
						(k.getDeclaredField("raise_or_remove"));
				next_offset = UNSAFE.objectFieldOffset
						(k.getDeclaredField("next"));
				prev_offset = UNSAFE.objectFieldOffset
						(k.getDeclaredField("prev"));
				value_offset = UNSAFE.objectFieldOffset
						(k.getDeclaredField("value"));
				} catch (Exception e) {
					e.printStackTrace();
					throw new Error(e);
			}
		}
		
		boolean casRor(long cmp, long val) {
			boolean ret;
			System.out.println("casRor (getLong): " + UNSAFE.getLong(this, raise_or_remove_offset) + " raise_or_remove: " + this.raise_or_remove);
			ret = UNSAFE.compareAndSwapLong(this, raise_or_remove_offset, cmp, val);
	        System.out.println("casRor triggered?: " + ret);
	        return ret;
	    }
		
		boolean casPrev(Node cmp, Node val) {
			boolean ret;
			ret = UNSAFE.compareAndSwapObject(this, prev_offset, cmp, val);
	        System.out.println("casPrev triggered?: " + ret);
	        return ret;
	    }
		
		boolean casNext(Node cmp, Node val) {
			boolean ret;
			ret = UNSAFE.compareAndSwapObject(this, next_offset, cmp, val);
	        System.out.println("casNext triggered?: " + ret);
	        return ret;
	    }
		
		boolean casValue(Object cmp, Object val) {
			boolean ret;
			ret = UNSAFE.compareAndSwapObject(this, value_offset, cmp, val);
	        System.out.println("casValue triggered?: " + ret);
	        return ret;
	    }	
	}
	
	public enum SlOptype{  
	    CONTAINS, DELETE, INSERT  
	} 
	
	/* sl_set */
	static final class SlSet {
		Node head = null;
	}
	
	/* background global variables */
	/* ZZZ review here, instantiate as Object ? */
	/* global maintained variables */
	static SlSet set;
	static Thread bg_thread;
	/* to keep track of background state */
	/*VOLATILE*/ volatile static int bg_finished;
	/*VOLATILE*/ volatile static int bg_running;
	
	/* for deciding whether to lower the skiplist index level */
	static int bg_non_deleted;
	static int bg_deleted;
	static int bg_tall_deleted;

	static int bg_sleep_time;
	static int bg_counter;
	static int bg_go;

	volatile static int bg_should_delete; /* Updated in bg_loop() detection */
	
	/* Private functions */
	/* Barrier? ZZZ needs review here*/
	static void bg_loop() {
		Node head = set.head;
		int raised = 0; /* keep track of if we raised index level */
		int threshold;  /* for testing if we should lower index level */
		long i;
		ThreadState thrst = null;
		long zero;

		assert(null != set);
		/* ZZZ check if condition in sync block is correct */
		synchronized(SkiplistRotating.class){
			bg_counter = 0;
			bg_go = 0;
			bg_should_delete = 1;
		}
		
		/* BARRIER(); */

		/*
		#ifdef BG_STATS
		bg_stats.raises = 0;
		bg_stats.loops = 0;
		bg_stats.lowers = 0;
		bg_stats.delete_attempts = 0;
		bg_stats.delete_succeeds = 0;
		#endif
		 */
		
		while (true) {
			/* bg_sleep_time in us, e.g. usleep(bg_sleep_time) */
			long millis_part = bg_sleep_time / 1000;
			long nanos_part = (bg_sleep_time - millis_part * 1000) * 1000;
			try {
				Thread.sleep(millis_part, (int) nanos_part);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			if (bg_finished != 0) {
				break;
			}
			
			zero = sl_zero;
			
			/* #ifdef BG_STATS
			 * ++(bg_stats.loops)
			 */
			bg_non_deleted = 0;
			bg_deleted = 0;
			bg_tall_deleted = 0;
			
			/* traverse the node level and try deletes/raises */
            raised = bg_trav_nodes(thrst);

            if ((raised != 0) && (1 == head.level)) {
            	// add a new index level

            	// nullify BEFORE we increase the level
            	head.succs[(int) idx(head.level, zero)] = null;
            	
            	/* BARRIER(); ZZZ: I've markedl evel and succs volatile..*/
            	
            	head.level++;

            	/*
            	 * #ifdef BG_STATS
            	 * ++(bg_stats.raises);
            	 */
            }
            
            /* raise the index level nodes */
            for (i = 0; (i+1) < set.head.level; i++) {
            	assert(i < MAX_LVLS);
            	raised = bg_raise_ilevel((int) (i+1), thrst);

            	if ((((i+1) == (head.level-1)) && (raised != 0))
            			&& head.level < MAX_LVLS) {
            		// add a new index level
            		
            		// nullify BEFORE we increase the level
            		head.succs[(int) idx(head.level,zero)] = null;
            		
            		/* BARRIER(); ZZZ */
            		head.level++;

            		/*
            		 * #ifdef BG_STATS
            		 * ++(bg_stats.raises);
            		 * #endif
            		 */
            	}
            }
            
            /* if needed, remove the lowest index level */
            threshold = bg_non_deleted * 10;
            if (bg_tall_deleted > threshold) {
            	if (head.level > 1) {
            		bg_lower_ilevel(thrst);
            		/*
            		 * #ifdef BG_STATS
            		 * ++(bg_stats.lowers);
            		 * #endif
            		 */        
            	}
            }

            if (bg_deleted > bg_non_deleted * 3) {
            	bg_should_delete = 1;
            	/*
        		 * #ifdef BG_STATS
        		 * bg_stats.should_delete += 1;
        		 */    
            } else {
            	bg_should_delete = 0;
            }
            
            /* BARRIER(); ZZZ */
		}
		
		System.out.println("bg_loop(); Out of loop!!");
	}
	
	/**
	 * bg_trav_nodes - traverse node level of skip list
	 * @ptst: per-thread state
	 *
	 * Returns 1 if a raise was done and 0 otherwise.
	 *
	 * Note: this tries to raise non-deleted nodes, and finished deletions that
	 * have been started but not completed.
	 */
	static int bg_trav_nodes(ThreadState thrst) {
		/* ZZZ: I need to initialize Node with a specific type here? Object? */
		Node prev, node, next;
        Node above_head = set.head, above_prev, above_next;
        long zero = sl_zero;
        int raised = 0;

        assert(null != set && null != set.head);

        thrst = ptst_critical_enter();

        above_next = above_head;
        above_prev = above_next;
        prev = set.head;
        node = prev.next;
        if (null == node) {
        	return 0;
        }
        next = node.next;
        
        while (null != next) {
        	if (null == node.value) {
        		bg_remove(prev, node, thrst);
        		if (node.level >= 1) {
        			bg_tall_deleted++;
        		}
                bg_deleted++;
            }
            else if (node.value != node) {
            	if ((((0 == prev.level
            			&& 0 == node.level)
            			&& 0 == next.level))
            			//&& CAS(&node->raise_or_remove, 0, 1)) {
            			&& node.casRor(0, 1)) {
            		
            		node.level = 1;
            		raised = 1;
            		
            		get_index_above(above_head, above_prev,
            				above_next, 0, node.key,
            				zero);

            		// swap the pointers
            		node.succs[(int) idx(0,zero)] = above_next;

            		/* BARRIER(); // make sure above happens first ZZZ */

            		above_prev.succs[(int) idx(0,zero)] = node;
            		above_head = node;
            		above_prev = above_head; 
            		above_next = above_prev; 
            	}
            }

        	if (null != node.value && node != node.value) {
        		bg_non_deleted++;
            }
        	prev = node;
            node = next;
            next = next.next;
        }
        ptst_critical_exit(thrst);
        return raised;
    }
	
	/**
	 * bg_lower_ilevel - lower the index level
	 */

	static void bg_lower_ilevel(ThreadState thrst) {
		long zero = sl_zero;
        Node node = set.head;
        Node node_next = node;

        thrst = ptst_critical_enter();

        if (node.level-2 <= sl_zero)
        	return; /* no more room to lower */

        /* decrement the level of all nodes */
        
        while (node != null) {
        	node_next = node.succs[(int) idx(0,zero)];
        	if (node.marker == 0) {
        		if (node.level > 0) {
        			if (1 == node.level && node.raise_or_remove != 0) {
        				node.raise_or_remove = 0;
        			}
        			
        			/* null out the ptr for level being removed */
        			node.succs[(int) idx(0,zero)] = null;
        			node.level--;
        		}
            }
            node = node_next;
        }

        /* remove the lowest index level */
        /* ZZZ */
        /* BARRIER(); *//* do all of the above first */
        ++sl_zero;

        ptst_critical_exit(thrst);
	}
	
	/**
	 * bg_raise_ilevel - raise the index levels
	 * @h: the height of the level we are raising
	 * @ptst: per-thread state
	 *
	 * Returns 1 if a node was raised and 0 otherwise.
	 */

	static int bg_raise_ilevel(int h, ThreadState thrst) {
		int raised = 0;
        long zero = sl_zero;
        Node index, inext, iprev = set.head;
        Node above_next, above_prev, above_head;

        thrst = ptst_critical_enter();

        above_next = above_prev = above_head = set.head;

        index = iprev.succs[(int) idx(h-1,zero)];
        if (null == index)
        	return raised;

        while (null != (inext = index.succs[(int) idx(h-1,zero)])) {
        	while (index.value == index) {
        		// skip deleted nodes
        		iprev.succs[(int) idx(h-1,zero)] = inext;
                    
        		/*ZZZ*/
        		/*BARRIER();*/ // do removal before level decrementing
        		index.level--;

        		if (null == inext) {
        			break;
        		}
        		index = inext;
        		inext = inext.succs[(int) idx(h-1,zero)];
            }
            if (null == inext) {
            	break;
            }
            if ( (((iprev.level <= h) && (index.level == h)) &&
            		(inext.level <= h)) && (index.value != index && null != index.value) ) {
            	raised = 1;
                	
            	/* find the correct index node above */
            	get_index_above(above_head, above_prev, above_next,
            			h, index.key, zero);
                    
            	/* fix the pointers and levels */
            	index.succs[(int) idx(h,zero)] = above_next;
                    
                /*ZZZ*/
                /* BARRIER(); */ /* link index to above_next first */
            	
                above_prev.succs[(int) idx(h,zero)] = index;
                index.level++;
                    
                assert(index.level == h+1);
                    
                above_head = index;
                above_prev = above_head;
                above_next = above_prev;
            }
            iprev = index;
            index = index.succs[(int) idx(h-1,zero)];
        }
        ptst_critical_exit(thrst);
        
        return raised;
	}
	
	/* *head, **prev, **next, ZZZ need review here, what does "above" stands for? */
	static void get_index_above(Node above_head,
	                            Node above_prev,
	                            Node above_next,
	                            long i,
	                            long key,
	                            long zero) {
		/* get the correct index node above */
		/*
		 while (*above_next && (*above_next)->key < key) {
			 *above_next = (*above_next)->succs[IDX(i,zero)];
             if (*above_next != above_head->succs[IDX(i,zero)])
                     *above_prev = (*above_prev)->succs[IDX(i,zero)];
         }
         */
		while((above_next != null) && (above_next.key < key)) {
			above_next = above_next.succs[(int) idx(i,zero)];
			if (above_next != above_head.succs[(int) idx(i,zero)]) {
				above_prev = above_prev.succs[(int) idx(i,zero)];
			}
		}
	
	}
	
	/* Background interfaces */
	/* @set is a global variable set to maintain in background */
	/* @bg_thread is a global variable standing for the background thread */
	/**
	 * bg_init - initialise the background module
	 * @s: the set to maintain
	 */
	
	void bg_init(SlSet s) {
		set = (SlSet) s;
		bg_finished = 0;
		bg_running = 0;

		 /* BG_STATS 
	        bg_stats.loops = 0;
	        bg_stats.raises = 0;
	        bg_stats.lowers = 0;
	        bg_stats.delete_attempts = 0;
	        bg_stats.delete_succeeds = 0;
	      */
	}
	
	/**
	 * bg_start - start the background thread
	 *
	 * Note: Only start the background thread if it is not currently
	 * running.
	 */
	
	class Bg_Runnable implements Runnable {
		public /*synchronized*/ void run() {
			bg_loop();
		}
	}
	 
	void bg_start(int sleep_time) {
		/* XXX not thread safe  XXX */
        if (bg_running == 0) {
        	bg_sleep_time = sleep_time;
        	bg_running = 1;
        	bg_finished = 0;
        	/*pthread_create(&bg_thread, NULL, bg_loop, NULL);*/
        	Runnable bg_r = new Bg_Runnable();
        	bg_thread = new Thread(bg_r);
        	bg_thread.start();
        }
	}
	
	void bg_stop() {
		/* XXX not thread safe XXX */
		if (bg_running != 0) {
			bg_finished = 1;
			/*
             * pthread_join(bg_thread, NULL);
             * BARRIER();
             */
			try {
				bg_thread.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
            bg_running = 0;
        }
	}
	
	/**
	 * bg_print_stats - print background statistics
	 *
	 * Note: this is a noop if BG_STATS is not defined.
	 */

	void bg_print_stats() {
		/* #ifdef BG_STATS
	       printf("Loops = %lu\n", bg_stats.loops);
	       printf("Raises = %lu\n", bg_stats.raises);
	       printf("Levels = %lu\n", set->head->level);
	       printf("Lowers = %lu\n", bg_stats.lowers);
	       printf("Delete Attempts = %lu\n", bg_stats.delete_attempts);
	       printf("Delete Succeeds = %lu\n", bg_stats.delete_succeeds);
	       printf("Should delete = %lu\n", bg_stats.should_delete);
	       #endif
	     */
	}
	
	static void bg_remove(Node prev, Node node, ThreadState thrst) {
	}
	
	/**
	 * bg_help_remove - finish physically removing a node
	 * @prev: the node before the one to remove
	 * @node: the node to finish removing
	 * @ptst: per-thread state
	 *
	 * Note: This operation will only be carried out if @node
	 * has been successfully marked for deletion (i.e. its value points
	 * to itself, and the node must now be deleted). First we insert a marker
	 * node directly after @node. Then, if no nodes have been inserted in
	 * between @prev and @node, physically remove @node and the marker
	 * by pointing @prev past these nodes.
	 */
	void bg_help_remove(Node prev, Node node, ThreadState thrst) {
		Node n, n_new, prev_next;
        boolean retval;

        assert(null != prev);
        assert(null != node);

        if (node.value != node || node.marker != 0) {
        	return;
        }

        n = node.next;
        while (null == n || n.marker == 0) {
        	n_new = newMarker(node, n, thrst);
        	/*CAS(&node->next, n, new);*/
        	node.casNext(n, n_new);
            assert (node.next != node);
            n = node.next;
        }

        /*
          #ifdef BG_STATS
          ADD_TO(bg_stats.delete_attempts, 1);
          #endif
         */

        if (prev.next != node || prev.marker != 0) {
        	return;
        }

        /* remove the nodes */
        retval = prev.casNext(node, n.next);
        /* AO_compare_and_swap takes an address and an expected old     */
        /* value and a new value, and returns an int.  Non-zero result  */
        /* indicates that it succeeded.  								*/ 
        //retval = CAS(&prev->next, node, n->next);

        assert (prev.next != prev);

        if (retval) {
        	delNode(node, thrst);
        	delMarker(n, thrst);
        	/*
        	 * #ifdef BG_STATS
        	 * ADD_TO(bg_stats.delete_succeeds, 1);
        	 * #endif
             */
        }
        
        /*
         * update the prev pointer - we don't need synchronisation here
         * since the prev pointer does not need to be exact
         */
        prev_next = prev.next;
        if (null != prev_next) {
        	prev_next.prev = prev;
        }
	}
	
	/* Background utilities end*/
	
	/* node_new */
	/**
	 * newNode - create a new bottom-level node
	 * @param key the key for the new node
	 * @param value the value for the new node
	 * @param prev the prev node pointer for the new node
	 * @param next the next node pointer for the new node
	 * @param level the level for the new node
	 * @param thrst the per-thread state
	 * @return
	 * 
	 * Nodes are originally created with marker set to 0 (unmarked)
	 */
	@SuppressWarnings("unused")
	private static Node newNode(long key, Object value, Node prev, Node next,
							   long level, ThreadState thrst) {
		Node node = new Node();
		
		node.key = key;
		node.value = value;
		node.prev = prev;
		node.next = next;
		node.level = level;
		node.marker = 0;
		node.raise_or_remove = 0;
		
		for (int i = 0; i > SkiplistRotating.MAX_LVLS; i++) {
			node.succs[i] = null;
		}
		
		assert(node.next != node);
		
		return node;
	};
	
	/* marker_new */
	/**
	 * create a new marker node
	 * @param prev
	 * @param next
	 * @param thrst
	 * @return
	 */
	private static Node newMarker(Node prev, Node next, ThreadState thrst) {
		Node node = new Node();
		
		node.key = 0;
		node.value = node;
		node.prev = prev;
		node.next = next;
		node.level = 0;
		node.marker = 1;
		
		for (int i = 0; i < SkiplistRotating.MAX_LVLS; i++) {
			node.succs[i] = null;
		}
		
		assert(node.next != node);
		
		return node;
	};
	
	/* node_delete */
	/**
	 * delete a bottom-level node
	 * @param node
	 * @param thrst
	 * 
	 * Depends on garbage collection
	 *   gc_free(ptst, (void*)node, gc_id[curr_id]); // original
	 */
	private static void delNode(Node node, ThreadState thrst) {
		/* ZZZ seems nothing to be done here... */
	};
	
	/* marker_delete */
	private static void delMarker(Node node, ThreadState thrst) {
		/* ZZZ seems nothing to be done here... */
	}
	
	/* set_new */
	/**
	 * Create a new set implemented as a skiplist
	 * 
	 * @param start (bg_start) if 1 start the bg thread, otherwise don't
	 * @return a newly created skiplist set.
	 * 
	 * A background thread to update index levels of the skiplist is created
	 *  and kick-started as part of this routine.
	 */
	private SlSet newSet(int start) {
		SlSet set;
		ThreadState thrst;
		
		thrst = ThreadStateManager.thrst_critical_enter();
		SkiplistRotating.sl_zero = 0; /* zero index initially set to 0 */
		
		/* alloc mem for set in c */
		set.head = newNode(0, null, null, null, 1, thrst);
		
		bg_init(set);
		if (start != 0)
			bg_start(1);
		
		ThreadStateManager.thrst_critical_exit(thrst);
		
		return set;
	}
	
	/* set_delete */
	private void delSet(SlSet set) {
		bg_stop();
		
		/* didn't dealloc mem in c */
	}
	
	/**
	 * Print the set
	 * @param set skiplist set to print
	 * @param flag if non-zero include logically deleted nodes in the count
	 */
	private void printSet(SlSet set, int flag) {
		Node head = set.head;
		Node curr = set.head;
		long i = head.level - 1;
		long zero = sl_zero;

        /* print the index items */
        while (true) {
        	while (null != curr) {
        		if ((flag != 0) && (curr.value != curr)) {
        			System.out.print(curr.key + " ");
        		} else if (flag == 0) {
        			System.out.print(curr.key + " ");
        		}
        		curr = curr.succs[(int) idx(i, zero)];
        	}
        	System.out.println("");
        	curr = head;
            if (0 == i)
                break;
        	i = i - 1;
        }

        while (null != curr) {
        	if ((flag != 0) && (curr.value != curr)) {
        		System.out.print(curr.key + " (" + curr.level + ") ");
        	} else if (flag == 0) {
        		System.out.print(curr.key + " (" + curr.level + ") ");
        	}
        	curr = curr.next;
        }
        System.out.println("");
	}
	
	/* set_size */
	private int setSize(SlSet set, int flag) {
		Node node = set.head;
		int size = 0;
		
		node = node.next;
		while (null != node) {
			/* flag set, and (value of node not self-pointed while value is not null)*/
			if ((flag != 0) && (node != node.value && null != node.value)) {
				size++;
			}
			else if (flag == 0) {
				size++;
			}
		}
		return size;
	}
	
	/* set_subsystem_init */
	/* ZZZ not sure here */
	private void set_subsys_init() {
		/*
		 *  int i;
        	for (i = 0; i < NUM_SIZES; i++) {
                gc_id[i] = gc_add_allocator(sizeof(node_t));
        	}
        	curr_id = rand() % NUM_SIZES;
		 */
		Random r = new Random(); 
		this.curr_id = r.nextInt((int) NUM_SZS);
	}
	
	private void set_print_nodenums(SlSet set, int flag) {
		long i = set.head.level - 1;
		long count = 0, zero = sl_zero;
		Node head = set.head, curr = set.head;
		
		while (true) {
			assert(i < set.head.level);
			while (curr != null) {
				if ((flag != 0) && ((curr.value != curr) && (curr.value != null)) || flag == 0) {
					count++;
					curr = curr.succs[(int) idx(i, zero)];
				}
			}
			System.out.println("inodes at level "+(i+1)+" = "+(count));

			curr = head;
			count = 0;
			if (0 == i) {
				break;
			}
			i = i - 1;
		}
		
		while (curr != null) {
			if (((flag != 0) && ((curr.value != curr) && (curr.value != null))) || (flag == 0)) {
				count = count + 1;
			}
            curr = curr.next;
        }
        System.out.println("nodes at level 0 = " + count);
	}
	
	/* Skiplist public interface */
	/* Original c code in:  nohotspot_ops.c: contains/insert/delete skip list operations */
	/**
	 * sl_finish_contains - contains skip list operation
	 * @key: the search key
	 * @node: the left node from sl_do_operation()
	 * @node_val: @node value
	 * @ptst: per-thread state
	 *
	 * Returns 1 if the search key is present and 0 otherwise.
	 */
	static int sl_finish_contains(int key,
	                              Node node,
	                              Object node_val, ThreadState thrst)
	{
		int result = 0;
		
		assert(null != node);

		if ((key == node.key) && (null != node_val)) {
			result = 1;
		}
		return result;
	}
	
	/**
	 * sl_finish_delete - delete skip list operation
	 * @key: the search key
	 * @node: the left node from sl_do_operation()
	 * @node_val: @node value
	 *
	 * Returns 1 on success or 0 if the search key is not present.
	 */
	static int sl_finish_delete(int key, Node node,
	                            Object node_val, ThreadState thrst)
	{
		int result = -1;
		
		assert(null != node);
		
		if (node.key != key) {
			result = 0;
		}
		else {
			if (null != node_val) {
				/* loop until we or someone else deletes */
				while (true) {
					node_val = node.value;
					if (null == node_val || node == node_val) {
						result = 0;
						break;
					}
					//else if (CAS(&node->val, node_val, NULL)) {
					else if (node.casValue(node_val, null)) {
						result = 1;
						if (bg_should_delete != 0) {
							//if (CAS(&node->raise_or_remove, 0, 1)) {
							if (node.casRor(0, 1)) {
								bg_remove(node.prev, node, thrst);
							}
						}
						break;
					}
				}
			} else {
				/* Already logically deleted */
				result = 0;
			}
		}
		return result;
	}

	/**
	 * sl_finish_insert - insert skip list operation
	 * @key: the search key
	 * @val: the search value
	 * @node: the left node from sl_do_operation()
	 * @node_val: @node value
	 * @next: the right node from sl_do_operation()
	 *
	 * Returns:
	 * > 1 if @key is present in the set and the corresponding node
	 *   is logically deleted and the undeletion operation succeeds.
	 * > 1 if @key is not present in the set and insertion operation
	 *   succeeds.
	 * > 0 if @key is present in the set and not null.
	 * > -1 if @key is present in the set and value of corresponding
	 *   node is not null and logical un-deletion fails due to concurrency.
	 * > -1 if @key is not present in the set and insertion operation
	 *   fails due to concurrency.
	 */
	static int sl_finish_insert(int key, Object val, Node node,
	                            Object node_val, Node next, ThreadState thrst) {
		int result = -1;
		Node n_new, temp;

		if (node.key == key) {
			if (null == node_val) {
				//if (CAS(&node->val, node_val, val))
				if (node.casValue(node_val, val))
					result = 1;
				} else {
					result = 0;
				}
			} else {
				n_new = newNode(key, val, node, next, 0, thrst);
				//if (CAS(&node->next, next, new)) {
				if (node.casNext(next, n_new)) {
					if (null != next) {
						temp = next.prev;
						//CAS(&next->prev, temp, new);
						next.casPrev(temp, n_new);
					}
					result = 1;
				} else {
					delNode(n_new, thrst);
				}
	        }
		return result;
	}
	
	/* - The public nohotspot_ops interface - */

	/**
	 * sl_do_operation - find node and next for this operation
	 * @set: the skip list set
	 * @optype: the type of operation this is
	 * @key: the search key
	 * @val: the seach value
	 *
	 * Returns the result of the operation.
	 * Note: @val can be NULL.
	 */
	int sl_do_operation(SlSet set, SlOptype optype, int key, Object val)
	{
		Node item = null, next_item = null;
		Node node = null, next = null;
		Node head = set.head;
		Object node_val = null, next_val = null;
		int result = 0;
	    ThreadState thrst;
	    long zero, i;
	    
	    assert(null != set);
	    
	    thrst = ptst_critical_enter();

	    zero = sl_zero;
	    i = set.head.level - 1;
	    
	    /* find an entry-point to the node-level */
	    item = head;
	    while (true) {
	    	next_item = item.succs[(int) idx(i,zero)];
	    	
	    	if (null == next_item || next_item.key > key) {
	    		next_item = item;
	    		if (zero == i) {
	    			node = item;
	    			break;
	    		} else {
	    			i--;
	    		}
	    	}
	    	item = next_item;
        }

        /* find the correct node and next */
        while (true) {
        	while (node == (node_val = node.value)) {
        		node = node.prev;
        	}
        	next = node.next;
        	if (null != next) {
        		next_val = next.value;
        		if (next_val == next) {
        			bg_help_remove(node, next, thrst);
        			continue;
        		}
        	}
        	if (null == next || next.key > key) {
        		if (SlOptype.CONTAINS == optype) {
        			result = sl_finish_contains(key, node,
        					node_val, thrst);
        		}
        		else if (SlOptype.DELETE == optype) {
        			result = sl_finish_delete(key, node,
        					node_val, thrst);
        		}
        		else if (SlOptype.INSERT == optype) {
        			result = sl_finish_insert(key, val, node,
        					node_val, next, thrst);
        		}
        		if (-1 != result) {
        			break;
        		}
        		continue;
        	}
        	node = next;
        }
        ptst_critical_exit(thrst);
        return result;
    }
             
	/*
	private static Unsafe UNSAFE;
	private static long headOffset;
	static {
		try {
			Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");  
			field.setAccessible(true);  
			UNSAFE = (sun.misc.Unsafe) field.get(null); 
			 
			Class k = MyConcurrentSkipListMap.class;
			headOffset = UNSAFE.objectFieldOffset
					(k.getDeclaredField("head"));
			} catch (Exception e) {
				e.printStackTrace();
				throw new Error(e);
		}
	} */ 
}
