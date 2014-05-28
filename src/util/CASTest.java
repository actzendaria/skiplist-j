package util;

import java.lang.reflect.Field;

import sun.misc.Unsafe;
import util.CASTest.Node;

@SuppressWarnings("restriction")
public class CASTest {
	public CASTest() {
		System.out.println("CAS initializing..");
	}
	static final class Node {
		/*ZZZ review here*/
		/* To ensure BARRIER (probably in background..), 
		 * Does long level; and Node[] succs need to be volatile???
		 */
		volatile long level;      //??
		volatile Node prev;
		volatile Node next;
		long key;
		volatile Object value;
		
		// MAX_LVLS successors;
		volatile Node[] succs; //??
		long marker;
		long raise_or_remove;
		//Integer raise_or_remove = new Integer(0); // This can trigger CASObj in casror().. But the above one (basic type) couldn't
		
		private static Unsafe UNSAFE;
		private static long rorOffset;
		static {
			try {
				System.out.println("Static initializing Node: Unsafe");
				Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");  
				field.setAccessible(true);  
				UNSAFE = (sun.misc.Unsafe) field.get(null); 
				 
				Class k = Node.class;
				rorOffset = UNSAFE.objectFieldOffset
						(k.getDeclaredField("raise_or_remove"));
				System.out.println("Static initializing Node: roroffset = " + rorOffset);
				} catch (Exception e) {
					e.printStackTrace();
					throw new Error(e);
			}
		}
		
		/*@SuppressWarnings("restriction")*/
		final boolean casror(long cmp, long val) {
			boolean ret;
			System.out.println("casror (getLong): " + UNSAFE.getLong(this, rorOffset) + " raise_or_remove: " + this.raise_or_remove);
	        //ret = UNSAFE.compareAndSwapObject(this, rorOffset, (Object) raise_or_remove, val);  //CAS OBJ
	        //System.out.println("casror arg-cmp: " + cmp + " arg-val: " + val);
	        ret = UNSAFE.compareAndSwapLong(this, rorOffset, cmp, val); //RIGHT!!
	        // WRONG boolean ret2 = UNSAFE.compareAndSwapLong(this.raise_or_remove, 7, cmp, val);
	        System.out.println("casror triggered?: " + ret);
	        return ret;
	    }
	}
	
	/* sl_set */
	static final class SlSet<V> {
		Node head = null;
	}
	
	int a;
	int b;
	long c;
	int d;
	long e;
	static long sa;
	static long sb;
	static int sc;
	static long sd;
	static int se;
	
	static SlSet<Object> set;
	
	public static void main(String[] args) {
		//CASTest cast = new CASTest();
		//set.head = new Node();
		//set.head.level = 9;
		//set.head.key = 98;
		//set.head.raise_or_remove = 17;
		Node node_temp = new Node();
		node_temp.level = 8;
		node_temp.key = 77;
		node_temp.raise_or_remove = 5;
		
		System.out.println("ror: " + node_temp.raise_or_remove);
		
		if (node_temp.casror(5, 1)) {
			System.out.println("CAS triggered!");
		}
		System.out.println("ror after: " + node_temp.raise_or_remove);

		long rorOffset = 9;
		if (UNSAFE.compareAndSwapLong(node_temp, 40, 1, 3)) {
			System.out.println("CAS: true!");
		}
		else
			System.out.println("CAS: false!");
		System.out.println("ror: " + node_temp.raise_or_remove);

		/* StringWrapper test if CASObj use equals()?
		 * A: No, s1 != s2; s1.equals(s2); CAS not triggered! 
		StringWrapper s1 = new StringWrapper("1st wrap");
		StringWrapper s2 = new StringWrapper("2st wrap");
		
		s1.tmp = new StringWrapper();
		s2.tmp = new StringWrapper();
		s1.tmp.ctx = "s1=s2 tmp ctx";
		s2.tmp.ctx = "s1=s2 tmp ctx";
		
		System.out.println("s1 ref: " + s1);
		System.out.println("s2 ref: " + s2);
		
		System.out.println("s1 == s2??: " + (s1 == s2));
		System.out.println("s1 equals to s2??: " + (s1.equals(s2)));
		//UNSAFE.compareAndSwapObject(node_temp, rorOffset, 5, 1)		
		s1.casror(s2.tmp, s2);
		System.out.println(s1.tmp.ctx);
		*/
		
	}
	
	private static Unsafe UNSAFE;
	//private static long rorOffset;
	static {
		try {
			System.out.println("Static initializing: SlSet");
			set = new SlSet<Object>();
			Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");  
			field.setAccessible(true);  
			UNSAFE = (sun.misc.Unsafe) field.get(null); 
			 
			/*Class k = Node.class;
			rorOffset = UNSAFE.objectFieldOffset
					(k.getDeclaredField("raise_or_remove"));
			System.out.println("Static initializing: roroffset = " + rorOffset);*/
			} catch (Exception e) {
				e.printStackTrace();
				throw new Error(e);
		}
	}  
}

class StringWrapper {
	String ctx;
	StringWrapper tmp;
	public StringWrapper() {
		ctx = "";
		//tmp = new StringWrapper();
	}
	public StringWrapper(String s){
		ctx = s;
		//tmp = new StringWrapper();
	}
	public boolean equals(Object another) {
		if (! (another instanceof StringWrapper))
			return false;
		StringWrapper ano = (StringWrapper) another;
		return this.ctx == ano.ctx;
	}
	final boolean casror(Object cmp, Object val) {
		boolean ret;
		 System.out.println("cas compare equals()?: " + this.tmp.equals(cmp));
        ret = UNSAFE.compareAndSwapObject(this, rorOffset, /*this do triggers!(Object)this.tmp*/cmp, val);
        System.out.println("casror triggered?: " + ret);
        return ret;
    }
	private static Unsafe UNSAFE;
	private static long rorOffset;
	static {
		try {
			System.out.println("Static initializing: StringWrapper");
			Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");  
			field.setAccessible(true);  
			UNSAFE = (sun.misc.Unsafe) field.get(null); 
			
			Class k = StringWrapper.class;
			rorOffset = UNSAFE.objectFieldOffset
					(k.getDeclaredField("tmp"));
			} catch (Exception e) {
				e.printStackTrace();
				throw new Error(e);
		}
	}  
}