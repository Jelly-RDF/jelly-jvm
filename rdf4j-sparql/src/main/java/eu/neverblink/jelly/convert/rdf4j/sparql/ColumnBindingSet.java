package eu.neverblink.jelly.convert.rdf4j.sparql;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.AbstractBindingSet;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.impl.ListBindingSet;
import org.eclipse.rdf4j.query.impl.SimpleBinding;

/**
 * A row of a decoded result set: the variables of the result set, and the columns of the frame the
 * row came from, with its index in them. A value is null where a variable is unbound.
 * <p>
 * The same as a ListBindingSet of the row's values, but the values are not copied out of the
 * columns: making a row is one allocation. A row keeps the columns of its frame in memory, as long
 * as it is kept itself. It is serialized as a ListBindingSet of its own values.
 */
final class ColumnBindingSet extends AbstractBindingSet {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<String> names;
    // All the names, bound or not, as ListBindingSet has them. Shared by the rows, not modifiable.
    private final Set<String> nameSet;
    private final transient Object[][] columns;
    private final int row;

    /**
     * @param names the variables of the result set
     * @param nameSet the same, as a set
     * @param columns the values of the frame, one array per variable, each value a Value or null.
     *                Not modified after this call.
     * @param row the index of this row in the columns
     */
    ColumnBindingSet(List<String> names, Set<String> nameSet, Object[][] columns, int row) {
        this.names = names;
        this.nameSet = nameSet;
        this.columns = columns;
        this.row = row;
    }

    private Value value(int index) {
        return (Value) columns[index][row];
    }

    @Override
    public Set<String> getBindingNames() {
        return nameSet;
    }

    @Override
    public Value getValue(String bindingName) {
        final int index = names.indexOf(bindingName);
        return index < 0 ? null : value(index);
    }

    @Override
    public Binding getBinding(String bindingName) {
        final Value value = getValue(bindingName);
        return value == null ? null : new SimpleBinding(bindingName, value);
    }

    @Override
    public boolean hasBinding(String bindingName) {
        return getValue(bindingName) != null;
    }

    @Override
    public Iterator<Binding> iterator() {
        return new Iterator<>() {
            private int next = advance(0);

            private int advance(int from) {
                while (from < columns.length && columns[from][row] == null) {
                    from++;
                }
                return from;
            }

            @Override
            public boolean hasNext() {
                return next < columns.length;
            }

            @Override
            public Binding next() {
                if (next >= columns.length) {
                    throw new NoSuchElementException();
                }
                final Binding binding = new SimpleBinding(names.get(next), value(next));
                next = advance(next + 1);
                return binding;
            }
        };
    }

    @Override
    public int size() {
        int size = 0;
        for (final Object[] column : columns) {
            if (column[row] != null) {
                size++;
            }
        }
        return size;
    }

    @Serial
    private Object writeReplace() {
        final List<Value> values = new ArrayList<>(columns.length);
        for (int i = 0; i < columns.length; i++) {
            values.add(value(i));
        }
        return new ListBindingSet(names, values);
    }
}
