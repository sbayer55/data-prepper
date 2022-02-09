/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression.util;

import org.junit.jupiter.api.BeforeEach;

class TestListenerTest {

    private TestListener listener;

    @BeforeEach
    void beforEach() {
        listener = new TestListener();
    }
}