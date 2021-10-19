package fr.univ_nantes.burdenOfthePast.universalImplementations;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import fr.univ_nantes.burdenOfthePast.tools.Universal;
import fr.univ_nantes.burdenOfthePast.tools.Universal.*;

/**
 * This class shows a wait-free universal construction based on compareAndSet
 * @see Algo. 2 in the research article : 
 *   Denis Bédin, François Lépine, Achour Mostéfaoui, Damien Perez, and Matthieu Perrin. Wait-free Algorithms: the Burden of the Past
 * @param <S> Type of the states of the simulated object
 * @author Matthieu Perrin
 */
public class CasUniversal<S extends State<S>> implements Universal<S> {

	/**
	 * Represents the inverse of the parameter function f in Algo. 2. 
	 * This function has an influence on both the space complexity and the time complexity. Namely,
	 *   - When less than n processes access the universal construction, at most Frank.f^{-1}(n) AnnounceNode objects are accessible in the list;
	 *   - When more than n processes access the universal construction concurrently, the algorithm behaves as a lock-free data structure for Frank.f(n) iterations, and then takes measures to force termination
	 *   
	 * When no Frank object is specified, a CasUniversal object uses rank->1<<rank, which represents an exponential function, hence a logarithmic space complexity.
	 */
	public interface Frank { int f(int rank); }
	
	/**
	 * Definition of an operation node
	 * @param <R> The return type of the operation stored in the operation node
	 */
	private class OperationNode <R> {
		final Operation<S, R> operation;
		// result does not need to be volatile because it is always updated before done is set to true, and it is always read after true is read in done. 
		// Hence, the correctness of the value read in result is ensured by the causality provided by volatile on done, and atomicity of result is not required
		R result = null;
		volatile boolean done;
		/**
		 * Creates an operation node for a given operation
		 * Used on Line 8 of Algo. 2
		 * @param operation: the operation that must be executed
		 */
		OperationNode (Operation<S, R> operation) {
			this.operation = operation;
			this.done = false;
		}
		/**
		 * Creates a dummy operation node, referenced by the initial linearization Node (Line 3 of Algo. 2)
		 */
		OperationNode () {
			this.operation = null;
			this.done = true;
		}
		/**
		 * Tries to execute the operation, until it is encoded into the current state of the state machine
		 * Implements Lines 24-32 of Algo. 2
		 */
		void linearize() {
			while(true) {                                     // Line  24
				LinearizationNode<?> l = linearization.get(); // Line  25
				l.reportResult();                             // Lines 26-27
				if (done) return;                             // Line  28
				linearization.compareAndSet(l, new LinearizationNode<R>(l.state, this)); // Lines 29-32
			}
		}
	}

	/**
	 * Definition of an announce node
	 */
	private class AnnounceNode {
		final AnnounceNode next;
		volatile OperationNode<?> o = null;
		final int rank;
		// fRank is the constant f(rank), pre-computed to improve complexity
		final int fRank;
		/**
		 * Creates a new announce node
		 * Used in Lines 2 and 12 of Algo. 2
		 * @param a the reference to the rest of the linked list
		 */
		AnnounceNode(AnnounceNode a) {
			this.next = a;
			this.rank = (a==null ? 0 : a.rank+1);
			this.fRank = frank.f(rank+1);
		}
		/**
		 * Tries to linearizes the operation stored into the operation node accessible from the announce node
		 * Implements Lines 19-32 of Algo 2.
		 */
		void help() {
			if(next != null) next.help();   // Lines 20-21
			OperationNode<?> o1 = o;        // Line  22
			if (o1 != null) o1.linearize(); // Lines 23-32
		}
	}

	/**
	 * Definition of a linearization node
	 * @param <R> The return type of the last operation used to create the linearization node
	 */
	private class LinearizationNode <R> {
		final S state;
		final R result;
		final OperationNode<R> o;
		/**
		 * Creates a new Linearization node by executing an operation on a state
		 * Used on Lines 29-31 of Algo. 2
		 * @param previousState the state of the automaton, prior to the execution of the last operation
		 * @param o the last operation executed on the operation node
		 */
		LinearizationNode (S previousState, OperationNode<R> o) {
			// Instead of having two methods newState and returned, execute(previousState, o) 
			// modifies the state and returns a return value in our implementation
			this.o = o;
			this.state = previousState.copy();
			this.result = o.operation.invoke(state);
		}
		/**
		 * Creates a new linearization node that containes the initial state of the state machine
		 * Used on Line 4 of Algo. 2
		 * @param initialState the initial state of the state machine
		 */
		LinearizationNode (S initialState) {
			this.o = new OperationNode<R>();
			this.state = initialState;
			this.result = null;
		}
		/**
		 * Reports the return value from the linearization node to the operation node
		 * Implements Lines 26-27 of Algo. 2
		 * Remark: this method is necessary for type safety, as the type R might not be known when reportResult is called
		 */
		void reportResult() {
			o.result = result; // Line 26
			o.done = true;     // Line 27
		}
	}
	
	private final AtomicReference<AnnounceNode> announces;
	private final AtomicReference<LinearizationNode<?>> linearization;
	private final Frank frank;
	
	// Used to execute compareAndSet on AnnounceNode.o
	@SuppressWarnings("rawtypes")
	private final static AtomicReferenceFieldUpdater<CasUniversal.AnnounceNode, CasUniversal.OperationNode> oUpdater = 
			AtomicReferenceFieldUpdater.newUpdater(CasUniversal.AnnounceNode.class, CasUniversal.OperationNode.class, "o");


	/**
	 * Creates a wait-free, linearizable simulator of an automaton
	 * @param initialState the initial state of the automaton
	 * @param inverseComplexity a function from int->int that determines the inverse of the space complexity of the simulator
	 */
	public CasUniversal(S initialState, Frank inverseComplexity) {
		frank = inverseComplexity;
		announces = new AtomicReference<AnnounceNode>(new AnnounceNode(null));                                // Line 2
		linearization = new AtomicReference<LinearizationNode<?>>(new LinearizationNode<Void>(initialState)); // Lines 3-4
	}

	/**
	 * Creates a wait-free, linearizable simulator of an automaton
	 * By default, the inverse complexity parameter is set to f^{-1}(n) = 2^n, for a logarithmic complexity
	 * @param initialState the initial state of the automaton
	 */
	public CasUniversal(S initialState) {
		this(initialState, rank->1<<rank);
	}

	
	/**
	 * Performs the given operation in a wait-free linearizable manner
	 * Implements Lines 7-18 of Algo. 2
	 * @param operation the operation that must be performed
	 * @return a result of the operation, so that it is linearizable
	 */
	public <R> R invoke(Operation<S, R> operation) {
		// We fix once and for all the announce node in which we will try to install our operation
		OperationNode<R> o = new OperationNode<R>(operation);       // Line  8
		AnnounceNode a = announces.get();                           // Line  9
		for(int k=0;;k++) {                                         // Line  10
			// Helping has been unsuccessful for too long: 
			// we make sure that future operations will not be concurrent to "operation"
			// on their insertion into "a.o"
			// Remark that this CAS can be lost, as we only need that "a" is 
			// no more the first node of announces
			if(k==a.fRank)                                          // Line  11
				announces.compareAndSet(a, new AnnounceNode(a));    // Line  12-13
			// Help the operation that is currently stored in the announce node
			OperationNode<?> o1 = a.o;                              // Line  14
			a.help();												// Line  15
			if(o.done) return o.result;								// Lines 16-17
			// Try to install the current operation into the announce node
			oUpdater.compareAndSet(a,o1,o);					 		// Line  18: a.o.compareAndSet(o1,o)
		}
	}

}
