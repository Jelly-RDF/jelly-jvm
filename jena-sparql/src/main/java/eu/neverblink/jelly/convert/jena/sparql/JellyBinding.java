package eu.neverblink.jelly.convert.jena.sparql;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingBase;

/**
 * A row of a decoded result set: the variables of the result set, shared by all its rows, and
 * this row's values, null where a variable is unbound.
 * <p>
 * Jena's own bindings check on creation that no variable is bound twice, by comparing the names
 * of every pair of variables. The variables of a result set are checked once instead, when its
 * header is read (see {@link RowSetReaderJelly}), so building a row is only an allocation.
 */
final class JellyBinding extends BindingBase {

    private final Var[] vars;
    private final Node[] values;
    private final int size;

    /**
     * @param vars the variables of the result set, all different
     * @param values the values of the variables, null for unbound, not modified after this call
     * @param size the number of values that are not null
     */
    JellyBinding(Binding parent, Var[] vars, Node[] values, int size) {
        super(parent);
        this.vars = vars;
        this.values = values;
        this.size = size;
    }

    @Override
    protected Iterator<Var> vars1() {
        return new Iterator<>() {
            private int next = advance(0);

            private int advance(int from) {
                while (from < values.length && values[from] == null) {
                    from++;
                }
                return from;
            }

            @Override
            public boolean hasNext() {
                return next < values.length;
            }

            @Override
            public Var next() {
                if (next >= values.length) {
                    throw new NoSuchElementException();
                }
                final Var var = vars[next];
                next = advance(next + 1);
                return var;
            }
        };
    }

    @Override
    protected void forEach1(BiConsumer<Var, Node> action) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                action.accept(vars[i], values[i]);
            }
        }
    }

    @Override
    protected int size1() {
        return size;
    }

    @Override
    protected boolean isEmpty1() {
        return size == 0;
    }

    @Override
    protected boolean contains1(Var var) {
        return get1(var) != null;
    }

    @Override
    protected Node get1(Var var) {
        for (int i = 0; i < vars.length; i++) {
            if (vars[i] == var) {
                return values[i];
            }
        }
        for (int i = 0; i < vars.length; i++) {
            if (vars[i].equals(var)) {
                return values[i];
            }
        }
        return null;
    }

    @Override
    protected Binding detachWithNewParent(Binding newParent) {
        return new JellyBinding(newParent, vars, values, size);
    }
}
