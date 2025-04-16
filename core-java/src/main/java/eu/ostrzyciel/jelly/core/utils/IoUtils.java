package eu.ostrzyciel.jelly.core.utils;

import com.google.protobuf.CodedOutputStream;
import java.io.*;

public class IoUtils {

    private IoUtils() {}

    record AutodetectDelimitingResponse(boolean isDelimited, InputStream newInput) {}

    AutodetectDelimitingResponse autodetectDelimiting(InputStream inputStream) throws IOException {
        var scout = inputStream.readNBytes(3);
        var scoutIn = new ByteArrayInputStream(scout);
        var newInput = new SequenceInputStream(scoutIn, inputStream);

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

        var isDelimited = scout.length == 3 && (scout[0] != 0x0A || (scout[1] == 0x0A && scout[2] != 0x0A));
        return new AutodetectDelimitingResponse(isDelimited, newInput);
    }

    void writeFrameAsDelimited(byte[] nonDelimitedFrame, OutputStream output) throws IOException {
        // Don't worry, the buffer won't really have 0-size. It will be of minimal size able to fit the varint.
        var codedOutput = CodedOutputStream.newInstance(output, 0);
        codedOutput.writeUInt32NoTag(nonDelimitedFrame.length);
        codedOutput.flush();
        output.write(nonDelimitedFrame);
    }
}
