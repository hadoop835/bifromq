/*
 * Copyright (c) 2023. Baidu, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.baidu.bifromq.basekv.store.range;

import com.baidu.bifromq.basekv.localengine.IKVSpaceWriter;
import com.baidu.bifromq.basekv.localengine.proto.KeyBoundary;
import com.baidu.bifromq.basekv.proto.KVRangeId;
import com.baidu.bifromq.basekv.store.api.IKVReader;
import com.baidu.bifromq.basekv.store.api.IKVWriter;
import com.baidu.bifromq.basekv.utils.KVRangeIdUtil;

public class KVRangeWriter extends AbstractKVRangeMetadataUpdatable<KVRangeWriter>
    implements IKVRangeWriter<KVRangeWriter> {
    private final IKVSpaceWriter keyRangeWriter;
    private final ILoadTracker loadTracker;

    public KVRangeWriter(IKVSpaceWriter rangeWriter, ILoadTracker loadTracker) {
        super(rangeWriter);
        this.keyRangeWriter = rangeWriter;
        this.loadTracker = loadTracker;
    }

    @Override
    protected IKVSpaceWriter keyRangeWriter() {
        return keyRangeWriter;
    }

    @Override
    public IKVRangeMetadataUpdatable<?> migrateTo(KVRangeId targetRangeId, KeyBoundary boundary) {
        return new KVRangeMetadataUpdatable(keyRangeWriter.migrateTo(KVRangeIdUtil.toString(targetRangeId), boundary));
    }

    @Override
    public IKVRangeMetadataUpdatable<?> migrateFrom(KVRangeId fromRangeId, KeyBoundary boundary) {
        return new KVRangeMetadataUpdatable(keyRangeWriter.migrateFrom(KVRangeIdUtil.toString(fromRangeId), boundary));
    }

    @Override
    public IKVWriter kvWriter() {
        return new KVWriter(keyRangeWriter, loadTracker);
    }

    @Override
    public IKVReader newDataReader() {
        return new KVReader(keyRangeWriter, this, loadTracker);
    }

    @Override
    public void abort() {
        keyRangeWriter().abort();
    }

    @Override
    public int count() {
        return keyRangeWriter().count();
    }

    @Override
    public void done() {
        keyRangeWriter().done();
    }
}
