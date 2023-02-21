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

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.txn.TxnStore;
import org.apache.hadoop.hive.ql.txn.compactor.CacheContainer;

import java.util.Arrays;
import java.util.List;

/**
 * A factory class to fetch handlers.
 */
public class CleaningRequestHandlerFactory {
  private static final CleaningRequestHandlerFactory INSTANCE = new CleaningRequestHandlerFactory();

  public static CleaningRequestHandlerFactory getInstance() {
    return INSTANCE;
  }

  /**
   * Factory class, no need to expose constructor.
   */
  private CleaningRequestHandlerFactory() {
  }

  public List<CleaningRequestHandler> getHandlers(HiveConf conf, TxnStore txnHandler, CacheContainer cacheContainer, boolean metricsEnabled) {
    return Arrays.asList(new CompactionCleaningRequestHandler(conf, txnHandler, cacheContainer, metricsEnabled));
  }
}
