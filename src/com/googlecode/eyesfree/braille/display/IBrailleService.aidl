/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.eyesfree.braille.display;

import com.googlecode.eyesfree.braille.display.IBrailleServiceCallback;

/**
 * Interface for clients to talk to the braille display service.
 */
interface IBrailleService {
    /**
     * Register a callback for the {@code callingApp} which will receive
     * certain braille display related events.
     */
    boolean registerCallback(in IBrailleServiceCallback callback);

    /**
     * Unregister a previously registered callback for the {@code callingApp}.
     */
    oneway void unregisterCallback(in IBrailleServiceCallback callback);

    /**
     * Updates the main cells of the connected braille display
     * with a given dot {@code pattern}.
     *
     * @return {@code true} on success and {@code false} otherwise.
     */
    void displayDots(in byte[] patterns);
}
