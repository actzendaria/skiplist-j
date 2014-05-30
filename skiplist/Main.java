package skiplist;

import java.util.ArrayList;
import java.util.List;

import algorithms.pattree.PatriciaTree;
import util.MyConcurrentSkipListMap;
import util.SkiplistRotating;

public class Main {

	static final int threadNum = 6;
	static final int totalops = 500000;
	static int ops = totalops/threadNum;
	static final float insert_chance = 0.6f;
	static final float search_chance = 0.2f;
	//static final int ops = 100000;

	public static void main(String[] args) throws InterruptedException {
		//testConcurrentSl();
		//testTrie();
		testNoHotSpotSl();
	}
	
	public static void testNoHotSpotSl() throws InterruptedException {
		SkiplistRotating[] ss = new SkiplistRotating[threadNum];
		SkiplistTest[] ts = new SkiplistTest[threadNum];
		long startTime = System.currentTimeMillis();
		for (int i = 0; i < threadNum; i++) {
			ss[i] = new SkiplistRotating();
		}
		for (int i = 0; i < threadNum; i++) {
			ts[i] = new SkiplistTest(i, ops, ss[i], insert_chance, search_chance);
			ts[i].start();
		}
		for (int i = 0; i < threadNum; i++) {
			ts[i].join();
		}
		long endTime = System.currentTimeMillis();
		System.out.println("Congratulations! time = " + (endTime - startTime)
				+ " ms");
	}

	/*
	public static void testConcurrentSl() throws InterruptedException {
		//SkiplistRotating[] ss = new SkiplistRotating[threadNum];
		//MyConcurrentSkipListMap<Integer, Object>[] ts = new MyConcurrentSkipListMap<Integer, Object>[threadNum];
		List<MyConcurrentSkipListMap<Integer, Object>> ss= new ArrayList<MyConcurrentSkipListMap<Integer, Object>>();		
		ConcurrentSkiplistTest[] ts = new ConcurrentSkiplistTest[threadNum];
		long startTime = System.currentTimeMillis();
		for (int i = 0; i < threadNum; i++) {
			ss.add(i, new MyConcurrentSkipListMap<Integer, Object>());
		}
		for (int i = 0; i < threadNum; i++) {
			ts[i] = new ConcurrentSkiplistTest(i, ops, ss.get(i), insert_chance, search_chance);
			ts[i].start();
		}
		for (int i = 0; i < threadNum; i++) {
			ts[i].join();
		}
		long endTime = System.currentTimeMillis();
		System.out.println("Congratulations! time = " + (endTime - startTime)
				+ " ms");
	}
	
	public static void testTrie() throws InterruptedException {
		List<PatriciaTree<Integer,Object>> ss= new ArrayList<PatriciaTree<Integer,Object>>();		
		TrieTest[] ts = new TrieTest[threadNum];
		long startTime = System.currentTimeMillis();
		for (int i = 0; i < threadNum; i++) {
			ss.add(i, new PatriciaTree<Integer,Object>());
		}
		for (int i = 0; i < threadNum; i++) {
			ts[i] = new TrieTest(i, ops, ss.get(i));
			ts[i].start();
		}
		for (int i = 0; i < threadNum; i++) {
			ts[i].join();
		}
		long endTime = System.currentTimeMillis();
		System.out.println("Congratulations! time = " + (endTime - startTime)
				+ " ms");
	}*/
}
