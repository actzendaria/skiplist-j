package util;

import java.lang.reflect.Field;
import java.util.Random;
import java.lang.Thread;
import java.util.concurrent.atomic.AtomicReferenceArray;

import sun.misc.Unsafe;

public class SkiplistRotating {

	private static final long MAX_LVLS = 20;
	private static final long NUM_SZS = 1;
	private static final long NODE_SZ = 0;
	public volatile static long sl_zero; /* the zero index */
	
	public static int curr_id;
	
	static long idx(long i, long z) {
		return ((z+i) % SkiplistRotating.MAX_LVLS);
	}
	
	static final class Node {
		/* Set long level; and Node[] succs volatile to satisfy the memory barrier in bg thread.
		 */
		volatile long level;
		volatile Node prev;
		volatile Node next;
		long key;
		volatile Object value;

		// MAX_LVLS successors;
		volatile AtomicReferenceArray<Node> succs = new AtomicReferenceArray<Node>(new Node[(int)MAX_LVLS]);
		 // marker != 0 : assistant node
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
			return UNSAFE.compareAndSwapLong(this, raise_or_remove_offset, cmp, val);
	    }
		
		boolean casPrev(Node cmp, Node val) {
			return UNSAFE.compareAndSwapObject(this, prev_offset, cmp, val);
	    }
		
		boolean casNext(Node cmp, Node val) {
			return UNSAFE.compareAndSwapObject(this, next_offset, cmp, val);
	    }
		
		boolean casValue(Object cmp, Object val) {
			return UNSAFE.compareAndSwapObject(this, value_offset, cmp, val);
	    }	
	}

    public SkiplistRotating() {
    	/* !!! The constructor may suffer from interleaving issues if initialized on several threads !!!*/
        if (null == set)
            set = newSet(1);
    }
	
	private enum SlOptype{
	    CONTAINS, DELETE, INSERT  
	} 
	
	/* sl_set */
	static final class SlSet {
		Node head = null;
	}
	
	/* background global variables */
	/* global maintained variables */
	static SlSet set = null;
	static Thread bg_thread;
	/* to keep track of background state */
	/*VOLATILE*/ volatile static int bg_finished;
	/*VOLATILE*/ volatile static int bg_running;
	
	/* for deciding whether to lower the skiplist index level */
	static int bg_non_deleted;
	static int bg_deleted;
	static int bg_tall_deleted;
	static int bg_sleep_time;
	
	 /* Updated in bg_loop() detection */
	volatile static int bg_should_delete;
	
	/* Private functions */
	static void bg_loop() {
		Node head = set.head;
		int raised = 0; /* keep track of if we raised index level */
		int threshold;  /* for testing if we should lower index level */
		long i;
		long zero;

		assert(null != set);
		bg_should_delete = 1;
		
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

			bg_non_deleted = 0;
			bg_deleted = 0;
			bg_tall_deleted = 0;
			
			/* traverse the node level and try deletes/raises */
            raised = bg_trav_nodes();

            if ((raised != 0) && (1 == head.level)) {
            	// add a new index level

            	// nullify BEFORE we increase the level
            	head.succs.set((int) idx(head.level, zero), null);           	            	
            	head.level++;

            	/*
            	 * ++(bg_stats.raises);
            	 */
            }
            
            /* raise the index level nodes */
            for (i = 0; (i+1) < set.head.level; i++) {
            	assert(i < MAX_LVLS);
            	raised = bg_raise_ilevel((int) (i+1));

            	if ((((i+1) == (head.level-1)) && (raised != 0))
            			&& head.level < MAX_LVLS) {
            		// add a new index level
            		
            		// nullify BEFORE we increase the level
            		head.succs.set((int) idx(head.level,zero), null);
            		
            		/* BARRIER(); */
            		head.level++;

            		/*
            		 * ++(bg_stats.raises);
            		 */
            	}
            }
            
            /* if needed, remove the lowest index level */
            threshold = bg_non_deleted * 10;
            if (bg_tall_deleted > threshold) {
            	if (head.level > 1) {
            		bg_lower_ilevel();
            		/*
            		 * ++(bg_stats.lowers);
            		 */        
            	}
            }

            if (bg_deleted > bg_non_deleted * 3) {
            	bg_should_delete = 1;
            	/*
        		 * bg_stats.should_delete += 1;
        		 */    
            } else {
            	bg_should_delete = 0;
            }
            
            /* BARRIER(); */
		}
		
		System.out.println("bg_loop(): Out of loop!!");
	}
	
	/**
	 * bg_trav_nodes - traverse node level of skip list
	 *
	 * Returns 1 if a raise was done and 0 otherwise.
	 *
	 * Note: this tries to raise non-deleted nodes, and finished deletions that
	 * have been started but not completed.
	 */
	static int bg_trav_nodes() {
		Node prev, node, next;
        Node above_head = set.head, above_prev, above_next;
        long zero = sl_zero;
        int raised = 0;

        assert(null != set && null != set.head);

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
        		bg_remove(prev, node);
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

                    while((above_next != null) && (above_next.key < node.key)) {
                        above_next = above_next.succs.get((int) idx(0,zero));
                        if (above_next != above_head.succs.get((int) idx(0,zero))) {
                            above_prev = above_prev.succs.get((int) idx(0,zero));
                        }
                    }

            		// swap the pointers
            		node.succs.set((int) idx(0,zero), above_next);

            		/* BARRIER(); // make sure above happens first */
            		above_prev.succs.set((int) idx(0,zero), node);
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

        return raised;
    }
	
	/**
	 * bg_lower_ilevel - lower the index level
	 */
	static void bg_lower_ilevel() {
		long zero = sl_zero;
        Node node = set.head;
        Node node_next = node;

        if (node.level-2 <= sl_zero)
        	return; /* no more room to lower */

        /* decrement the level of all nodes */
        
        while (node != null) {
        	node_next = node.succs.get((int) idx(0,zero));
        	if (node.marker == 0) {
        		if (node.level > 0) {
        			if (1 == node.level && node.raise_or_remove != 0) {
        				node.raise_or_remove = 0;
        			}
        			
        			/* null out the ptr for level being removed */
        			node.succs.set((int) idx(0,zero), null);
        			node.level--;
        		}
            }
            node = node_next;
        }

        /* remove the lowest index level */
        /* BARRIER(); *//* do all of the above first */
        ++sl_zero;
	}
	
	/**
	 * bg_raise_ilevel - raise the index levels
	 * @param h the height of the level we are raising
	 * @return 1 if a node was raised and 0 otherwise.
	 */
	static int bg_raise_ilevel(int h) {
		int raised = 0;
        long zero = sl_zero;
        Node index, inext, iprev = set.head;
        Node above_next, above_prev, above_head;

        above_next = above_prev = above_head = set.head;

        index = iprev.succs.get((int) idx(h-1,zero));
        if (null == index)
        	return raised;

        while (null != (inext = index.succs.get((int) idx(h-1,zero)))) {
        	while (index.value == index) {
        		// skip deleted nodes
        		iprev.succs.set((int) idx(h-1,zero), inext);
                    
        		/*BARRIER();*/ // do removal before level decrementing
        		index.level--;

        		if (null == inext) {
        			break;
        		}
        		index = inext;
        		inext = inext.succs.get((int) idx(h-1,zero));
            }
            if (null == inext) {
            	break;
            }
            if ( (((iprev.level <= h) && (index.level == h)) &&
            		(inext.level <= h)) && (index.value != index && null != index.value) ) {
            	raised = 1;
                	
            	/* find the correct index node above */
                while((above_next != null) && (above_next.key < index.key)) {
                    above_next = above_next.succs.get((int) idx(h,zero));
                    if (above_next != above_head.succs.get((int) idx(h,zero))) {
                        above_prev = above_prev.succs.get((int) idx(h,zero));
                    }
                }
                    
            	/* fix the pointers and levels */
            	index.succs.set((int) idx(h,zero), above_next);
            	
                /* BARRIER(); */ /* link index to above_next first */
            	
                above_prev.succs.set((int) idx(h,zero), index);
                index.level++;
                    
                assert(index.level == h+1);
                    
                above_head = index;
                above_prev = above_head;
                above_next = above_prev;
            }
            iprev = index;
            index = index.succs.get((int) idx(h-1,zero));
        }
        
        return raised;
	}
	
	/* Background interfaces */
	/* @set is a global variable set to maintain in background */
	/* @bg_thread is a global variable standing for the background thread */
	/**
	 * bg_init - initialise the background module
	 * @param s the set to maintain
	 */
	
	void bg_init(SlSet s) {
		set = (SlSet) s;
		bg_finished = 0;
		bg_running = 0;
	}
	
	class Bg_Runnable implements Runnable {
		public /*synchronized*/ void run() {
			bg_loop();
		}
	}
	 
	/**
	 * bg_start - start the background thread
	 * @param sleep_time
	 * 
	 * Note: Only start the background thread if it is not currently
	 * running.
	 */
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
		/*
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
	
	static void bg_remove(Node prev, Node node) {
        if (0 == node.level) {
            node.casValue(null, node);
            if (node.value == node)
                bg_help_remove(prev, node);
        }
	}
	
	/**
	 * bg_help_remove - finish physically removing a node
	 * @param prev the node before the one to remove
	 * @param node the node to finish removing
	 *
	 * Note: This operation will only be carried out if @node
	 * has been successfully marked for deletion (i.e. its value points
	 * to itself, and the node must now be deleted). First we insert a marker
	 * node directly after @node. Then, if no nodes have been inserted in
	 * between @prev and @node, physically remove @node and the marker
	 * by pointing @prev past these nodes.
	 */
	static void bg_help_remove(Node prev, Node node) {
		Node n, n_new, prev_next;
        boolean retval;

        assert(null != prev);
        assert(null != node);

        if (node.value != node || node.marker != 0) {
        	return;
        }

        n = node.next;
        while (null == n || n.marker == 0) {
        	n_new = newMarker(node, n);
        	/*CAS(&node->next, n, new);*/
        	node.casNext(n, n_new);
            assert (node.next != node);
            n = node.next;
        }

        if (prev.next != node || prev.marker != 0) {
        	return;
        }

        /* remove the nodes */
        /* AO_compare_and_swap takes an address and an expected old     */
        /* value and a new value, and returns an int.  Non-zero result  */
        /* indicates that it succeeded.  								*/ 
        //retval = CAS(&prev->next, node, n->next);
        retval = prev.casNext(node, n.next);

        assert (prev.next != prev);

        if (retval) {
        	delNode(node);
        	delMarker(n);
        	/*
        	 * ADD_TO(bg_stats.delete_succeeds, 1);
        	 * #endif
             */
        }
        
        /*
         * update the prev pointer - we don't need synchronization here
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
	 * @return
	 * 
	 * Nodes are originally created with marker set to 0 (unmarked)
	 */
	private static Node newNode(long key, Object value, Node prev, Node next,
							   long level) {
		Node node = new Node();
		
		node.key = key;
		node.value = value;
		node.prev = prev;
		node.next = next;
		node.level = level;
		node.marker = 0;
		node.raise_or_remove = 0;
		
		for (int i = 0; i > SkiplistRotating.MAX_LVLS; i++) {
			node.succs.set(i, null);
		}
		
		assert(node.next != node);
		
		return node;
	};
	
	/* marker_new */
	/**
	 * create a new marker node
	 * @param prev
	 * @param next
	 * @return
	 */
	private static Node newMarker(Node prev, Node next) {
		Node node = new Node();
		
		node.key = 0;
		node.value = node;
		node.prev = prev;
		node.next = next;
		node.level = 0;
		node.marker = 1;
		
		for (int i = 0; i < SkiplistRotating.MAX_LVLS; i++) {
			node.succs.set(i, null);
		}
		
		assert(node.next != node);
		
		return node;
	};
	
	/* node_delete */
	/**
	 * delete a bottom-level node
	 * @param node
	 * 
	 * Depends on garbage collection
	 *   gc_free(); 
	 */
	private static void delNode(Node node) {
		/* garbage collection */
	};
	
	/* marker_delete */
	private static void delMarker(Node node) {
		/* garbage collection */
	}
	
	/* set_new */
	/**
	 * newSet - Create a new set implemented as a skiplist
	 * 
	 * @param start (bg_start) if 1 start the bg thread, otherwise don't
	 * @return a newly created skiplist set.
	 * 
	 * A background thread to update index levels of the skiplist is created
	 *  and kick-started as part of this routine.
	 */
	private SlSet newSet(int start) {
		SlSet set = null;

		SkiplistRotating.sl_zero = 0; /* zero index initially set to 0 */
		
		set = new SlSet();
		set.head = newNode(0, null, null, null, 1);

        bg_init(set);
        if (1 == start)
            bg_start(1);

        return set;
	}
	
	/* set_delete */
	private void delSet(SlSet set) {
		bg_stop();
		/* didn't dealloc mem in original design */
	}
	
	/**
	 * printSset - Print the set
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
        		curr = curr.succs.get((int) idx(i, zero));
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
	private void set_subsys_init() {
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
					curr = curr.succs.get((int) idx(i, zero));
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
	/* nohotspot_ops: contains/insert/delete skip list operations */
	/**
	 * sl_finish_contains - contains skip list operation
	 * @param key the search key
	 * @param node the left node from sl_do_operation()
	 * @param node_val @node value
	 *
	 * @Return 1 if the search key is present and 0 otherwise.
	 */
	static int sl_finish_contains(int key,
	                              Node node,
	                              Object node_val)
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
	 * @param key the search key
	 * @param node the left node from sl_do_operation()
	 * @param node_val @node value
	 *
	 * @Return 1 on success or 0 if the search key is not present.
	 */
	static int sl_finish_delete(int key, Node node,
	                            Object node_val)
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
								bg_remove(node.prev, node);
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
	 * @param key the search key
	 * @param val the search value
	 * @param node the left node from sl_do_operation()
	 * @param node_val @node value
	 * @param next the right node from sl_do_operation()
	 *
	 * @Returns
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
	                            Object node_val, Node next) {
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
				n_new = newNode(key, val, node, next, 0);
				//if (CAS(&node->next, next, new)) {
				if (node.casNext(next, n_new)) {
					if (null != next) {
						temp = next.prev;
						//CAS(&next->prev, temp, new);
						next.casPrev(temp, n_new);
					}
					result = 1;
				} else {
					delNode(n_new);
				}
	        }
		return result;
	}
	
	/* - The public nohotspot_ops interface - */

	/**
	 * sl_do_operation - find node and next for this operation
	 * @param set the skip list set
	 * @param optype the type of operation this is
	 * @param key the search key
	 * @param val the search value
	 *
	 * @Return the result of the operation.
	 * Note: @val can be NULL.
	 */
	int sl_do_operation(SlSet set, SlOptype optype, int key, Object val)
	{
		Node item = null, next_item = null;
		Node node = null, next = null;
		Node head = set.head;
		Object node_val = null, next_val = null;
		int result = 0;
	    long zero, i;
	    
	    assert(null != set);

	    zero = sl_zero;
	    i = set.head.level - 1;
	    
	    /* find an entry-point to the node-level */
	    item = head;
	    while (true) {
	    	next_item = item.succs.get((int) idx(i,zero));
	    	
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
        			bg_help_remove(node, next);
        			continue;
        		}
        	}
        	if (null == next || next.key > key) {
        		if (SlOptype.CONTAINS == optype) {
        			result = sl_finish_contains(key, node,
        					node_val);
        		}
        		else if (SlOptype.DELETE == optype) {
        			result = sl_finish_delete(key, node,
        					node_val);
        		}
        		else if (SlOptype.INSERT == optype) {
        			result = sl_finish_insert(key, val, node,
        					node_val, next);
        		}
        		if (-1 != result) {
        			break;
        		}
        		continue;
        	}
        	node = next;
        }
        return result;
    }

    public int contains(int key)
    {
        return sl_do_operation(set, SlOptype.CONTAINS, key, null);
    }

    public int delete(int key)
    {
        return sl_do_operation(set, SlOptype.DELETE, key, null);
    }

    public int insert(int key, Object value)
    {
        return sl_do_operation(set, SlOptype.INSERT, key, value);
    }
}
