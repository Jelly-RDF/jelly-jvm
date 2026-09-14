package eu.neverblink.jelly.core.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.utils.IoUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;

/**
 * IO utilities specific to Jelly-SPARQL.
 */
@ExperimentalApi
public final class JellySparqlIoUtils {

    private JellySparqlIoUtils() {}

    /**
     * Autodetects whether the input stream contains a single non-delimited Jelly-SPARQL frame or a delimited
     * stream of them.
     * <p>
     * This is the Jelly-SPARQL counterpart of {@link IoUtils#autodetectDelimiting(InputStream)}.
     * <p>
     * As in the RDF case, the first three bytes are peeked and then put back into the stream, so
     * the parser does not notice.
     *
     * @param inputStream the input stream
     * @return (isDelimited, newInputStream), where isDelimited is true for a delimited stream
     * @throws IOException if an I/O error occurs
     */
    public static IoUtils.AutodetectDelimitingResponse autodetectDelimiting(InputStream inputStream)
        throws IOException {
        final var scout = inputStream.readNBytes(3);
        final var scoutIn = new ByteArrayInputStream(scout);
        final var newInput = new SequenceInputStream(scoutIn, inputStream);

        // The first frame of a stream must have the options, which is field 1 of
        // SparqlResultsFrame and a message, so a non-delimited frame always starts with its tag,
        // 0x0A. A delimited stream starts with the frame's length instead.
        //
        //   non-delimited: 0A <options size> <first byte of options> ...
        //   delimited:     <frame size> 0A <options size> ...
        //
        // Truth table (notation: 0A = 0x0A, NN = not 0x0A, ?? = don't care, LO = less than 0x0A):
        // NN ?? ?? -> delimited (all non-delimited frames start with 0A)
        // 0A NN ?? -> non-delimited (a delimited frame of size 10 would have 0A here, the
        //             options tag)
        // 0A 0A LO -> delimited (the frame is 10 bytes in total, so the third byte is the size of
        //             the options, which has at most 8 bytes left to it)
        // 0A 0A ?? -> non-delimited (the options are 10 bytes, so the third byte is the tag of
        //             their first field – 0x0A, 0x48, 0x50, 0x58, or 0x78, all >= 0x0A)
        //
        // The two readings of the last two rows can never collide: a size that fits in a 10-byte
        // frame is at most 8, and every tag SparqlResultsOptions can start with is at least 0x0A.
        //
        // A long varint cannot start with 0x0A, because its continuation bit would have to be set,
        // so the first byte tells delimited from non-delimited on its own most of the time.
        final boolean isDelimited =
            scout.length == 3 && (scout[0] != 0x0A || (scout[1] == 0x0A && (scout[2] & 0xFF) < 0x0A));
        return new IoUtils.AutodetectDelimitingResponse(isDelimited, newInput);
    }
}
