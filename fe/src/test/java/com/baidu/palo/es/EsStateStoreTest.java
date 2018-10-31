// Copyright (c) 2017, Baidu.com, Inc. All Rights Reserved

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.baidu.palo.es;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.Map;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.baidu.palo.catalog.Catalog;
import com.baidu.palo.catalog.CatalogTestUtil;
import com.baidu.palo.catalog.EsTable;
import com.baidu.palo.catalog.FakeCatalog;
import com.baidu.palo.catalog.FakeEditLog;
import com.baidu.palo.catalog.PartitionKey;
import com.baidu.palo.catalog.RangePartitionInfo;
import com.baidu.palo.common.AnalysisException;
import com.baidu.palo.common.FeMetaVersion;
import com.baidu.palo.external.EsIndexState;
import com.baidu.palo.external.EsStateStore;
import com.baidu.palo.external.EsTableState;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;

public class EsStateStoreTest {

    private static FakeEditLog fakeEditLog;
    private static FakeCatalog fakeCatalog;
    private static Catalog masterCatalog;
    private static String clusterStateStr1 = "";
    private static String clusterStateStr2 = "";
    private static String clusterStateStr3 = "";
    private EsStateStore esStateStore;
    
    @BeforeClass
    public static void init() throws IOException, InstantiationException, IllegalAccessException, 
        IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException,
        URISyntaxException {
        fakeEditLog = new FakeEditLog();
        fakeCatalog = new FakeCatalog();
        masterCatalog = CatalogTestUtil.createTestCatalog();
        masterCatalog.setJournalVersion(FeMetaVersion.VERSION_40);
        FakeCatalog.setCatalog(masterCatalog);
        clusterStateStr1 = loadJsonFromFile("data/es/clusterstate1.json");
        clusterStateStr2 = loadJsonFromFile("data/es/clusterstate2.json");
        clusterStateStr3 = loadJsonFromFile("data/es/clusterstate3.json");
    }
    
    @Before
    public void setUp() {
        esStateStore = new EsStateStore();
    }
    
    /**
     * partitioned es table schema: k1(date), k2(int), v(double)
     * @throws AnalysisException 
     */
    @Test
    public void testParsePartitionedClusterState() throws AnalysisException {
        EsTable esTable = (EsTable) Catalog.getCurrentCatalog()
                                            .getDb(CatalogTestUtil.testDb1)
                                            .getTable(CatalogTestUtil.testPartitionedEsTable1);
        boolean hasException = false;
        EsTableState esTableState = null;
        try {
            esTableState = esStateStore.parseClusterState55(clusterStateStr1, esTable);
        } catch (Exception e) {
            e.printStackTrace();
            hasException = true;
        }
        assertFalse(hasException);
        assertNotNull(esTableState);
        assertEquals(2, esTableState.getPartitionedIndexStates().size());
        RangePartitionInfo definedPartInfo = (RangePartitionInfo) esTable.getPartitionInfo();
        RangePartitionInfo rangePartitionInfo = (RangePartitionInfo) esTableState.getPartitionInfo();
        Map<Long, Range<PartitionKey>> rangeMap = rangePartitionInfo.getIdToRange();
        assertEquals(2, rangeMap.size());
        Range<PartitionKey> part0 = rangeMap.get(new Long(0));
        EsIndexState esIndexState1 = esTableState.getIndexState(0);
        assertEquals(5, esIndexState1.getShardRoutings().size());
        assertEquals("index1", esIndexState1.getIndexName());
        PartitionKey lowKey = PartitionKey.createInfinityPartitionKey(definedPartInfo.getPartitionColumns(), false);
        PartitionKey upperKey = PartitionKey.createPartitionKey(Lists.newArrayList("2018-10-01"), 
                definedPartInfo.getPartitionColumns());
        Range<PartitionKey> newRange = Range.closedOpen(lowKey, upperKey);
        assertEquals(newRange, part0);
        Range<PartitionKey> part1 = rangeMap.get(new Long(1));
        EsIndexState esIndexState2 = esTableState.getIndexState(1);
        assertEquals("index2", esIndexState2.getIndexName());
        lowKey = PartitionKey.createPartitionKey(Lists.newArrayList("2018-10-01"), 
                definedPartInfo.getPartitionColumns());
        upperKey = PartitionKey.createPartitionKey(Lists.newArrayList("2018-10-02"), 
                definedPartInfo.getPartitionColumns());
        newRange = Range.closedOpen(lowKey, upperKey);
        assertEquals(newRange, part1);
        assertEquals(6, esIndexState2.getShardRoutings().size());
    }
    
    /**
     * partitioned es table schema: k1(date), k2(int), v(double)
     * scenario desc:
     * 2 indices, one with partition desc, the other does not contains partition desc
     * @throws AnalysisException 
     */
    @Test
    public void testParsePartitionedClusterStateTwoIndices() throws AnalysisException {
        EsTable esTable = (EsTable) Catalog.getCurrentCatalog()
                                            .getDb(CatalogTestUtil.testDb1)
                                            .getTable(CatalogTestUtil.testPartitionedEsTable1);
        boolean hasException = false;
        EsTableState esTableState = null;
        try {
            esTableState = esStateStore.parseClusterState55(clusterStateStr3, esTable);
        } catch (Exception e) {
            e.printStackTrace();
            hasException = true;
        }
        assertFalse(hasException);
        assertNotNull(esTableState);
        
        // check 
        assertEquals(1, esTableState.getPartitionedIndexStates().size());
        assertEquals(1, esTableState.getUnPartitionedIndexStates().size());
        
        // check partition info
        RangePartitionInfo definedPartInfo = (RangePartitionInfo) esTable.getPartitionInfo();
        RangePartitionInfo rangePartitionInfo = (RangePartitionInfo) esTableState.getPartitionInfo();
        Map<Long, Range<PartitionKey>> rangeMap = rangePartitionInfo.getIdToRange();
        assertEquals(1, rangeMap.size());
        Range<PartitionKey> part0 = rangeMap.get(new Long(0));
        EsIndexState esIndexState1 = esTableState.getIndexState(0);
        assertEquals(5, esIndexState1.getShardRoutings().size());
        assertEquals("index1", esIndexState1.getIndexName());
        PartitionKey lowKey = PartitionKey.createInfinityPartitionKey(definedPartInfo.getPartitionColumns(), false);
        PartitionKey upperKey = PartitionKey.createPartitionKey(Lists.newArrayList("2018-10-01"), 
                definedPartInfo.getPartitionColumns());
        Range<PartitionKey> newRange = Range.closedOpen(lowKey, upperKey);
        assertEquals(newRange, part0);
        
        // check index with no partition desc
        EsIndexState esIndexState2 = esTableState.getUnPartitionedIndexStates().get("index2");
        assertEquals("index2", esIndexState2.getIndexName());
        assertEquals(6, esIndexState2.getShardRoutings().size());
    }
    
    /**
     * partitioned es table schema: k1(date), k2(int), v(double)
     * "upperbound": "2018" is not a valid date value, so parsing procedure will fail
     */
    @Test
    public void testParseInvalidUpperbound() {
        EsTable esTable = (EsTable) Catalog.getCurrentCatalog()
                                            .getDb(CatalogTestUtil.testDb1)
                                            .getTable(CatalogTestUtil.testPartitionedEsTable1);
        boolean hasException = false;
        EsTableState esTableState = null;
        try {
            esTableState = esStateStore.parseClusterState55(clusterStateStr2, esTable);
        } catch (Exception e) {
            hasException = true;
        }
        assertTrue(hasException);
        assertTrue(esTableState == null);
    }
    
    private static String loadJsonFromFile(String fileName) throws IOException, URISyntaxException {
        File file = new File(EsStateStoreTest.class.getClassLoader().getResource(fileName).toURI());
        InputStream is = new FileInputStream(file);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));  
        StringBuilder jsonStr = new StringBuilder();
        String line = "";
        while ((line = br.readLine()) != null)  {
            jsonStr.append(line);
        }
        br.close();
        is.close();
        return jsonStr.toString();
    }
}