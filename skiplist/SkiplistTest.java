package skiplist;

import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import util.MyConcurrentSkipListMap;
import util.SkiplistRotating;
//import java.util.concurrent.ConcurrentSkipListMap;

public class SkiplistTest {
	
	public static void main(String... args){

		MyConcurrentSkipListMap<String, String> ob= new MyConcurrentSkipListMap<String, String>();
		ob.put("3","A");
		ob.put("2","B");
		ob.put("1","C");
		ob.put("5","D");
		ob.put("4","E");
       //returns a key-value mapping of the least value but greater than the given key.
		System.out.println("(My) ceinling entry of 3:"+ob.ceilingEntry("3"));
		
		// returns the NavigableSet in reverse order
		 NavigableSet ns=ob.descendingKeySet();
		 
		System.out.println("Values in reverse order......");
		 Iterator itr=ns.iterator();
		 while(itr.hasNext()){
			 String s = (String)itr.next();
			 System.out.println(s);
		 }
        
		 // returns the  key value pair of least key in the map
		 System.out.println("Value with least key: "+ob.firstEntry());
		 
		 // returns the  key value pair of greatest key in the map
		 System.out.println("Value with greatest key: "+ob.lastEntry());
		 
		 //returns the lowest entry and also removes from the map
		 System.out.println("value removed from the map:"+ob.pollFirstEntry());
		 
		 //returns the greatest entry and also removes from the map
		 System.out.println("value removed from the map:"+ob.pollLastEntry());


        SkiplistRotating sl = new SkiplistRotating();
        sl.insert(3, "A");
        sl.insert(2, "B");
        sl.insert(1, "C");
        sl.insert(5, "D");
        sl.insert(4, "E");

        for (int i = 1; i < 6; i++)  {
            int ret = sl.contains(i);
            System.out.println(ret);
        }
	}
}