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

package org.apache.flink.connector.hbase2.source;

import org.apache.flink.connector.hbase.util.HBaseTableSchema;
import org.apache.flink.connector.hbase2.util.HBaseTestBase;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.connector.source.lookup.LookupOptions;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.DataType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.apache.flink.table.api.DataTypes.BIGINT;
import static org.apache.flink.table.api.DataTypes.DOUBLE;
import static org.apache.flink.table.api.DataTypes.FIELD;
import static org.apache.flink.table.api.DataTypes.INT;
import static org.apache.flink.table.api.DataTypes.ROW;
import static org.apache.flink.table.api.DataTypes.STRING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Test suite for {@link HBaseRowDataAsyncLookupFunction}. */
public class HBaseRowDataAsyncLookupFunctionTest extends HBaseTestBase {
    @Test
    public void testEval() throws Exception {
        HBaseRowDataAsyncLookupFunction lookupFunction = buildRowDataAsyncLookupFunction();

        lookupFunction.open(null);
        final List<String> result = new ArrayList<>();
        int[] rowkeys = {1, 2, 1, 12, 3, 12, 4, 3};
        CountDownLatch latch = new CountDownLatch(rowkeys.length);
        for (int rowkey : rowkeys) {
            CompletableFuture<Collection<RowData>> future = new CompletableFuture<>();
            lookupFunction.eval(future, rowkey);
            future.whenComplete(
                    (rs, t) -> {
                        synchronized (result) {
                            if (rs.isEmpty()) {
                                result.add(rowkey + ": null");
                            } else {
                                rs.forEach(row -> result.add(rowkey + ": " + row.toString()));
                            }
                        }
                        latch.countDown();
                    });
        }
        // this verifies lookup calls are async
        assertTrue(result.size() < rowkeys.length);
        latch.await();
        lookupFunction.close();
        List<String> sortResult = new ArrayList<>(result);
        Collections.sort(sortResult);
        List<String> expected = new ArrayList<>();
        expected.add("12: null");
        expected.add("12: null");
        expected.add("1: +I(1,+I(10),+I(Hello-1,100),+I(1.01,false,Welt-1))");
        expected.add("1: +I(1,+I(10),+I(Hello-1,100),+I(1.01,false,Welt-1))");
        expected.add("2: +I(2,+I(20),+I(Hello-2,200),+I(2.02,true,Welt-2))");
        expected.add("3: +I(3,+I(30),+I(Hello-3,300),+I(3.03,false,Welt-3))");
        expected.add("3: +I(3,+I(30),+I(Hello-3,300),+I(3.03,false,Welt-3))");
        expected.add("4: +I(4,+I(40),+I(null,400),+I(4.04,true,Welt-4))");
        assertEquals(expected, sortResult);
    }

    private HBaseRowDataAsyncLookupFunction buildRowDataAsyncLookupFunction() {
        DataType dataType =
                ROW(
                        FIELD(ROW_KEY, INT()),
                        FIELD(FAMILY1, ROW(FIELD(F1COL1, INT()))),
                        FIELD(FAMILY2, ROW(FIELD(F2COL1, STRING()), FIELD(F2COL2, BIGINT()))),
                        FIELD(
                                FAMILY3,
                                ROW(
                                        FIELD(F3COL1, DOUBLE()),
                                        FIELD(F3COL2, DataTypes.BOOLEAN()),
                                        FIELD(F3COL3, STRING()))));
        HBaseTableSchema hbaseSchema = HBaseTableSchema.fromDataType(dataType);
        return new HBaseRowDataAsyncLookupFunction(
                getConf(),
                TEST_TABLE_1,
                hbaseSchema,
                "null",
                LookupOptions.MAX_RETRIES.defaultValue());
    }
}
