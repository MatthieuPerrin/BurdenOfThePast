package fr.univ_nantes.burdenOfthePast.tools;

/**
 * An object reference whose content may be swapped atomically with the content of another Swappable reference.
 * 
 * Note that this implementation is only a deadlock-free simulation of the atomic special instruction we suppose in the paper.
 * This technically renders classes examples.SwapQueue and universalImplementations.SwapUniversal deadlock-free as well,
 * although the present code is only given for illustration purpose.
 * 
 * @author Matthieu Perrin
 * @param <T> The type of object referred to by this reference
 */
public class Swappable<T> {

	private T value = null;

	/**
	 * Creates a new Swappable with the given initial value
	 * @param value the initial value
	 */
	public Swappable(T value) { 
		this.value = value; 
	}
	/**
	 * Gets the current value.
	 */
	public synchronized T get() { 
		return value; 
	}
	/**
	 * Sets to the given value.
	 * @param newValue the new value
	 */
	public synchronized void set(T newValue) { 
		this.value = newValue; 
	}
	/**
	 * Atomically exchanges the referenced value with the value referenced by the other swappable object
	 * @param other the reference with which the values must be swapped
	 */
	public void swap(Swappable<T> other) {
		if(hashCode() <= other.hashCode()) 
			synchronized(this) { synchronized(other) {
				T temp = value;
				value = other.value;
				other.value = temp;
			}}
		else other.swap(this);
	}
}
