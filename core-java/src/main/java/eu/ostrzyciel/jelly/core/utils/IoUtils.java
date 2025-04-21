package eu.ostrzyciel.jelly.core.utils;

import com.google.protobuf.CodedOutputStream;
import java.io.*;

public class IoUtils {

    private IoUtils() {}

    public record AutodetectDelimitingResponse(boolean isDelimited, InputStream newInput) {}

    /**
     * Autodetects whether the input stream is a non-delimited Jelly file or a delimited Jelly file.
     * <p>
     * To do this, the first three bytes in the stream are peeked.
     * These bytes are then put back into the stream, and the stream is returned, so the parser won't notice the peeking.
     * @param inputStream the input stream
     * @return (isDelimited, newInputStream) where isDelimited is true if the stream is a delimited Jelly file
     */
    public static AutodetectDelimitingResponse autodetectDelimiting(InputStream inputStream) throws IOException {
        final var scout = inputStream.readNBytes(3);
        final var scoutIn = new ByteArrayInputStream(scout);
        final var newInput = new SequenceInputStream(scoutIn, inputStream);

        // Truth table (notation: 0A = 0x0A, NN = not 0x0A, ?? = don't care):
        // NN ?? ?? -> delimited (all non-delimited start with 0A)
        // 0A NN ?? -> non-delimited
        // 0A 0A NN -> delimited (total message size = 10)
        // 0A 0A 0A -> non-delimited (stream options size = 10)

        // A case like "0A 0A 0A 0A" in the delimited variant is impossible. It would mean that the whole message
        // is 10 bytes long, while stream options alone are 10 bytes long.

        // It's not possible to have a long varint starting with 0A, because its most significant bit
        // would have to be 1 (continuation bit). So, we don't need to worry about that case.

        // Yeah, it's magic. But it works.

        final var isDelimited = scout.length == 3 && (scout[0] != 0x0A || (scout[1] == 0x0A && scout[2] != 0x0A));
        return new AutodetectDelimitingResponse(isDelimited, newInput);
    }

    /**
     * Utility method to transform a non-delimited Jelly frame (as a byte array) into a delimited one,
     * writing it to a byte stream.
     * <p>
     * This is useful if you for example store non-delimited frames in a database, but want to write them to a stream.
     *
     * @param nonDelimitedFrame EXACTLY one non-delimited Jelly frame
     * @param output the output stream to write the frame to
     */
    public static void writeFrameAsDelimited(byte[] nonDelimitedFrame, OutputStream output) throws IOException {
        // Don't worry, the buffer won't really have 0-size. It will be of minimal size able to fit the varint.
        final var codedOutput = CodedOutputStream.newInstance(output, 0);
        codedOutput.writeUInt32NoTag(nonDelimitedFrame.length);
        codedOutput.flush();
        output.write(nonDelimitedFrame);
    }
}
