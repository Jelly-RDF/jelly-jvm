package eu.neverblink.jelly.convert.jena.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
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
 */
@ExperimentalApi
public final class RowSetReaderJelly implements RowSetReader {

    /**
     * Options for the Jelly-SPARQL reader.
     *
     * @param supportedOptions options supported by the reader
     */
    public record Options(SparqlResultsOptions supportedOptions) {
        public Options() {
            this(JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS);
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
        final Object result = readInternal(in);
        if (result instanceof Boolean askResult) {
            return new QueryExecResult(askResult);
        }
        return new QueryExecResult((RowSet) result);
    }

    @Override
    public RowSet read(InputStream in, Context context) {
        final Object result = readInternal(in);
        if (result instanceof Boolean) {
            throw new RiotException("The stream carries a boolean (ASK) result, not bindings. Use readAny to read it.");
        }
        return (RowSet) result;
    }

    /**
     * Reads the stream, returning either a RowSet (bindings) or a Boolean (ASK result).
     */
    private Object readInternal(InputStream in) {
        final RowCollector handler = new RowCollector();
        final SparqlDecoder decoder = converterFactory.decoder(handler, options.supportedOptions());
        try {
            final IoUtils.AutodetectDelimitingResponse response = IoUtils.autodetectDelimiting(in);
            if (!response.isDelimited()) {
                // Non-delimited: the entire input is a single frame
                decoder.ingestFrame(SparqlResultsFrame.parseFrom(response.newInput()));
                if (handler.askResult != null) {
                    return handler.askResult;
                }
                if (handler.vars == null) {
                    throw new RiotException("No result set header found in the input.");
                }
                return RowSetStream.create(handler.vars, handler.queue.iterator());
            }

            final InputStream input = response.newInput();
            // Read frames until the header (or an ASK result) is known;
            // the first frame must carry one of them.
            SparqlResultsFrame frame;
            while (
                handler.vars == null &&
                handler.askResult == null &&
                (frame = SparqlResultsFrame.parseDelimitedFrom(input)) != null
            ) {
                decoder.ingestFrame(frame);
            }
            if (handler.askResult != null) {
                return handler.askResult;
            }
            if (handler.vars == null) {
                throw new RiotException("No result set header found in the input.");
            }
            // Stream the rest of the frames lazily
            final Iterator<Binding> iterator = new Iterator<>() {
                @Override
                public boolean hasNext() {
                    while (handler.queue.isEmpty()) {
                        try {
                            final SparqlResultsFrame nextFrame = SparqlResultsFrame.parseDelimitedFrom(input);
                            if (nextFrame == null) {
                                return false;
                            }
                            decoder.ingestFrame(nextFrame);
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
        } catch (IOException e) {
            throw new RiotException(e);
        }
    }

    private static final class RowCollector implements SparqlResultsHandler<Node> {

        private List<Var> vars = null;
        private Boolean askResult = null;
        private final ArrayDeque<Binding> queue = new ArrayDeque<>();

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
    }
}
