package eu.neverblink.jelly.core.internal.proto;

public interface OptionsBase {
    boolean getGeneralizedStatements();
    boolean getRdfStar();

    int getVersion();
    int getMaxNameTableSize();
    int getMaxPrefixTableSize();
    int getMaxDatatypeTableSize();
}
