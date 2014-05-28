package util;

import java.io.*;
import java.lang.Thread;
import java.lang.ThreadLocal;
import java.util.*;

public class PerThreadTest implements Runnable {
	public static List<Integer> a = null;
	static int l = 0;
	public /*synchronized*/ void run() {
		for (int i = 0; i < 5000; i++) {
			l++;
			System.out.println(Thread.currentThread().getName() + "=" + l + " ");
			//perthrkey.set(perthrkey.get()+1);
			//System.out.println(Thread.currentThread().getName() + "=" + perthrkey.get() + " ");
		}
	}
	
	private static ThreadLocal<Integer> perthrkey = new ThreadLocal<Integer>() {
		public Integer initialValue() {
			return 0;
		}
	};
	
	public static void changeName(Staff s, String newname) {
		s.name = newname;
	}
	
	public static void changeSalary(Staff s, long sa) {
		s.salary = sa;
	}
	
	private static <V> Node<V> newNode(long key, V value, Node<V> prev, Node<V> next,
			long level) {
		Node<V> node = new Node<V>();
		node.key = key;
		node.value = value;
		node.prev = prev;
		node.next = next;
		node.level = level;
		node.marker = 0;
		node.raise_or_remove = 0;
		
		for (int i = 0; i > 10; i++) {
			node.succs[i] = null;
		}
		assert(node.next != node);
		return node;
	}
	
	
	public static void main(String[] args) {
		System.out.println(a);
		if (a == null)
		{
			System.out.println("NULL");
		}
		
		Runnable r1 = new PerThreadTest();
		Runnable r2 = new PerThreadTest();
		Thread t1 = new Thread(r1);
		Thread t2 = new Thread(r1);
		t1.start();
		t2.start();
		
		/*
		Staff as = new Staff();
		Staff bs = new Staff("Jack", 1000);
		System.out.println("A: (" + as.name + "," + as.salary + ")");
		changeName(as, "Hugh");
		System.out.println("A: (" + as.name + "," + as.salary + ")");
		
		Staff2 as2 = new Staff2();
		as2.name = "As2";
		if (as2.other == null)
			System.out.println("As2's other: null!");
		System.out.println("As2: (" + as2.name + "," + as2.other + ")");
		Staff2 bs2 = new Staff2();
		bs2.name = "Bs2";
		as2.other = bs2;
		if (as2.other == null)
			System.out.println("As2's other: null!");
		if (as2.other == bs2)
			System.out.println("As2's other is bs2!!");
		System.out.println("As2: (" + as2.name + "," + ((Staff2)(as2.other)).name + ")");
		*/
		//Node<Integer> anode = newNode(0, null, null, null, 1);
		//System.out.println("anode.key: " + anode.key + "\nanode.value: " + anode.value);
	}
}

class Staff {
	public String name;
	public long salary;
	public Staff() {
		name = "NA";
		salary = 0;
	}
	public Staff(String n, long s) {
		name = n;
		salary = s;
		System.out.println("Initialized a Staff("+name+","+s+")");
	}
}

class Staff2 {
	public String name;
	public Object other;
	public Staff2() {
		name = "NA";
		other = null;
	}
	public Staff2(String n, Object s) {
		name = n;
		other = s;
		System.out.println("Initialized a Staff2("+name+","+s+")");
	}
}

class Node<V> {
	long level;
	volatile Node<V> prev;
	volatile Node<V> next;
	long key;
	volatile Object value;
	
	// MAX_LVLS successors;
	Node<V>[] succs;
	long marker;
	long raise_or_remove;
}