package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.RdfProtoTranscodingError;
import eu.neverblink.jelly.core.proto.v1.RdfLookupEntryPacked;
import eu.neverblink.protoc.java.runtime.MessageCollection;
import eu.neverblink.protoc.java.runtime.RepeatedString;
import java.util.Arrays;

/**
 * Maps input lookup ids to output ids for one lookup table of the transcoder.
 * Each batch is an epoch of the output lookup, so entries used by the batch are not evicted.
 */
@InternalApi
final class TranscoderLookup {

    final String tableName;
    private final EncoderLookup lookup;
    final int outputSize;

    private int inputSize = 0;
    private String[] inputValues = new String[1];
    // 0 = not mapped
    private int[] outputIds = new int[1];
    // Serial of the output entry at mapping time, to notice it was evicted since
    private int[] outputSerials = new int[1];
    // Ids mapped in the current batch are pinned, so they need no check
    private int[] batchOfId = new int[1];
    private int batch = 1;
    private int lastInputSetId = 0;

    private MessageCollection<RdfLookupEntryPacked, RdfLookupEntryPacked.Mutable> target;
    private RdfLookupEntryPacked.Mutable current = null;
    private int lastOutputSetId = 0;

    TranscoderLookup(String tableName, int outputSize) {
        this.tableName = tableName;
        this.outputSize = outputSize;
        this.lookup = new EncoderLookup(outputSize, true);
    }

    /** New input stream (or restated options): empty input table, the output keeps its entries. */
    void newInputStream(int size) {
        if (size + 1 > outputIds.length) {
            inputValues = new String[size + 1];
            outputIds = new int[size + 1];
            outputSerials = new int[size + 1];
            batchOfId = new int[size + 1];
        } else {
            Arrays.fill(inputValues, null);
            Arrays.fill(outputIds, 0);
            Arrays.fill(batchOfId, 0);
        }
        inputSize = size;
        lastInputSetId = 0;
    }

    void startBatch(MessageCollection<RdfLookupEntryPacked, RdfLookupEntryPacked.Mutable> target) {
        this.target = target;
        this.current = null;
        lookup.newEpoch();
        if (++batch == 0) {
            Arrays.fill(batchOfId, 0);
            batch = 1;
        }
    }

    void addInputEntries(Iterable<RdfLookupEntryPacked> entries) {
        for (final RdfLookupEntryPacked entry : entries) {
            final RepeatedString values = entry.getValues();
            final int count = values.size();
            if (count == 0) {
                continue;
            }
            int id = entry.getId() == 0 ? lastInputSetId + 1 : entry.getId();
            for (int i = 0; i < count; i++, id++) {
                addInputEntry(id, values.get(i));
            }
            lastInputSetId = id - 1;
        }
    }

    private void addInputEntry(int id, String value) {
        if (id < 1 || id > inputSize) {
            throw new RdfProtoTranscodingError(
                "The input stream sets %s lookup id %s, outside of its table of size %d.".formatted(
                    tableName,
                    Integer.toUnsignedString(id),
                    inputSize
                )
            );
        }
        inputValues[id] = value;
        // Evict the same entry as the input did, so output ids follow the input ids
        final int previous = outputIds[id];
        final int hint = previous != 0 && lookup.serial(previous) == outputSerials[id] ? previous : 0;
        add(id, value, hint);
    }

    int remap(int id) {
        if (id < 1 || id > inputSize) {
            throw unknownId(id);
        }
        final int out = outputIds[id];
        if (batchOfId[id] == batch) {
            return out;
        }
        if (out != 0 && lookup.serial(out) == outputSerials[id]) {
            lookup.onAccess(out);
            batchOfId[id] = batch;
            return out;
        }
        // Never mapped, or the output entry was evicted since
        final String value = inputValues[id];
        if (value == null) {
            throw unknownId(id);
        }
        return add(id, value, 0);
    }

    private RdfProtoTranscodingError unknownId(int id) {
        return new RdfProtoTranscodingError(
            "The input stream refers to %s lookup id %s, which it did not set.".formatted(
                tableName,
                Integer.toUnsignedString(id)
            )
        );
    }

    private int add(int id, String value, int evictHint) {
        final EncoderLookup.LookupEntry entry;
        try {
            entry = lookup.getOrAddEntryTranscoder(value, evictHint);
        } catch (RdfProtoSerializationError e) {
            throw new RdfProtoTranscodingError(
                "The output %s lookup table of size %d is too small for a batch of the input stream.".formatted(
                    tableName,
                    outputSize
                ),
                e
            );
        }
        final int out = entry.getId;
        outputIds[id] = out;
        outputSerials[id] = lookup.serial(out);
        batchOfId[id] = batch;
        if (entry.newEntry) {
            write(out, value);
        }
        return out;
    }

    private void write(int id, String value) {
        final boolean next = id == lastOutputSetId + 1;
        if (current == null || !next) {
            current = RdfLookupEntryPacked.newInstance().setId(next ? 0 : id);
            target.add(current);
        }
        current.addValues(value);
        lastOutputSetId = id;
    }
}
