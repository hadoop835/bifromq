/*
 * Copyright (c) 2023. The BifroMQ Authors. All Rights Reserved.
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

package com.baidu.bifromq.basekv.localengine.rocksdb;

import static com.baidu.bifromq.basekv.localengine.metrics.KVSpaceMeters.getTimer;
import static com.baidu.bifromq.basekv.localengine.rocksdb.Keys.toMetaKey;
import static com.google.protobuf.UnsafeByteOperations.unsafeWrap;

import com.baidu.bifromq.basekv.localengine.IKVSpaceIterator;
import com.baidu.bifromq.basekv.localengine.IKVSpaceMetadataWriter;
import com.baidu.bifromq.basekv.localengine.IKVSpaceWriter;
import com.baidu.bifromq.basekv.localengine.ISyncContext;
import com.baidu.bifromq.basekv.localengine.KVEngineException;
import com.baidu.bifromq.basekv.localengine.metrics.KVSpaceMetric;
import com.baidu.bifromq.basekv.proto.Boundary;
import com.google.protobuf.ByteString;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

class RocksDBKVSpaceWriter<
    E extends RocksDBKVEngine<E, T, C>,
    T extends RocksDBKVSpace<E, T, C>,
    C extends RocksDBKVEngineConfigurator<C>
    >
    extends RocksDBKVSpaceReader implements IKVSpaceWriter {
    private final RocksDB db;
    private final ColumnFamilyHandle cfHandle;
    private final ISyncContext syncContext;
    private final E engine;
    private final RocksDBKVSpaceWriterHelper helper;
    private final IWriteStatsRecorder.IRecorder writeStatsRecorder;
    private final MetricManager metricManager;


    RocksDBKVSpaceWriter(String id,
                         RocksDB db,
                         ColumnFamilyHandle cfHandle,
                         E engine,
                         WriteOptions writeOptions,
                         ISyncContext syncContext,
                         IWriteStatsRecorder.IRecorder writeStatsRecorder,
                         Consumer<Map<ByteString, ByteString>> afterWrite,
                         String... metricTags) {
        this(id, db, cfHandle, engine, syncContext,
            new RocksDBKVSpaceWriterHelper(db, writeOptions), writeStatsRecorder, afterWrite, metricTags);
    }

    private RocksDBKVSpaceWriter(String id,
                                 RocksDB db,
                                 ColumnFamilyHandle cfHandle,
                                 E engine,
                                 ISyncContext syncContext,
                                 RocksDBKVSpaceWriterHelper writerHelper,
                                 IWriteStatsRecorder.IRecorder writeStatsRecorder,
                                 Consumer<Map<ByteString, ByteString>> afterWrite,
                                 String... metricTags) {
        super(id, metricTags);
        this.db = db;
        this.cfHandle = cfHandle;
        this.engine = engine;
        this.syncContext = syncContext;
        this.helper = writerHelper;
        this.writeStatsRecorder = writeStatsRecorder;
        writerHelper.addMutators(syncContext.mutator());
        writerHelper.addAfterWriteCallback(cfHandle, afterWrite);
        this.metricManager = new MetricManager();
    }


    @Override
    public IKVSpaceWriter metadata(ByteString metaKey, ByteString metaValue) {
        try {
            helper.metadata(cfHandle(), metaKey, metaValue);
            return this;
        } catch (RocksDBException e) {
            throw new KVEngineException("Put in batch failed", e);
        }
    }

    @Override
    public IKVSpaceWriter insert(ByteString key, ByteString value) {
        try {
            helper.insert(cfHandle(), key, value);
            writeStatsRecorder.recordInsert();
            return this;
        } catch (RocksDBException e) {
            throw new KVEngineException("Insert in batch failed", e);
        }
    }

    @Override
    public IKVSpaceWriter put(ByteString key, ByteString value) {
        try {
            helper.put(cfHandle(), key, value);
            writeStatsRecorder.recordPut();
            return this;
        } catch (RocksDBException e) {
            throw new KVEngineException("Put in batch failed", e);
        }
    }

    @Override
    public IKVSpaceWriter delete(ByteString key) {
        try {
            helper.delete(cfHandle(), key);
            writeStatsRecorder.recordDelete();
            return this;
        } catch (RocksDBException e) {
            throw new KVEngineException("Single delete in batch failed", e);
        }
    }

    @Override
    public IKVSpaceWriter clear() {
        return clear(Boundary.getDefaultInstance());
    }

    @Override
    public IKVSpaceWriter clear(Boundary boundary) {
        try {
            helper.clear(cfHandle(), boundary);
            writeStatsRecorder.recordDeleteRange();
        } catch (Throwable e) {
            throw new KVEngineException("Delete range in batch failed", e);
        }
        return this;
    }

    @Override
    public IKVSpaceMetadataWriter migrateTo(String targetSpaceId, Boundary boundary) {
        try {
            RocksDBKVSpace<?, ?, ?> targetKVSpace = engine.createIfMissing(targetSpaceId);
            IKVSpaceWriter targetKVSpaceWriter = targetKVSpace.toWriter();
            // move data
            int c = 0;
            try (IKVSpaceIterator itr = newIterator(boundary)) {
                for (itr.seekToFirst(); itr.isValid(); itr.next()) {
                    targetKVSpaceWriter.put(itr.key(), itr.value());
                    c++;
                }
            }
            log.debug("Migrate {} kv to range[{}] from range[{}]: startKey={}, endKey={}",
                c, targetSpaceId, id, boundary.getStartKey().toStringUtf8(), boundary.getEndKey().toStringUtf8());
            // clear moved data in left range
            helper.clear(cfHandle(), boundary);
            return targetKVSpaceWriter;
        } catch (Throwable e) {
            throw new KVEngineException("Delete range in batch failed", e);
        }
    }

    @Override
    public IKVSpaceMetadataWriter migrateFrom(String fromSpaceId, Boundary boundary) {
        try {
            RocksDBKVSpace<?, ?, ?> sourceKVSpace = engine.createIfMissing(fromSpaceId);
            IKVSpaceWriter sourceKVSpaceWriter = sourceKVSpace.toWriter();
            // move data
            try (IKVSpaceIterator itr = sourceKVSpace.newIterator(boundary)) {
                for (itr.seekToFirst(); itr.isValid(); itr.next()) {
                    helper.put(cfHandle(), itr.key(), itr.value());
                }
            }
            // clear moved data in right range
            sourceKVSpaceWriter.clear(boundary);
            return sourceKVSpaceWriter;
        } catch (Throwable e) {
            throw new KVEngineException("Delete range in batch failed", e);
        }
    }

    @Override
    public void done() {
        metricManager.batchWriteCallTimer.record(() -> {
            try {
                helper.done();
                writeStatsRecorder.stop();
            } catch (RocksDBException e) {
                log.error("Write Batch commit failed", e);
                throw new KVEngineException("Batch commit failed", e);
            }
        });
    }

    @Override
    public void abort() {
        helper.abort();
    }

    @Override
    public int count() {
        return helper.count();
    }

    @Override
    protected Optional<ByteString> doMetadata(ByteString metaKey) {
        try {
            byte[] metaValBytes = db.get(cfHandle, toMetaKey(metaKey));
            return Optional.ofNullable(metaValBytes == null ? null : unsafeWrap(metaValBytes));
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected RocksDB db() {
        return db;
    }

    @Override
    protected ColumnFamilyHandle cfHandle() {
        return cfHandle;
    }

    @Override
    protected ISyncContext.IRefresher newRefresher() {
        return syncContext.refresher();
    }

    @Override
    void close() {
        // nothing to close
    }

    private class MetricManager {
        private final Timer batchWriteCallTimer;

        MetricManager() {
            Tags tags = Tags.of(metricTags);
            batchWriteCallTimer = getTimer(id, KVSpaceMetric.CallTimer, tags.and("op", "bwrite"));
        }
    }
}
