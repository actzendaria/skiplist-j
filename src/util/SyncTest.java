package util;

import java.io.*;
import java.lang.Thread;
import java.lang.ThreadLocal;
import java.util.*;
/*!!!!!!!!!!!!?????????为什么Counter不用Sync值也没出错？？？*/
/*http://tutorials.jenkov.com/java-concurrency/synchronized.html#java-synchronized-example*/
public class SyncTest {
	
	public static void main(String[] args) {
		/*Runnable r1 = new PerThreadTest();
		Runnable r2 = new PerThreadTest();
		Thread t1 = new Thread(r1);
		Thread t2 = new Thread(r1);
		t1.start();
		t2.start();*/
		Counter counter = new Counter();
		Thread  threadA = new CounterThread(counter);
		Thread  threadB = new CounterThread(counter);
		Thread  threadC = new CounterThread(counter);
		Thread  threadD = new CounterThread(counter);

		threadA.start();
		threadB.start();
		threadC.start();
		threadD.start();
	}

}

class Counter{
    long count = 0;
   
    public /*synchronized*/ void add(long value){
      System.out.println(Thread.currentThread().getName() + "': " + count + " ");
      this.count += value;
    }
 }

class CounterThread extends Thread{

    protected Counter counter = null;

    public CounterThread(Counter counter){
       this.counter = counter;
    }

    public void run() {
    	for(int i=0; i<10000; i++){
          counter.add(1);
          //System.out.println(Thread.currentThread().getName() + ": " + counter.count + " ");
       }
    }
 }