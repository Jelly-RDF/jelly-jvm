package eu.neverblink.jelly.convert.rdf4j.rio;

import org.eclipse.rdf4j.rio.helpers.AbstractRioSetting;
import org.eclipse.rdf4j.rio.helpers.RioConfigurationException;

/**
 * Backport of RDF4J's 5 JellyIntegerRioSetting for use with RDF4J 4.x.
 * <p>
 * Original source:
 * https://github.com/eclipse-rdf4j/rdf4j/blob/f6870acabd42d55ef48974ee5acd7d5607d1451a/core/rio/api/src/main/java/org/eclipse/rdf4j/rio/helpers/JellyIntegerRioSetting.java
 * <p>
 * Original copyright notice:
 * <p>
 * Copyright (c) 2024 Eclipse RDF4J contributors.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 * <p>
 * SPDX-License-Identifier: BSD-3-Clause
 */
public class JellyIntegerRioSetting extends AbstractRioSetting<Integer> {

    public JellyIntegerRioSetting(String key, String description, Integer defaultValue) {
        super(key, description, defaultValue);
    }

    @Override
    public Integer convert(String stringValue) {
        try {
            return Integer.parseInt(stringValue);
        } catch (NumberFormatException e) {
            throw new RioConfigurationException("Conversion error for setting: " + getKey(), e);
        }
    }
}
