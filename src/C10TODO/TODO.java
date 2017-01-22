package C10TODO;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

public class TODO {

  // - final fields
  // - Actors. Do not share state. You can only pass a message to an actor's mailbox.
  //
  // Further reading:
  // - Brian Goetz. Java concurrency in practice.
  // - java.util.concurrent collections http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/package-summary.html
  // - https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html
  // - Shipilev JMM lectures https://shipilev.net/
  //
  // Exercise 1.
  // Implement:
  interface Counters {
    void increment(String tag); // called often from different threads
    Map<String, Long> getCountersAndClear(); // called rarely (for example once per minute by a scheduled thread)
  }
  // Can be used for example to accumulate number of views per resume.
  // Each resume view increments counter with tag = resumeId.
  // A scheduled thread once per minute gets accumulated data and sends it to a stats system.
  // In the same time all internal data structures are cleared to reduce memory footprint.
  // You can use java.util.concurrent but not any external library.

  // Uses atomic operations of ConcurrentHashMap
  class HashMapCounters implements Counters {
    private ConcurrentHashMap<String, Long> map;

    public HashMapCounters() {
      map = new ConcurrentHashMap<String, Long>();
    }

    public void increment(String tag) {
      map.merge(tag, 1L, Long::sum);
    }

    public Map<String, Long> getCountersAndClear() {
      Map<String, Long> result = new HashMap<String, Long>();

      // synchronization is used to prevent several threads from running getCountersAndClear at the same time
      synchronized (this) {
        // all keys are copied, because removing of elements from a collection cannot be done atomically while 
        // iterating this collection
        Set<String> keySet = new HashSet<String>(map.keySet());
        for (String key : keySet) {
          Long removedValue = map.remove(key);
          if (removedValue != null)
            result.put(key, removedValue);
        }
      }

      return result;
    }
  }


  //
  // Exercise 2.
  // Given:
  interface Bucket {
    void awaitDrop(); // blocks until some other thread calls Drop.arrived()
                      // second invocation of the await() must wait another thread to call Drop.ready()
    void leak();      // unblocks a thread arrived at Drop.arrived() point in a FIFO order.
  }
  interface Drop {
    void arrived();  // notifies Bucket.awaitDrop() that a thread has arrived to a barrier.
                     // then blocks until Bucket.leak() is called.
  }
  // Implement class BucketBarrier implements Bucket, Drop.
  // It is useful for example for testing purposes.
  // You create several worker threads that arrive to some critical point (they drop into the bucket).
  // In the test you ensure that all "drops" are in the "bucket" by calling Bucket.awaitDrop() method.
  // Finally you let worker threads to pass a barrier one by one (the bucket leaks drops one by one).
  // You can NOT use java.util.concurrent.

  class BucketBarrier implements Bucket, Drop {
    private Object bucketLock = new Object();
    private long bucketHeadId = 0; // Number of times awaitDrop was notified about new drops
    private long bucketTailId = 0; // Minimal free id of a drop, which will be used next

    private Object dropLock = new Object();
    private long dropHeadId = 0; // Minimal id of a drop, which is not allowed to pass yet
    private long dropTailId = 0; // Minimal free id of a drop, which will be used next

    public void awaitDrop() {
      try {
        synchronized (bucketLock) {
          while (bucketHeadId >= bucketTailId)
            bucketLock.wait();
        }
        ++bucketHeadId;
      }
      catch (InterruptedException e) {
      }
    }

    public void leak() {
      synchronized (dropLock) {
        ++dropHeadId;
        // notifyAll is used because the thread with minimal id must be selected from all waiting threads
        dropLock.notifyAll(); // notifies arrived()
      }
    }

    public void arrived() {
      try {
        synchronized (bucketLock) {
          ++bucketTailId;
          bucketLock.notify(); // notifies awaitDrop()
        }
        synchronized (dropLock) {
          ++dropTailId;
          long currentId = dropTailId;
          while (dropHeadId < currentId)
            dropLock.wait();
        }
      }
      catch (InterruptedException e) {
      }
    }
  }
}
