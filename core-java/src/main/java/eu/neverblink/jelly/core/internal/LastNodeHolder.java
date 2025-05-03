package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.InternalApi;

/**
 * Tiny mutable holder for the last node that occurred as S, P, O, or G.
 * @param <TNode> the type of the node
 */
@InternalApi
public class LastNodeHolder<TNode> {

    /**
     * null indicates that there was no value for this node yet OR that the value is null.
     */
    private TNode node = null;

    /**
     * Indicates whether the last node held a value or not.
     */
    private boolean hasValue = false;

    /**
     * Get the last node.
     * @return the last node
     */
    public TNode get() {
        return node;
    }

    /**
     * Set the last node.
     * @param node the last node
     */
    public void set(TNode node) {
        this.node = node;
        this.hasValue = true;
    }

    /**
     * Check if the last node has a value.
     * @return true if the last node has a value, false otherwise
     */
    public boolean hasValue() {
        return hasValue;
    }

    /**
     * Check if the last node has no value.
     * @return true if the last node has no value, false otherwise
     */
    public boolean hasNoValue() {
        return !hasValue;
    }

    /**
     * Clear the last node.
     */
    public void clear() {
        this.node = null;
        this.hasValue = false;
    }
}
