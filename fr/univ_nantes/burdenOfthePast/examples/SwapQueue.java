package fr.univ_nantes.burdenOfthePast.examples;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import fr.univ_nantes.burdenOfthePast.tools.Swappable;

/**
 * This class shows a wait-free linearizable queue based on compareAndSet and Swap
 * @see Algo. 4 in the research article : 
 *   Denis Bédin, François Lépine, Achour Mostéfaoui, Damien Perez, and Matthieu Perrin. Wait-free Algorithms: the Burden of the Past
 * @param <T> Type of the value stored in the queue
 * @author Matthieu Perrin
 */
public class SwapQueue<T> {
	

	/**
	 * Definition of enqueue nodes
	 */
	private class EnqueueNode {

		private final Swappable<EnqueueNode> previous;
		private final T value;
		private volatile EnqueueNode next = null;

		/**
		 * Creates a dummy enqueue node used at system setup
		 * Used on Lines 2 of Algo. 4
		 */
		public EnqueueNode() { 
			this.previous = new Swappable<EnqueueNode>(null);
			this.value = null; 
		}

		/**
		 * Creates an enqueue node that contains the given value
		 * Used on Lines 7-8 of Algo. 4
		 * @param value a value to be stored into the queue
		 */
		public EnqueueNode(T value) { 
			this.previous = new Swappable<EnqueueNode>(this);
			this.value = value; 
		}
		
		/**
		 * Recursively help previously linearized enqueue operations to terminate
		 * Implements Line 11-16 of Algo. 4
		 */
		public void help() {
			EnqueueNode p = previous.get(); // Line 12
			if(p!=null) {                   // Line 13
				p.help();                   // Line 14
				p.next = this;              // Line 15
				previous.set(null);         // Line 16
			}
		}
	}
	
	/**
	 * Definition of dequeue nodes
	 */
	private class DequeueNode {
		
		final Swappable<DequeueNode> previous;
		volatile T value = null;
		volatile EnqueueNode match;

		/**
		 * Creates a dummy dequeue node used at system setup
		 * Used on Line 3 of Algo. 4
		 */
		public DequeueNode(EnqueueNode e) { 
			this.previous = new Swappable<DequeueNode>(null);
			this.match = e;
		}

		/**
		 * Creates a dequeue node
		 * Used on Lines 18-19 of Algo. 4
		 * @param value a value to be stored into the queue
		 */
		public DequeueNode() { 
			this.previous = new Swappable<DequeueNode>(this);
		}

		
		/**
		 * Recursively help previously linearized dequeue operations to terminate
		 * Implements Line 23-33 of Algo. 4
		 */
		void help() {
			DequeueNode p = previous.get();                // Line 24
			if(p!=null) {                                  // Line 25
				p.help();                                  // Line 26
				EnqueueNode pe = p.match;                  // Line 27
				EnqueueNode e = pe.next;                   // Line 28
				if(e == null) e = pe;                      // Line 29
				matchUpdater.compareAndSet(this, null, e); // Line 30: match.compareAndSet(null,e)
				e = match;                                 // Line 31
				if(e != pe) value = e.value;               // Line 32
				previous.set(null);                        // Line 33
			}
		}
	}

	private final Swappable<EnqueueNode> enqueues;
	private final Swappable<DequeueNode> dequeues;

	// Used to execute compareAndSet on DequeueNode.match
	@SuppressWarnings("rawtypes")
	private final static AtomicReferenceFieldUpdater<SwapQueue.DequeueNode, SwapQueue.EnqueueNode> matchUpdater = 
			AtomicReferenceFieldUpdater.newUpdater(SwapQueue.DequeueNode.class, SwapQueue.EnqueueNode.class, "match");

	/**
	 * Creates a wait-free linearizable queue object
	 */
	public SwapQueue() {
		EnqueueNode e0 = new EnqueueNode();                            // Line 2
		enqueues = new Swappable<EnqueueNode>(e0);                     // Line 4
		dequeues = new Swappable<DequeueNode>(new DequeueNode(e0));    // Line 5
	}
	/**
	 * Adds the given value at the end of the queue
	 * @param value the value to enqueue
	 */
	public void enqueue (T value) {
		EnqueueNode n = new EnqueueNode(value);                        // Line 7-8
		enqueues.swap(n.previous);                                     // Line 9
		n.help();                                                      // Line 10
	}

	/**
	 * Deletes the value at the beginning of the queue and returns it
	 * @return the dequeued value, if any, or null if the queue is empty
	 */
	public T dequeue() {
		DequeueNode d = new DequeueNode();                              // Line 18-19
		dequeues.swap(d.previous);                                      // Line 20
		d.help();                                                       // Line 21
		return d.value;                                                 // Line 22
	}
}