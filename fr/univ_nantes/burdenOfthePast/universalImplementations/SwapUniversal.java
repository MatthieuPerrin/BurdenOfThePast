package fr.univ_nantes.burdenOfthePast.universalImplementations;

import fr.univ_nantes.burdenOfthePast.tools.Swappable;
import fr.univ_nantes.burdenOfthePast.tools.Universal;
import fr.univ_nantes.burdenOfthePast.tools.Universal.*;


/**
 * This class shows a wait-free universal construction based on the swap instruction
 * @see Algo. 3 in the research article : 
 *   Denis Bédin, François Lépine, Achour Mostéfaoui, Damien Perez, and Matthieu Perrin. Wait-free Algorithms: the Burden of the Past
 * @param <S> Type of the states of the simulated object
 * @author Matthieu Perrin
 */
public class SwapUniversal<S extends State<S>> implements Universal<S> {

	/**
	 * Definition of the nodes of Algo. 3
	 * @param <S> Type of the states of the simulated object
	 * @param <R> Return type of the operation that issued the node
	 */
	private static class Node<S extends State<S>, R> {
		// pointer to the previous operation in the linearization order
		private final Swappable<Node<S,?>> previous;
		// operation whose invocation resulted in the creation of this block
		private final Operation<S,R> operation;
		// resulting state after this operation is completed
		private volatile S s = null;
		// Return value of the operation
		private volatile R result = null;
		
		/**
		 * Class constructor: creates a node whose previous node is itself and the operation is given in parameter
		 * Used on Line 4-5 of Algo 3.
		 * @param operation operation whose invocation resulted in the creation of this block
		 */
		public Node(Operation<S,R> operation) {	
			this.operation = operation; 
			this.previous = new Swappable<Node<S,?>>(this);
		}

		/**
		 * Class constructor: creates a node that represents the initial state passed in argument
		 * used on Line 2 of Algo. 3.
		 * @param initialState the initial state of the automaton
		 */
		public Node(S initialState) {
			this.operation = null;
			this.s = initialState;
			this.previous = new Swappable<Node<S,?>>(null);
		}

		/**
		 * Executes recursively all the operations that were linearized before this one, including this one
		 */
		public void help() {
			// First, read previous, then read s;
			// This ensures that, if s == null, then previous != this
			Node<S,?> previous = this.previous.get();                                           // Line 10
			if(previous != null) {                                                              // Line 11
				// recursively help the previous operations to complete
				previous.help();                                                                // Line 12
				// execute the operation to get the new state and return value
				// Lines 13-15
				S s = previous.s.copy();
				this.result = operation.invoke(s);
				this.s = s;
				// unset previous to allow the garbage collector to free the previous nodes
				// it would also work to set previous to null
				this.previous.set(this);                                                        // Line 16
			}
		}
	}

	private final Swappable<Node<S,?>> last;

	/**
	 * Creates a wait-free, linearizable simulator of an automaton
	 * @param initial The initial state of the shared object implemented by the universal construction
	 */
	public SwapUniversal(S initial) { 
		last = new Swappable<Node<S,?>>(new Node<S,Void>(initial)); // Line 2
	}
	
	/**
	 * Performs the given operation in a linearizable manner
	 * @param operation the operation that must be performed
	 * @return a result of the operation, so that it is linearizable
	 */
	public <R> R invoke(Operation<S, R> operation) {
		Node<S,R> node = new Node<S,R>(operation); // Line 4-5
		last.swap(node.previous);                  // Line 6
		node.help();                               // Line 7
		return node.result;                        // Line 8
	}
}
