package eu.neverblink.jelly.core.patch.internal;

import static eu.neverblink.jelly.core.internal.BaseJellyOptions.*;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.internal.DecoderBase;
import eu.neverblink.jelly.core.patch.JellyPatchOptions;
import eu.neverblink.jelly.core.patch.PatchDecoder;
import eu.neverblink.jelly.core.patch.PatchHandler;
import eu.neverblink.jelly.core.proto.v1.*;
import eu.neverblink.jelly.core.proto.v1.RdfQuad;
import eu.neverblink.jelly.core.proto.v1.patch.*;

@ExperimentalApi
@InternalApi
public abstract sealed class PatchDecoderImpl<TNode, TDatatype>
    extends DecoderBase<TNode, TDatatype>
    implements PatchDecoder
{

    protected final PatchHandler<TNode> patchHandler;
    protected final RdfPatchOptions supportedOptions;

    private RdfPatchOptions currentOptions;
    private boolean isFrameStreamType = false;
    private boolean isPunctuatedStreamType = false;

    protected PatchDecoderImpl(
        ProtoDecoderConverter<TNode, TDatatype> converter,
        PatchHandler<TNode> patchHandler,
        RdfPatchOptions supportedOptions
    ) {
        super(converter);
        this.patchHandler = patchHandler;
        this.supportedOptions =
            supportedOptions != null ? supportedOptions : JellyPatchOptions.DEFAULT_SUPPORTED_OPTIONS;
    }

    @Override
    protected int getNameTableSize() {
        if (currentOptions == null) {
            return SMALL_NAME_TABLE_SIZE;
        }

        return currentOptions.getMaxNameTableSize();
    }

    @Override
    protected int getPrefixTableSize() {
        if (currentOptions == null) {
            return SMALL_PREFIX_TABLE_SIZE;
        }

        return currentOptions.getMaxPrefixTableSize();
    }

    @Override
    protected int getDatatypeTableSize() {
        if (currentOptions == null) {
            return SMALL_DT_TABLE_SIZE;
        }

        return currentOptions.getMaxDatatypeTableSize();
    }

    @Override
    public RdfPatchOptions getPatchOptions() {
        return currentOptions;
    }

    @Override
    public void ingestRow(RdfPatchRow row) {
        switch (row.getRowFieldNumber()) {
            case RdfPatchRow.OPTIONS -> handleOptions(row.getOptions());
            case RdfPatchRow.STATEMENT_ADD -> handleStatementAdd(row.getStatementAdd());
            case RdfPatchRow.STATEMENT_DELETE -> handleStatementDelete(row.getStatementDelete());
            case RdfPatchRow.NAMESPACE_ADD -> handleNamespaceAdd(row.getNamespaceAdd());
            case RdfPatchRow.NAMESPACE_DELETE -> handleNamespaceDelete(row.getNamespaceDelete());
            case RdfPatchRow.TRANSACTION_START -> handleTransactionStart();
            case RdfPatchRow.TRANSACTION_COMMIT -> handleTransactionCommit();
            case RdfPatchRow.TRANSACTION_ABORT -> handleTransactionAbort();
            case RdfPatchRow.NAME -> handleName(row.getName());
            case RdfPatchRow.PREFIX -> handlePrefix(row.getPrefix());
            case RdfPatchRow.DATATYPE -> handleDatatype(row.getDatatype());
            case RdfPatchRow.HEADER -> handleHeader(row.getHeader());
            case RdfPatchRow.PUNCTUATION -> handlePunctuation();
            default -> throw new RdfProtoDeserializationError(
                "Row kind is not set or unknown: " + row.getRowFieldNumber()
            );
        }
    }

    @Override
    public void ingestFrame(RdfPatchFrame frame) {
        for (final var row : frame.getRows()) {
            ingestRow(row);
        }

        if (isFrameStreamType) {
            patchHandler.punctuation();
        }
    }

    protected abstract void handleStatementAdd(RdfQuad statement);

    protected abstract void handleStatementDelete(RdfQuad statement);

    protected void handleOptions(RdfPatchOptions opt) {
        JellyPatchOptions.checkCompatibility(opt, supportedOptions);
        setPatchOptions(opt);
    }

    private void handleNamespaceAdd(RdfPatchNamespace nsRow) {
        final var valueIri = nsRow.getValue();
        patchHandler.addNamespace(
            nsRow.getName(),
            // The value is required for the namespace add operation
            getNameDecoder().decode(valueIri.getPrefixId(), valueIri.getNameId()),
            convertGraphTermWrapped(nsRow)
        );
    }

    private void handleNamespaceDelete(RdfPatchNamespace nsRow) {
        final var valueIri = nsRow.getValue();
        patchHandler.deleteNamespace(
            nsRow.getName(),
            // The value is not required for the namespace delete operation, null is fine
            decodeNsIri(valueIri),
            convertGraphTermWrapped(nsRow)
        );
    }

    private void handleTransactionStart() {
        patchHandler.transactionStart();
    }

    private void handleTransactionCommit() {
        patchHandler.transactionCommit();
    }

    private void handleTransactionAbort() {
        patchHandler.transactionAbort();
    }

    private void handleName(RdfNameEntry name) {
        getNameDecoder().updateNames(name);
    }

    private void handlePrefix(RdfPrefixEntry prefix) {
        getNameDecoder().updatePrefixes(prefix);
    }

    private void handleDatatype(RdfDatatypeEntry dtRow) {
        getDatatypeLookup().update(dtRow.getId(), converter.makeDatatype(dtRow.getValue()));
    }

    private void handleHeader(RdfPatchHeader hRow) {
        // No support for repeated terms in the header
        patchHandler.header(hRow.getKey(), convertTerm(hRow.getValue()));
    }

    private void handlePunctuation() {
        if (!isPunctuatedStreamType) {
            throw new RdfProtoDeserializationError("Unexpected punctuation row in non-punctuated stream.");
        }

        patchHandler.punctuation();
    }

    private void setPatchOptions(RdfPatchOptions options) {
        if (currentOptions != null) {
            return;
        }

        this.currentOptions = options;
        this.isFrameStreamType = options.getStreamType() == PatchStreamType.FRAME;
        this.isPunctuatedStreamType = options.getStreamType() == PatchStreamType.PUNCTUATED;
    }

    private TNode decodeNsIri(RdfIri iri) {
        if (iri == null) {
            return null;
        }

        return getNameDecoder().decode(iri.getPrefixId(), iri.getNameId());
    }

    @ExperimentalApi
    public static final class TriplesDecoder<TNode, TDatatype> extends PatchDecoderImpl<TNode, TDatatype> {

        private final PatchHandler.TriplePatchHandler<TNode> patchHandler;

        public TriplesDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            PatchHandler.TriplePatchHandler<TNode> patchHandler,
            RdfPatchOptions supportedOptions
        ) {
            super(converter, patchHandler, supportedOptions);
            this.patchHandler = patchHandler;
        }

        @Override
        protected void handleOptions(RdfPatchOptions opt) {
            if (opt.getStatementType() != PatchStatementType.TRIPLES) {
                throw new RdfProtoDeserializationError(
                    "Incoming stream with statement type %s cannot be decoded by this decoder. Only TRIPLES streams are accepted.".formatted(
                        opt.getStatementType()
                    )
                );
            }
            super.handleOptions(opt);
        }

        @Override
        protected void handleStatementAdd(RdfQuad statement) {
            patchHandler.addTriple(
                convertSubjectTermWrapped(statement),
                convertPredicateTermWrapped(statement),
                convertObjectTermWrapped(statement)
            );
        }

        @Override
        protected void handleStatementDelete(RdfQuad statement) {
            patchHandler.deleteTriple(
                convertSubjectTermWrapped(statement),
                convertPredicateTermWrapped(statement),
                convertObjectTermWrapped(statement)
            );
        }
    }

    @ExperimentalApi
    public static final class QuadsDecoder<TNode, TDatatype> extends PatchDecoderImpl<TNode, TDatatype> {

        private final PatchHandler.QuadPatchHandler<TNode> patchHandler;

        public QuadsDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            PatchHandler.QuadPatchHandler<TNode> patchHandler,
            RdfPatchOptions supportedOptions
        ) {
            super(converter, patchHandler, supportedOptions);
            this.patchHandler = patchHandler;
        }

        @Override
        protected void handleOptions(RdfPatchOptions opt) {
            if (opt.getStatementType() != PatchStatementType.QUADS) {
                throw new RdfProtoDeserializationError(
                    "Incoming stream with statement type %s cannot be decoded by this decoder. Only QUADS streams are accepted.".formatted(
                        opt.getStatementType()
                    )
                );
            }
            super.handleOptions(opt);
        }

        @Override
        protected void handleStatementAdd(RdfQuad statement) {
            patchHandler.addQuad(
                convertSubjectTermWrapped(statement),
                convertPredicateTermWrapped(statement),
                convertObjectTermWrapped(statement),
                convertGraphTermWrapped(statement)
            );
        }

        @Override
        protected void handleStatementDelete(RdfQuad statement) {
            patchHandler.deleteQuad(
                convertSubjectTermWrapped(statement),
                convertPredicateTermWrapped(statement),
                convertObjectTermWrapped(statement),
                convertGraphTermWrapped(statement)
            );
        }
    }

    @ExperimentalApi
    public static final class AnyStatementDecoder<TNode, TDatatype> extends PatchDecoderImpl<TNode, TDatatype> {

        private final PatchHandler.AnyPatchHandler<TNode> patchHandler;
        private PatchStatementType statementType = null;

        public AnyStatementDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            PatchHandler.AnyPatchHandler<TNode> patchHandler,
            RdfPatchOptions supportedOptions
        ) {
            super(converter, patchHandler, supportedOptions);
            this.patchHandler = patchHandler;
        }

        @Override
        protected void handleOptions(RdfPatchOptions opt) {
            if (opt.getStatementType() == PatchStatementType.UNSPECIFIED) {
                throw new RdfProtoDeserializationError("Incoming stream has no statement type set. Cannot decode.");
            }
            if (opt.getStatementType() == null) {
                throw new RdfProtoDeserializationError(
                    "Incoming stream has an unrecognized statement type cannot be decoded by this decoder. " +
                        "Only TRIPLES and QUADS streams are accepted."
                );
            }

            this.statementType = opt.getStatementType();
            super.handleOptions(opt);
        }

        @Override
        protected void handleStatementAdd(RdfQuad statement) {
            final var s = convertSubjectTermWrapped(statement);
            final var p = convertPredicateTermWrapped(statement);
            final var o = convertObjectTermWrapped(statement);

            if (statementType == null) {
                throw new RdfProtoDeserializationError(
                    "Statement type is not set, statement add command cannot be decoded."
                );
            }
            switch (statementType) {
                case TRIPLES -> patchHandler.addTriple(s, p, o);
                case QUADS -> {
                    final var g = convertGraphTermWrapped(statement);
                    patchHandler.addQuad(s, p, o, g);
                }
            }
        }

        @Override
        protected void handleStatementDelete(RdfQuad statement) {
            final var s = convertSubjectTermWrapped(statement);
            final var p = convertPredicateTermWrapped(statement);
            final var o = convertObjectTermWrapped(statement);

            if (statementType == null) {
                throw new RdfProtoDeserializationError(
                    "Statement type is not set, statement delete command cannot be decoded."
                );
            }
            switch (statementType) {
                case TRIPLES -> patchHandler.deleteTriple(s, p, o);
                case QUADS -> {
                    final var g = convertGraphTermWrapped(statement);
                    patchHandler.deleteQuad(s, p, o, g);
                }
            }
        }
    }
}
