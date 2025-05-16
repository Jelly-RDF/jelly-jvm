package eu.neverblink.jelly.core.internal.proto;

import eu.neverblink.jelly.core.InternalApi;

@InternalApi
public interface OptionsBase {
    boolean getGeneralizedStatements();
    boolean getRdfStar();

    int getVersion();
    int getMaxNameTableSize();
    int getMaxPrefixTableSize();
    int getMaxDatatypeTableSize();
}
