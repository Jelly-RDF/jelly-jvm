package eu.neverblink.jelly.convert.jena.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
import eu.neverblink.jelly.core.sparql.JellySparqlIoUtils;
import eu.neverblink.jelly.core.sparql.JellySparqlOptions;
import eu.neverblink.jelly.core.sparql.SparqlDecoder;
import eu.neverblink.jelly.core.sparql.SparqlResultsHandler;
import eu.neverblink.jelly.core.utils.IoUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
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
import org.apache.jena.sparql.exec.RowSetStream;
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
 */
@ExperimentalApi
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
     * Reads the stream, returning either a RowSet (bindings) or a Boolean (ASK result).
     */
    private Object readInternal(InputStream in, Options options) {
        final RowCollector handler = new RowCollector();
        final SparqlDecoder decoder = converterFactory.decoder(
            handler,
            options.supportedOptions(),
            options.maxRowsPerFrame()
        );
        final FrameReader reader;
        try {
            final IoUtils.AutodetectDelimitingResponse response = JellySparqlIoUtils.autodetectDelimiting(in);
            reader = new FrameReader(response.newInput(), response.isDelimited(), decoder, options.requireTrailer());
            // Read frames until the header (or an ASK result) is known.
            while (handler.vars == null && handler.askResult == null && reader.readFrame()) {
                // Errors are reported by the iterator, after the rows received before them
            }
            if (handler.askResult != null) {
                // Read to the end of the stream: the decoder rejects any frame after this one
                do {
                    handler.checkError();
                } while (reader.readFrame());
                return handler.askResult;
            }
        } catch (IOException e) {
            throw new RiotException(e);
        }
        if (handler.vars == null) {
            throw new RiotException("No result set header found in the input.");
        }
        // Stream the rest of the frames lazily
        final Iterator<Binding> iterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                while (handler.queue.isEmpty()) {
                    // The rows before an error are still returned, the error comes after them
                    handler.checkError();
                    try {
                        if (!reader.readFrame()) {
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
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return handler.queue.poll();
            }
        };
        return RowSetStream.create(handler.vars, iterator);
    }

    /**
     * Reads the frames of the stream one by one, and checks at the end of the stream that it was
     * ended with a trailer, if one is required.
     */
    private static final class FrameReader {

        private final InputStream input;
        private final boolean delimited;
        private final SparqlDecoder decoder;
        private final boolean requireTrailer;
        private boolean finished = false;
        private boolean lastFrameHadTrailer = false;

        FrameReader(InputStream input, boolean delimited, SparqlDecoder decoder, boolean requireTrailer) {
            this.input = input;
            this.delimited = delimited;
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
                frame = SparqlResultsFrame.parseDelimitedFrom(input);
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
        private Boolean askResult = null;
        private String error = null;
        private final ArrayDeque<Binding> queue = new ArrayDeque<>();

        @Override
        public void handleTrailer(String error) {
            if (!error.isEmpty() && this.error == null) {
                this.error = error;
            }
        }

        void checkError() {
            if (error != null) {
                throw new RiotException("The producer could not complete the result set: " + error);
            }
        }

        @Override
        public void handleVariables(List<String> variables) {
            vars = variables.stream().map(Var::alloc).toList();
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
            final BindingBuilder builder = BindingFactory.builder();
            for (int i = 0; i < row.length; i++) {
                if (row[i] != null) {
                    builder.add(vars.get(i), row[i]);
                }
            }
            queue.add(builder.build());
        }

        @Override
        public void handleRows(Object[][] columns, int rowCount, Node[] row) {
            // Straight from the columns, without copying each row into the buffer first
            final int width = columns.length;
            for (int r = 0; r < rowCount; r++) {
                final BindingBuilder builder = BindingFactory.builder();
                for (int v = 0; v < width; v++) {
                    final Node node = (Node) columns[v][r];
                    if (node != null) {
                        builder.add(vars.get(v), node);
                    }
                }
                queue.add(builder.build());
            }
        }
    }
}
