package eu.neverblink.jelly.core.internal;

/**
 * Tiny mutable holder for the last node that occurred as S, P, O, or G.
 * @param <TNode> the type of the node
 */
public class LastNodeHolder<TNode> {

    /**
     * null indicates that there was no value for this node yet.
     */
    TNode node = null;
}
