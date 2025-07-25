package eu.neverblink.jelly.convert.jena.riot;

import static eu.neverblink.jelly.core.utils.IoUtils.readStream;

import eu.neverblink.jelly.convert.jena.JenaConverterFactory;
import eu.neverblink.jelly.core.JellyOptions;
import eu.neverblink.jelly.core.RdfHandler;
import eu.neverblink.jelly.core.memory.RowBuffer;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.utils.IoUtils;
import eu.neverblink.protoc.java.runtime.MessageFactory;
import eu.neverblink.protoc.java.runtime.ProtoMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import org.apache.jena.atlas.web.ContentType;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.ReaderRIOT;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.util.Context;

public final class JellyReader implements ReaderRIOT {

    private final JenaConverterFactory converterFactory;

    public JellyReader(JenaConverterFactory converterFactory) {
        this.converterFactory = converterFactory;
    }

    /**
     * Reads Jelly RDF data from an InputStream.
     * Automatically detects whether the input is a single frame (non-delimited) or a stream of frames (delimited).
     */
    @Override
    public void read(InputStream in, String baseURI, ContentType ct, StreamRDF output, Context context) {
        final RdfStreamOptions supportedOptions = context.get(
            JellyLanguage.SYMBOL_SUPPORTED_OPTIONS,
            JellyOptions.DEFAULT_SUPPORTED_OPTIONS
        );

        final var handler = new RdfHandler.AnyStatementHandler<Node>() {
            @Override
            public void handleNamespace(String prefix, Node namespace) {
                output.prefix(prefix, namespace.getURI());
            }

            @Override
            public void handleTriple(Node subject, Node predicate, Node object) {
                output.triple(converterFactory.decoderConverter().makeTriple(subject, predicate, object));
            }

            @Override
            public void handleQuad(Node subject, Node predicate, Node object, Node graph) {
                output.quad(converterFactory.decoderConverter().makeQuad(subject, predicate, object, graph));
            }
        };

        final var decoder = converterFactory.anyStatementDecoder(handler, supportedOptions);
        // Single row buffer -- rows are passed to the decoder immediately after being read
        final RowBuffer buffer = RowBuffer.newSingle(decoder::ingestRow);
        final RdfStreamFrame.Mutable reusableFrame = RdfStreamFrame.newInstance().setRows(buffer);
        final MessageFactory<RdfStreamFrame> getReusableFrame = () -> reusableFrame;

        output.start();
        try {
            final var delimitingResponse = IoUtils.autodetectDelimiting(in);
            if (delimitingResponse.isDelimited()) {
                // Delimited Jelly file
                // In this case, we can read multiple frames
                readStream(delimitingResponse.newInput(), getReusableFrame, frame -> buffer.clear());
            } else {
                // Non-delimited Jelly file
                // In this case, we can only read one frame
                ProtoMessage.parseFrom(delimitingResponse.newInput(), getReusableFrame);
                buffer.clear();
            }
        } catch (IOException e) {
            throw new RiotException(e);
        } finally {
            output.finish();
        }
    }

    @Override
    public void read(Reader reader, String baseURI, ContentType ct, StreamRDF output, Context context) {
        throw new RiotException(
            "RDF Jelly: Reading binary data from a java.io.Reader is not supported. Please use an InputStream."
        );
    }
}
