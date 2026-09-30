package eu.neverblink.jelly.convert.jena.sparql;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingBase;

/**
 * A row of a decoded result set: the variables of the result set, and the columns of the frame the
 * row came from, with its index in them. A value is null where a variable is unbound.
 * <p>
 * Jena's own bindings check on creation that no variable is bound twice, by comparing the names
 * of every pair of variables. The variables of a result set are checked once instead, when its
 * header is read (see {@link RowSetReaderJelly}). The values are not copied out of the columns,
 * so building a row is only an allocation. A row keeps the columns of its frame in memory, as
 * long as it is kept itself.
 */
final class JellyBinding extends BindingBase {

    private final Var[] vars;
    private final Object[][] columns;
    private final int row;

    /**
     * @param vars the variables of the result set, all different
     * @param columns the values of the frame, one array per variable, each value a Node or null.
     *                Not modified after this call.
     * @param row the index of this row in the columns
     */
    JellyBinding(Binding parent, Var[] vars, Object[][] columns, int row) {
        super(parent);
        this.vars = vars;
        this.columns = columns;
        this.row = row;
    }

    private Node value(int index) {
        return (Node) columns[index][row];
    }

    @Override
    protected Iterator<Var> vars1() {
        return new Iterator<>() {
            private int next = advance(0);

            private int advance(int from) {
                while (from < vars.length && value(from) == null) {
                    from++;
                }
                return from;
            }

            @Override
            public boolean hasNext() {
                return next < vars.length;
            }

            @Override
            public Var next() {
                if (next >= vars.length) {
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
        for (int i = 0; i < vars.length; i++) {
            final Node node = value(i);
            if (node != null) {
                action.accept(vars[i], node);
            }
        }
    }

    @Override
    protected int size1() {
        int size = 0;
        for (int i = 0; i < vars.length; i++) {
            if (value(i) != null) {
                size++;
            }
        }
        return size;
    }

    @Override
    protected boolean isEmpty1() {
        return size1() == 0;
    }

    @Override
    protected boolean contains1(Var var) {
        return get1(var) != null;
    }

    @Override
    protected Node get1(Var var) {
        for (int i = 0; i < vars.length; i++) {
            if (vars[i] == var) {
                return value(i);
            }
        }
        for (int i = 0; i < vars.length; i++) {
            if (vars[i].equals(var)) {
                return value(i);
            }
        }
        return null;
    }

    @Override
    protected Binding detachWithNewParent(Binding newParent) {
        return new JellyBinding(newParent, vars, columns, row);
    }
}
