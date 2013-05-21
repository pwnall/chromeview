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

package com.googlecode.eyesfree.braille.translate;

import com.googlecode.eyesfree.braille.translate.ITranslatorServiceCallback;

interface ITranslatorService {
    /**
     * Sets a callback to be called when the service is ready to translate.
     * Using any of the other methods in this interface before the
     * callback is called with a successful status will return
     * failure.
     */
    void setCallback(ITranslatorServiceCallback callback);

    /**
     * Makes sure that the given table string is valid and that the
     * table compiles.
     */
    boolean checkTable(String tableName);

    /**
     * Translates text into braille according to the give tableName.
     * Returns null on fatal translation errors.
     */
    byte[] translate(String text, String tableName);

    /**
     * Translates braille cells into text according to the given table
     * name.  Returns null on fatal translation errors.
     */
    String backTranslate(in byte[] cells, String tableName);
}
