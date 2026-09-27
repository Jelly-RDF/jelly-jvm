package eu.neverblink.jelly.core.sparql.internal;

import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral2;
import eu.neverblink.jelly.core.proto.v1.RdfTripleTerm;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlTerm;
import eu.neverblink.protoc.java.runtime.MessageCollection;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Reusable store for the SparqlTerm wrappers of a polymorphic column.
 */
final class TermBuffer
    extends AbstractCollection<SparqlTerm>
    implements MessageCollection<SparqlTerm, SparqlTerm.Mutable>
{

    private SparqlTerm.Mutable[] terms = new SparqlTerm.Mutable[0];
    private int size = 0;

    @Override
    public SparqlTerm.Mutable appendMessage() {
        if (size == terms.length) {
            terms = Arrays.copyOf(terms, Math.max(8, terms.length * 2));
        }
        SparqlTerm.Mutable term = terms[size];
        if (term == null) {
            term = SparqlTerm.newInstance();
            terms[size] = term;
        } else {
            // The setters leave the cached serialized size alone, so a reused wrapper has to be
            // cleared - otherwise the frame would be written with the previous value's length.
            term.clear();
        }
        size++;
        return term;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        // Keeps the wrappers around for the next frame
        size = 0;
    }

    @Override
    public Iterator<SparqlTerm> iterator() {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < size;
            }

            @Override
            public SparqlTerm next() {
                if (index >= size) {
                    throw new NoSuchElementException();
                }
                return terms[index++];
            }
        };
    }
}

/**
 * Reusable store for RdfLiteral messages of a mixed-datatype literal column
 * or a polymorphic column. Literal values are kept as buffer entries while the frame is
 * built (see ColumnState), so messages only get materialized here, at endFrame().
 */
final class LiteralBuffer
    extends AbstractCollection<RdfLiteral2>
    implements MessageCollection<RdfLiteral2, RdfLiteral2.Mutable>
{

    private RdfLiteral2.Mutable[] literals = new RdfLiteral2.Mutable[0];
    private int size = 0;

    @Override
    public RdfLiteral2.Mutable appendMessage() {
        if (size == literals.length) {
            literals = Arrays.copyOf(literals, Math.max(8, literals.length * 2));
        }
        RdfLiteral2.Mutable literal = literals[size];
        if (literal == null) {
            literal = RdfLiteral2.newInstance();
            literals[size] = literal;
        } else {
            literal.clear();
        }
        size++;
        return literal;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        size = 0;
    }

    @Override
    public Iterator<RdfLiteral2> iterator() {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < size;
            }

            @Override
            public RdfLiteral2 next() {
                if (index >= size) {
                    throw new NoSuchElementException();
                }
                return literals[index++];
            }
        };
    }
}

/**
 * Reusable store for the RdfIri messages to be used in polymorphic columns.
 * IRI values are kept as plain ints while the frame is built (see ColumnState), so the
 * messages only get materialized here, at endFrame(), and only for polymorphic columns.
 */
final class IriBuffer {

    private RdfIri.Mutable[] iris = new RdfIri.Mutable[0];
    private int size = 0;

    RdfIri.Mutable append() {
        if (size == iris.length) {
            iris = Arrays.copyOf(iris, Math.max(8, iris.length * 2));
        }
        RdfIri.Mutable iri = iris[size];
        if (iri == null) {
            iri = RdfIri.newInstance();
            iris[size] = iri;
        } else {
            iri.clear();
        }
        size++;
        return iri;
    }

    void clear() {
        size = 0;
    }
}

/**
 * Buffer for a mixed-datatype or polymorphic column. It is separated from
 * ColumnState and created lazily, so that monomorphic columns never allocate any of this.
 * This also saves bytes in the SparqlEncoderImpl object, allowing us to fit it into one cache line.
 */
final class PolyBuffers {

    final TermBuffer terms = new TermBuffer();
    final LiteralBuffer literals = new LiteralBuffer();
    final IriBuffer iris = new IriBuffer();

    // The language tag and base direction shared by every literal of the column in the current
    // frame, while the column's datatype state says so. Kept here rather than in ColumnState,
    // because it's rarely used and ColumnState is much more performance-sensitive (shouldn't use
    // more cache lines).
    String langtag = null;
    RdfBaseDirection direction = RdfBaseDirection.NONE;

    // The triple terms of the current frame, in value order. Built as messages right away, as
    // they are rare – only their prefix ids are resolved at endFrame().
    final ArrayList<RdfTripleTerm.Mutable> tripleTerms = new ArrayList<>();
    // The largest number of IRIs in one triple term of this column so far, across all frames.
    // Used to size the lookup budget of a frame, as a triple term can need a lookup entry for
    // each of its IRIs.
    int maxTripleTermIris = 0;

    void resetFrameState() {
        terms.clear();
        literals.clear();
        iris.clear();
        tripleTerms.clear();
    }
}
