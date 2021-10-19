package fr.univ_nantes.burdenOfthePast.examples;

import fr.univ_nantes.burdenOfthePast.universalImplementations.CasUniversal;
import fr.univ_nantes.burdenOfthePast.tools.Universal;
import fr.univ_nantes.burdenOfthePast.tools.Universal.State;

/**
 * An example of an object implemented using the universal construction
 * A wait-free, linearizable counter
 */
public class Counter {

	// Class representing the internal state of the object: an integer
	protected static class S implements State<S> {
		public int value = 0;
		public S(int initial) {
			value = initial;
		}
		public S copy() {
			return new S(value);
		}
	}

	// The instance of the universal construction
	protected final Universal<S> universal;

	public Counter(int initial) {
		// Can be replaced by SwapUniversal
		this.universal = new CasUniversal<S>(new S(initial), rank -> 1 << rank);
	}

	public int get() {
		return universal.invoke(state -> {
			return state.value;
		});
	}

	public void add(int delta) {
		universal.invoke(state -> {
			state.value += delta;
			return null;
		});
	}
	
	public static void main(String[] args) {
		Counter x = new Counter(0);
		for(int i = 0; i<10; i++) {
			new Thread(() -> {
				x.add(1);
				System.out.println(x.get());
			}).start();
		}
	}

}
