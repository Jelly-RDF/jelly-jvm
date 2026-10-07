package eu.neverblink.jelly.convert.jena.sparql;

import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlStreamType;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
import eu.neverblink.jelly.core.sparql.JellySparqlIoUtils;
import eu.neverblink.jelly.core.sparql.JellySparqlOptions;
import eu.neverblink.jelly.core.sparql.SparqlDecoder;
import eu.neverblink.jelly.core.sparql.SparqlResultsHandler;
import eu.neverblink.jelly.core.utils.IoUtils;
import eu.neverblink.protoc.java.runtime.DelimitedMessageReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.rowset.RowSetReader;
import org.apache.jena.riot.rowset.RowSetReaderFactory;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingBuilder;
import org.apache.jena.sparql.engine.binding.BindingFactory;
import org.apache.jena.sparql.exec.QueryExecResult;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.util.Context;

/**
 * Jena RowSet reader for the Jelly-SPARQL format.
 * <p>
 * Both delimited and non-delimited inputs are accepted (autodetected). Delimited streams are
 * read frame-by-frame, so the returned RowSet is streaming.
 * <p>
 * If the stream ends with a trailer containing an error, the RowSet returns all rows that were
 * received, and then throws a RiotException. A stream that ends without a trailer is accepted,
 * unless {@link Options#requireTrailer()} is set.
 * <p>
 * To read a PUNCTUATED stream, with a sequence of result sets, use
 * {@link #readAll(InputStream, Context, Consumer)}.
 */
public final class RowSetReaderJelly implements RowSetReader {

    /**
     * Options for the Jelly-SPARQL reader.
     *
     * @param supportedOptions options supported by the reader
     * @param maxRowsPerFrame largest row count a single frame may declare
     * @param requireTrailer whether to throw when the stream ends without a trailer
     */
    public record Options(SparqlResultsOptions supportedOptions, int maxRowsPerFrame, boolean requireTrailer) {
        public Options() {
            this(JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS);
        }

        public Options(SparqlResultsOptions supportedOptions) {
            this(supportedOptions, JellySparqlConstants.DEFAULT_MAX_ROWS_PER_FRAME);
        }

        public Options(SparqlResultsOptions supportedOptions, int maxRowsPerFrame) {
            this(supportedOptions, maxRowsPerFrame, false);
        }

        /**
         * Returns these options with whatever Jena's Context sets on top of them. Settings absent
         * from the context are left as-is.
         *
         * @param context the context to read, may be null
         * @return the updated options
         * @see JellySparqlLanguage#SYMBOL_SUPPORTED_OPTIONS
         */
        public Options withContext(Context context) {
            if (context == null) {
                return this;
            }
            return new Options(
                context.get(JellySparqlLanguage.SYMBOL_SUPPORTED_OPTIONS, this.supportedOptions),
                context.getInt(JellySparqlLanguage.SYMBOL_MAX_ROWS_PER_FRAME, this.maxRowsPerFrame),
                context.isDefined(JellySparqlLanguage.SYMBOL_REQUIRE_TRAILER)
                    ? context.isTrue(JellySparqlLanguage.SYMBOL_REQUIRE_TRAILER)
                    : this.requireTrailer
            );
        }
    }

    /**
     * Factory creating readers with the default options.
     */
    public static final RowSetReaderFactory FACTORY = lang ->
        new RowSetReaderJelly(new Options(), JenaSparqlConverterFactory.getInstance());

    private final Options options;
    private final JenaSparqlConverterFactory converterFactory;

    public RowSetReaderJelly(Options options, JenaSparqlConverterFactory converterFactory) {
        this.options = options;
        this.converterFactory = converterFactory;
    }

    @Override
    public QueryExecResult readAny(InputStream in, Context context) {
        final Object result = readInternal(in, options.withContext(context));
        if (result instanceof Boolean askResult) {
            return new QueryExecResult(askResult);
        }
        return new QueryExecResult((RowSet) result);
    }

    @Override
    public RowSet read(InputStream in, Context context) {
        final Object result = readInternal(in, options.withContext(context));
        if (result instanceof Boolean) {
            throw new RiotException("The stream carries a boolean (ASK) result, not bindings. Use readAny to read it.");
        }
        return (RowSet) result;
    }

    /**
     * Reads a stream of any type, passing each of its result sets to the consumer in turn. A FLAT
     * stream has one result set, a PUNCTUATED stream may have many.
     * <p>
     * The rows of a result set are read from the stream as the consumer asks for them, so they
     * must be read before the consumer returns. The rows it does not read are skipped. A result
     * set that ends with an error makes this throw after its rows, and the ones after it are not
     * read.
     *
     * @param in the stream to read
     * @param context Jena context with the reader settings, may be null
     * @param consumer receives the result sets, in stream order
     */
    public void readAll(InputStream in, Context context, Consumer<QueryExecResult> consumer) {
        final Options options = this.options.withContext(context);
        final RowCollector handler = new RowCollector();
        final SparqlDecoder decoder = converterFactory.decoder(
            handler,
            options.supportedOptions().clone().setStreamType(SparqlStreamType.PUNCTUATED),
            options.maxRowsPerFrame()
        );
        try {
            final FrameReader reader = openReader(in, decoder, options);
            Object result = nextResult(handler, reader, decoder);
            if (result == null) {
                throw new RiotException("No result set found in the input.");
            }
            while (result != null) {
                if (result instanceof Boolean askResult) {
                    consumer.accept(new QueryExecResult(askResult));
                } else {
                    final RowSet rowSet = (RowSet) result;
                    consumer.accept(new QueryExecResult(rowSet));
                    while (rowSet.hasNext()) {
                        rowSet.next();
                    }
                }
                result = nextResult(handler, reader, decoder);
            }
        } catch (IOException e) {
            throw new RiotException(e);
        }
    }

    /**
     * Reads the stream, returning either a RowSet (bindings) or a Boolean (ASK result).
     */
    private Object readInternal(InputStream in, Options options) {
        final RowCollector handler = new RowCollector();
        final SparqlDecoder decoder = converterFactory.decoder(
            handler,
            options.supportedOptions(),
            options.maxRowsPerFrame()
        );
        try {
            final FrameReader reader = openReader(in, decoder, options);
            final Object result = nextResult(handler, reader, decoder);
            if (result == null) {
                throw new RiotException("No result set header found in the input.");
            }
            if (result instanceof Boolean) {
                // Read to the end of the stream: the decoder rejects any frame after this one
                while (reader.readFrame()) {
                    handler.checkError();
                }
            }
            return result;
        } catch (IOException e) {
            throw new RiotException(e);
        }
    }

    private static FrameReader openReader(InputStream in, SparqlDecoder decoder, Options options) throws IOException {
        final IoUtils.AutodetectDelimitingResponse response = JellySparqlIoUtils.autodetectDelimiting(in);
        return new FrameReader(response.newInput(), response.isDelimited(), decoder, options.requireTrailer());
    }

    /**
     * Reads frames until the next result set starts.
     *
     * @return the boolean result, a RowSet that streams the rows of the result set, or null if
     *         the stream has ended
     */
    private static Object nextResult(RowCollector handler, FrameReader reader, SparqlDecoder decoder)
        throws IOException {
        handler.startResultSet();
        while (handler.vars == null && handler.askResult == null && reader.readFrame()) {
            // Errors are reported by the iterator, after the rows received before them
        }
        if (handler.askResult != null) {
            handler.checkError();
            return handler.askResult;
        }
        if (handler.vars == null) {
            return null;
        }
        // In a FLAT stream, more rows of the same result set may follow a trailer
        final boolean punctuated = decoder.getSparqlOptions().getStreamType() == SparqlStreamType.PUNCTUATED;
        return new JellyRowSet(handler, reader, punctuated);
    }

    /**
     * The rows of a result set, read frame by frame as they are asked for. The same as Jena's
     * RowSetStream over an iterator, minus a layer.
     */
    private static final class JellyRowSet implements RowSet {

        private final RowCollector handler;
        private final FrameReader reader;
        // The collector moves on to the next result set once this one is read
        private final List<Var> vars;
        // Whether a trailer ends the result set (PUNCTUATED stream)
        private final boolean stopAtTrailer;
        private boolean done = false;
        private long rowNumber = 0;

        JellyRowSet(RowCollector handler, FrameReader reader, boolean stopAtTrailer) {
            this.handler = handler;
            this.reader = reader;
            this.vars = handler.vars;
            this.stopAtTrailer = stopAtTrailer;
        }

        @Override
        public boolean hasNext() {
            if (done) {
                // Do not read into the next result set
                return false;
            }
            while (!handler.hasRow()) {
                // The rows before an error are still returned, the error comes after them
                handler.checkError();
                try {
                    if ((stopAtTrailer && reader.lastFrameHadTrailer) || !reader.readFrame()) {
                        done = true;
                        return false;
                    }
                } catch (IOException e) {
                    throw new RiotException(e);
                }
            }
            return true;
        }

        @Override
        public Binding next() {
            if (!handler.hasRow() && !hasNext()) {
                throw new NoSuchElementException();
            }
            rowNumber++;
            return handler.nextRow();
        }

        @Override
        public List<Var> getResultVars() {
            return vars;
        }

        @Override
        public long getRowNumber() {
            return rowNumber;
        }

        @Override
        public void close() {}
    }

    /**
     * Reads the frames of the stream one by one, and checks at the end of the stream that it was
     * ended with a trailer, if one is required.
     */
    private static final class FrameReader {

        private final InputStream input;
        private final boolean delimited;
        // Null for a non-delimited stream
        private final DelimitedMessageReader<SparqlResultsFrame> frames;
        private final SparqlDecoder decoder;
        private final boolean requireTrailer;
        private boolean finished = false;
        private boolean lastFrameHadTrailer = false;

        FrameReader(InputStream input, boolean delimited, SparqlDecoder decoder, boolean requireTrailer) {
            this.input = input;
            this.delimited = delimited;
            this.frames = delimited ? new DelimitedMessageReader<>(input, SparqlResultsFrame.getFactory()) : null;
            this.decoder = decoder;
            this.requireTrailer = requireTrailer;
        }

        /**
         * Reads and decodes the next frame.
         *
         * @return false if the stream has ended
         */
        boolean readFrame() throws IOException {
            if (finished) {
                return false;
            }
            final SparqlResultsFrame frame;
            if (delimited) {
                frame = frames.read();
            } else {
                // Non-delimited: the entire input is a single frame
                frame = SparqlResultsFrame.parseFrom(input);
                finished = true;
            }
            if (frame == null) {
                finished = true;
            } else {
                decoder.ingestFrame(frame);
                lastFrameHadTrailer = frame.getTrailer() != null;
            }
            if (finished && requireTrailer && !lastFrameHadTrailer) {
                throw new RiotException(
                    "The Jelly-SPARQL stream ended without a trailer, so the result set may be incomplete."
                );
            }
            return frame != null;
        }
    }

    private static final class RowCollector implements SparqlResultsHandler<Node> {

        private List<Var> vars = null;
        private Var[] distinctVars = null;
        private Boolean askResult = null;
        private String error = null;
        // The frames decoded but not read yet: their columns and row counts. Bindings are made
        // from them only when they are read.
        private final ArrayDeque<Object[][]> frames = new ArrayDeque<>();
        private final ArrayDeque<Integer> frameRows = new ArrayDeque<>();
        // The frame being read, and the index of its next row
        private Object[][] current = null;
        private int currentRows = 0;
        private int next = 0;

        @Override
        public void handleTrailer(String error) {
            if (!error.isEmpty() && this.error == null) {
                this.error = error;
            }
        }

        /** Forgets the previous result set. Its rows must all be read by now. */
        void startResultSet() {
            vars = null;
            distinctVars = null;
            askResult = null;
        }

        void checkError() {
            if (error != null) {
                throw new RiotException("The producer could not complete the result set: " + error);
            }
        }

        @Override
        public void handleVariables(List<String> variables) {
            vars = variables.stream().map(Var::alloc).toList();
            distinctVars = Set.copyOf(vars).size() == vars.size() ? vars.toArray(new Var[0]) : null;
        }

        @Override
        public void handleAskResult(boolean value) {
            askResult = value;
        }

        @Override
        public Node[] createRowBuffer(int size) {
            return new Node[size];
        }

        @Override
        public void handleRow(Node[] row) {
            // Not called, as handleRows is overridden. A row is a frame of one row.
            final Object[][] columns = new Object[row.length][];
            for (int v = 0; v < row.length; v++) {
                columns[v] = new Object[] { row[v] };
            }
            handleRows(columns, 1, row);
        }

        @Override
        public void handleRows(Object[][] columns, int rowCount, Node[] row) {
            if (rowCount > 0) {
                frames.add(columns);
                frameRows.add(rowCount);
            }
        }

        /** Whether a row is decoded, but not read yet. */
        boolean hasRow() {
            while (next >= currentRows) {
                final Object[][] columns = frames.poll();
                if (columns == null) {
                    return false;
                }
                current = columns;
                currentRows = frameRows.poll();
                next = 0;
            }
            return true;
        }

        /** The next row, if {@link #hasRow()}. Straight from the columns: nothing is copied. */
        Binding nextRow() {
            final int r = next++;
            final Var[] distinct = distinctVars;
            if (distinct != null) {
                return new JellyBinding(BindingFactory.noParent, distinct, current, r);
            }
            final BindingBuilder builder = BindingFactory.builder();
            for (int v = 0; v < current.length; v++) {
                final Node node = (Node) current[v][r];
                if (node != null) {
                    builder.add(vars.get(v), node);
                }
            }
            return builder.build();
        }

        @Override
        public boolean keepsColumns() {
            // JellyBinding reads its values from the columns
            return true;
        }
    }
}
