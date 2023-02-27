/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.ql.txn.compactor.handler;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.common.ValidReadTxnList;
import org.apache.hadoop.hive.common.ValidReaderWriteIdList;
import org.apache.hadoop.hive.common.ValidTxnList;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.api.*;
import org.apache.hadoop.hive.metastore.metrics.AcidMetricService;
import org.apache.hadoop.hive.metastore.metrics.MetricsConstants;
import org.apache.hadoop.hive.metastore.metrics.PerfLogger;
import org.apache.hadoop.hive.metastore.txn.CompactionInfo;
import org.apache.hadoop.hive.metastore.txn.TxnCommonUtils;
import org.apache.hadoop.hive.metastore.txn.TxnStore;
import org.apache.hadoop.hive.metastore.txn.TxnUtils;
import org.apache.hadoop.hive.metastore.utils.MetaStoreUtils;
import org.apache.hadoop.hive.ql.io.AcidDirectory;
import org.apache.hadoop.hive.ql.io.AcidUtils;
import org.apache.hadoop.hive.ql.txn.compactor.CleanupRequest.CleanupRequestBuilder;
import org.apache.hadoop.hive.ql.txn.compactor.CompactorUtil;
import org.apache.hadoop.hive.ql.txn.compactor.CompactorUtil.ThrowingRunnable;
import org.apache.hadoop.hive.ql.txn.compactor.FSRemover;
import org.apache.hadoop.hive.ql.txn.compactor.MetadataCache;
import org.apache.hive.common.util.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.ListUtils.subtract;

public class AbortCleanHandler extends TaskHandler {

  private static final Logger LOG = LoggerFactory.getLogger(AbortCleanHandler.class.getName());

  public AbortCleanHandler(HiveConf conf, TxnStore txnHandler,
                           MetadataCache metadataCache, boolean metricsEnabled,
                           FSRemover fsRemover) {
    super(conf, txnHandler, metadataCache, metricsEnabled, fsRemover);
  }

  /**
   The following cleanup is based on the following ideas - <br>
   1. Aborted cleanup is independent of compaction. This is because directories which are written by
      aborted txns are not visible by any open txns. It is only visible while determining the AcidState (which
      only sees the aborted deltas and does not read the file).<br>
   2. There is no limiting factors for aborted directories cleanup (unlike min open txn ID of a table
      in the case of compaction). As long as the associated aborted directories and its associated metadata in
      TXN_COMPONENTS is removed, we should be OK.<br><br>
   The following algorithm is used to clean the set of aborted directories - <br>
      a. Find the list of entries which are suitable for cleanup (This is done in {@link TxnStore#findReadyToCleanForAborts(long, long, int)}).<br>
      b. If the table/partition does not exist, then remove the associated aborted entry in TXN_COMPONENTS table. <br>
      c. Get the AcidState of the table by using the min open txnID, database name, tableName, partition name, highest write ID <br>
      d. Fetch the aborted directories and delete the directories. <br>
      e. Fetch the aborted write IDs from the AcidState and use it to delete the associated metadata in the TXN_COMPONENTS table.
   **/
  @Override
  public List<Runnable> getTasks() throws MetaException {
    long minOpenTxnId = txnHandler.findMinOpenTxnIdForCleaner();
    int abortedThreshold = HiveConf.getIntVar(conf,
              HiveConf.ConfVars.HIVE_COMPACTOR_ABORTEDTXN_THRESHOLD);
    long abortedTimeThreshold = HiveConf
              .getTimeVar(conf, HiveConf.ConfVars.HIVE_COMPACTOR_ABORTEDTXN_TIME_THRESHOLD,
                      TimeUnit.MILLISECONDS);
    List<CompactionInfo> readyToCleanAborts = txnHandler.findReadyToCleanForAborts(minOpenTxnId,
            abortedTimeThreshold, abortedThreshold);

    if (!readyToCleanAborts.isEmpty()) {
      long minTxnIdSeenOpen = txnHandler.findMinTxnIdSeenOpen();
      final long cleanerWaterMark =
              minTxnIdSeenOpen < 0 ? minOpenTxnId : Math.min(minOpenTxnId, minTxnIdSeenOpen);
      return readyToCleanAborts.stream().map(ci -> ThrowingRunnable.unchecked(() -> clean(ci, cleanerWaterMark, metricsEnabled)))
              .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  private void clean(CompactionInfo ci, long minOpenTxn, boolean metricsEnabled) throws MetaException {
    LOG.info("Starting cleaning for {}", ci);
    PerfLogger perfLogger = PerfLogger.getPerfLogger(false);
    String cleanerMetric = MetricsConstants.COMPACTION_CLEANER_CYCLE + "_";
    try {
      if (metricsEnabled) {
        perfLogger.perfLogBegin(AbortCleanHandler.class.getName(), cleanerMetric);
      }
      Table t;
      Partition p = null;
      t = metadataCache.computeIfAbsent(ci.getFullTableName(), () -> resolveTable(ci.dbname, ci.tableName));
      if (isNull(t)) {
        // The table was dropped before we got around to cleaning it.
        LOG.info("Unable to find table {}, assuming it was dropped.", ci.getFullTableName());
        txnHandler.markCleanedForAborts(ci);
        return;
      }
      if (MetaStoreUtils.isNoCleanUpSet(t.getParameters())) {
        // The table was marked no clean up true.
        LOG.info("Skipping table {} clean up, as NO_CLEANUP set to true", ci.getFullTableName());
        return;
      }
      if (!isNull(ci.partName)) {
        p = resolvePartition(ci.dbname, ci.tableName, ci.partName);
        if (isNull(p)) {
          // The partition was dropped before we got around to cleaning it.
          LOG.info("Unable to find partition {}, assuming it was dropped.",
                  ci.getFullPartitionName());
          txnHandler.markCleanedForAborts(ci);
          return;
        }
        if (MetaStoreUtils.isNoCleanUpSet(p.getParameters())) {
          // The partition was marked no clean up true.
          LOG.info("Skipping partition {} clean up, as NO_CLEANUP set to true", ci.getFullPartitionName());
          return;
        }
      }

      String location = CompactorUtil.resolveStorageDescriptor(t,p).getLocation();
      abortCleanUsingAcidDir(ci, location, minOpenTxn);

    } catch (Exception e) {
      LOG.error("Caught exception when cleaning, unable to complete cleaning of {} due to {}", ci,
              e.getMessage());

    } finally {
      if (metricsEnabled) {
        perfLogger.perfLogEnd(AbortCleanHandler.class.getName(), cleanerMetric);
      }
    }
  }

  private void abortCleanUsingAcidDir(CompactionInfo ci, String location, long minOpenTxn) throws Exception {
    ValidTxnList validTxnList =
            TxnUtils.createValidTxnListForCleaner(txnHandler.getOpenTxns(), minOpenTxn);
    //save it so that getAcidState() sees it
    conf.set(ValidTxnList.VALID_TXNS_KEY, validTxnList.writeToString());

    ValidReaderWriteIdList validWriteIdList = getValidCleanerWriteIdList(ci, validTxnList);
    LOG.debug("Cleaning based on writeIdList: {}", validWriteIdList);

    Path path = new Path(location);
    FileSystem fs = path.getFileSystem(conf);

    // Collect all the files/dirs
    Map<Path, AcidUtils.HdfsDirSnapshot> dirSnapshots = AcidUtils.getHdfsDirSnapshotsForCleaner(fs, path);
    AcidDirectory dir = AcidUtils.getAcidState(fs, path, conf, validWriteIdList, Ref.from(false), false,
            dirSnapshots);
    Table table = metadataCache.computeIfAbsent(ci.getFullTableName(), () -> resolveTable(ci.dbname, ci.tableName));
    boolean isDynPartAbort = CompactorUtil.isDynPartAbort(table, ci.partName);

    List<Path> obsoleteDirs = CompactorUtil.getObsoleteDirs(dir, isDynPartAbort);
    if (isDynPartAbort || dir.hasUncompactedAborts()) {
      ci.setWriteIds(dir.hasUncompactedAborts(), dir.getAbortedWriteIds());
    }

    List<Path> deleted = fsRemover.clean(new CleanupRequestBuilder().setLocation(location)
            .setDbName(ci.dbname).setFullPartitionName(ci.getFullPartitionName())
            .setRunAs(ci.runAs).setObsoleteDirs(obsoleteDirs).setPurge(true)
            .build());

    if (!deleted.isEmpty()) {
      AcidMetricService.updateMetricsFromCleaner(ci.dbname, ci.tableName, ci.partName, dir.getObsolete(), conf,
              txnHandler);
    }

    // Make sure there are no leftovers below the compacted watermark
    boolean success = false;
    conf.set(ValidTxnList.VALID_TXNS_KEY, new ValidReadTxnList().toString());
    dir = AcidUtils.getAcidState(fs, path, conf, new ValidReaderWriteIdList(
                    ci.getFullTableName(), new long[0], new BitSet(), ci.highestWriteId, Long.MAX_VALUE),
            Ref.from(false), false, dirSnapshots);

    List<Path> remained = subtract(CompactorUtil.getObsoleteDirs(dir, isDynPartAbort), deleted);
    if (!remained.isEmpty()) {
      LOG.warn("Remained {} obsolete directories from {}. {}",
              remained.size(), location, CompactorUtil.getDebugInfo(remained));
    } else {
      LOG.debug("All cleared below the watermark: {} from {}", ci.highestWriteId, location);
      success = true;
    }
    if (success || CompactorUtil.isDynPartAbort(table, ci.partName)) {
      txnHandler.markCleanedForAborts(ci);
    } else {
      LOG.warn("Leaving aborted entry {} in TXN_COMPONENTS table.", ci);
    }

  }

  private ValidReaderWriteIdList getValidCleanerWriteIdList(CompactionInfo ci, ValidTxnList validTxnList)
          throws NoSuchTxnException, MetaException {
    List<String> tblNames = Collections.singletonList(AcidUtils.getFullTableName(ci.dbname, ci.tableName));
    GetValidWriteIdsRequest request = new GetValidWriteIdsRequest(tblNames);
    request.setValidTxnList(validTxnList.writeToString());
    GetValidWriteIdsResponse rsp = txnHandler.getValidWriteIds(request);
    // we could have no write IDs for a table if it was never written to but
    // since we are in the Cleaner phase of compactions, there must have
    // been some delta/base dirs
    assert rsp != null && rsp.getTblValidWriteIdsSize() == 1;
    ValidReaderWriteIdList validWriteIdList =
            TxnCommonUtils.createValidReaderWriteIdList(rsp.getTblValidWriteIds().get(0));
    /*
     * We need to filter the obsoletes dir list, to only remove directories that were made obsolete by this compaction
     * If we have a higher retentionTime it is possible for a second compaction to run on the same partition. Cleaning up the first compaction
     * should not touch the newer obsolete directories to not to violate the retentionTime for those.
     */
    if (ci.highestWriteId < validWriteIdList.getHighWatermark()) {
      validWriteIdList = validWriteIdList.updateHighWatermark(ci.highestWriteId);
    }
    return validWriteIdList;
  }
}
