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

package org.apache.ambari.server.upgrade;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import junit.framework.Assert;
import org.apache.ambari.server.actionmanager.ActionManager;
import org.apache.ambari.server.configuration.Configuration;
import org.apache.ambari.server.controller.AmbariManagementController;
import org.apache.ambari.server.controller.AmbariManagementControllerImpl;
import org.apache.ambari.server.controller.KerberosHelper;
import org.apache.ambari.server.controller.MaintenanceStateHelper;
import org.apache.ambari.server.orm.DBAccessor;
import org.apache.ambari.server.state.Cluster;
import org.apache.ambari.server.state.Clusters;
import org.apache.ambari.server.state.Config;
import org.apache.ambari.server.state.Service;
import org.apache.ambari.server.state.stack.OsFamily;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.easymock.MockType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.persistence.EntityManager;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMockBuilder;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertTrue;

/**
 * {@link UpgradeCatalog250} unit tests.
 */
@RunWith(EasyMockRunner.class)
public class UpgradeCatalog250Test {

  //  private Injector injector;
  @Mock(type = MockType.STRICT)
  private Provider<EntityManager> entityManagerProvider;

  @Mock(type = MockType.NICE)
  private EntityManager entityManager;

  @Mock(type = MockType.NICE)
  private DBAccessor dbAccessor;

  @Mock(type = MockType.NICE)
  private Configuration configuration;

  @Mock(type = MockType.NICE)
  private Connection connection;

  @Mock(type = MockType.NICE)
  private Statement statement;

  @Mock(type = MockType.NICE)
  private ResultSet resultSet;

  @Mock(type = MockType.NICE)
  private OsFamily osFamily;

  @Mock(type = MockType.NICE)
  private KerberosHelper kerberosHelper;

  @Mock(type = MockType.NICE)
  private ActionManager actionManager;

  @Mock(type = MockType.NICE)
  private Config config;

  @Mock(type = MockType.STRICT)
  private Service service;

  @Mock(type = MockType.NICE)
  private Clusters clusters;

  @Mock(type = MockType.NICE)
  private  Cluster cluster;

  @Mock(type = MockType.NICE)
  private Injector injector;

  @Before
  public void init() {
    reset(entityManagerProvider, injector);

    expect(entityManagerProvider.get()).andReturn(entityManager).anyTimes();

    expect(injector.getInstance(Gson.class)).andReturn(null).anyTimes();
    expect(injector.getInstance(MaintenanceStateHelper.class)).andReturn(null).anyTimes();
    expect(injector.getInstance(KerberosHelper.class)).andReturn(kerberosHelper).anyTimes();

    replay(entityManagerProvider, injector);
  }

  @After
  public void tearDown() {
  }

  @Test
  public void testExecuteDDLUpdates() throws Exception {
    // !!! setup capture for host_version
    dbAccessor.addUniqueConstraint("host_version", "UQ_host_repo", "repo_version_id", "host_id");

    Capture<DBAccessor.DBColumnInfo> groupGroupType = newCapture();
    dbAccessor.addColumn(eq(UpgradeCatalog250.GROUPS_TABLE), capture(groupGroupType));
    dbAccessor.addUniqueConstraint("groups", "UNQ_groups_0", "group_name", "group_type");

    expectLastCall().once();

    // !!! setup capture for servicecomponent_version
    Capture<List<DBAccessor.DBColumnInfo>> capturedComponentVersionColumns = newCapture();

    dbAccessor.createTable(eq(UpgradeCatalog250.COMPONENT_VERSION_TABLE), capture(capturedComponentVersionColumns),
      eq((String[]) null));

    dbAccessor.addPKConstraint(eq(UpgradeCatalog250.COMPONENT_VERSION_TABLE),
      eq(UpgradeCatalog250.COMPONENT_VERSION_PK), eq("id"));
    dbAccessor.addFKConstraint(eq(UpgradeCatalog250.COMPONENT_VERSION_TABLE),
      eq(UpgradeCatalog250.COMPONENT_VERSION_FK_COMPONENT), eq("component_id"),
      eq(UpgradeCatalog250.COMPONENT_TABLE), eq("id"), eq(false));
    dbAccessor.addFKConstraint(eq(UpgradeCatalog250.COMPONENT_VERSION_TABLE),
      eq(UpgradeCatalog250.COMPONENT_VERSION_FK_REPO_VERSION), eq("repo_version_id"),
      eq("repo_version"), eq("repo_version_id"), eq(false));

    // servicedesiredstate table
    Capture<DBAccessor.DBColumnInfo> capturedCredentialStoreSupportedCol = newCapture();
    Capture<DBAccessor.DBColumnInfo> capturedCredentialStoreEnabledCol = newCapture();
    dbAccessor.addColumn(eq(UpgradeCatalog250.SERVICE_DESIRED_STATE_TABLE), capture(capturedCredentialStoreSupportedCol));
    dbAccessor.addColumn(eq(UpgradeCatalog250.SERVICE_DESIRED_STATE_TABLE), capture(capturedCredentialStoreEnabledCol));

    expect(dbAccessor.getConnection()).andReturn(connection).anyTimes();
    expect(connection.createStatement()).andReturn(statement).anyTimes();
    expect(statement.executeQuery(anyObject(String.class))).andReturn(resultSet).anyTimes();
    expect(configuration.getDatabaseType()).andReturn(Configuration.DatabaseType.POSTGRES).anyTimes();

    replay(dbAccessor, configuration, connection, statement, resultSet);

    Module module = new Module() {
      @Override
      public void configure(Binder binder) {
        binder.bind(DBAccessor.class).toInstance(dbAccessor);
        binder.bind(OsFamily.class).toInstance(osFamily);
        binder.bind(EntityManager.class).toInstance(entityManager);
        binder.bind(Configuration.class).toInstance(configuration);
      }
    };

    Injector injector = Guice.createInjector(module);
    UpgradeCatalog250 upgradeCatalog250 = injector.getInstance(UpgradeCatalog250.class);
    upgradeCatalog250.executeDDLUpdates();

    DBAccessor.DBColumnInfo capturedGroupTypeColumn = groupGroupType.getValue();
    Assert.assertNotNull(capturedGroupTypeColumn);
    Assert.assertEquals(UpgradeCatalog250.GROUP_TYPE_COL, capturedGroupTypeColumn.getName());
    Assert.assertEquals(String.class, capturedGroupTypeColumn.getType());
    Assert.assertEquals(null, capturedGroupTypeColumn.getLength());
    Assert.assertEquals("LOCAL", capturedGroupTypeColumn.getDefaultValue());
    Assert.assertEquals(false, capturedGroupTypeColumn.isNullable());

    verify(dbAccessor);

    // !!! check the captured for host_version
    // (no checks)

    // !!! check the captured for servicecomponent_version
    Map<String, DBAccessor.DBColumnInfo> expected = new HashMap<>();
    expected.put("id", new DBAccessor.DBColumnInfo("id", Long.class, null, null, false));
    expected.put("component_id", new DBAccessor.DBColumnInfo("component_id", Long.class, null, null, false));
    expected.put("repo_version_id", new DBAccessor.DBColumnInfo("repo_version_id", Long.class, null, null, false));
    expected.put("state", new DBAccessor.DBColumnInfo("state", String.class, 32, null, false));
    expected.put("user_name", new DBAccessor.DBColumnInfo("user_name", String.class, 255, null, false));

    List<DBAccessor.DBColumnInfo> captured = capturedComponentVersionColumns.getValue();
    Assert.assertEquals(5, captured.size());

    for (DBAccessor.DBColumnInfo column : captured) {
      DBAccessor.DBColumnInfo expectedColumn = expected.remove(column.getName());

      Assert.assertNotNull(expectedColumn);
      Assert.assertEquals(expectedColumn.getDefaultValue(), column.getDefaultValue());
      Assert.assertEquals(expectedColumn.getName(), column.getName());
      Assert.assertEquals(expectedColumn.getLength(), column.getLength());
      Assert.assertEquals(expectedColumn.getType(), column.getType());
      Assert.assertEquals(expectedColumn.getClass(), column.getClass());
    }

    // did we get them all?
    Assert.assertEquals(0, expected.size());

    // Verify if credential_store_supported & credential_store_enabled columns
    // were added to servicedesiredstate table
    DBAccessor.DBColumnInfo capturedCredentialStoreSupportedColValues = capturedCredentialStoreSupportedCol.getValue();
    Assert.assertNotNull(capturedCredentialStoreSupportedColValues);

    Assert.assertEquals(UpgradeCatalog250.CREDENTIAL_STORE_SUPPORTED_COL, capturedCredentialStoreSupportedColValues.getName());
    Assert.assertEquals(null, capturedCredentialStoreSupportedColValues.getLength());
    Assert.assertEquals(Short.class, capturedCredentialStoreSupportedColValues.getType());
    Assert.assertEquals(0, capturedCredentialStoreSupportedColValues.getDefaultValue());
    Assert.assertEquals(false, capturedCredentialStoreSupportedColValues.isNullable());

    DBAccessor.DBColumnInfo capturedCredentialStoreEnabledColValues = capturedCredentialStoreEnabledCol.getValue();
    Assert.assertNotNull(capturedCredentialStoreEnabledColValues);

    Assert.assertEquals(UpgradeCatalog250.CREDENTIAL_STORE_ENABLED_COL, capturedCredentialStoreEnabledColValues.getName());
    Assert.assertEquals(null, capturedCredentialStoreEnabledColValues.getLength());
    Assert.assertEquals(Short.class, capturedCredentialStoreEnabledColValues.getType());
    Assert.assertEquals(0, capturedCredentialStoreEnabledColValues.getDefaultValue());
    Assert.assertEquals(false, capturedCredentialStoreEnabledColValues.isNullable());
  }

  @Test
  public void testExecuteDMLUpdates() throws Exception {
    Method updateAmsConfigs = UpgradeCatalog250.class.getDeclaredMethod("updateAMSConfigs");
    Method updateKafkaConfigs = UpgradeCatalog250.class.getDeclaredMethod("updateKafkaConfigs");
    Method updateHiveLlapConfigs = UpgradeCatalog250.class.getDeclaredMethod("updateHiveLlapConfigs");
    Method updateHIVEInteractiveConfigs = UpgradeCatalog250.class.getDeclaredMethod("updateHIVEInteractiveConfigs");
    Method updateTEZInteractiveConfigs = UpgradeCatalog250.class.getDeclaredMethod("updateTEZInteractiveConfigs");
    Method addNewConfigurationsFromXml = AbstractUpgradeCatalog.class.getDeclaredMethod("addNewConfigurationsFromXml");
    Method updateTablesForZeppelinViewRemoval = UpgradeCatalog250.class.getDeclaredMethod("updateTablesForZeppelinViewRemoval");
    Method updateAtlasConfigs = UpgradeCatalog250.class.getDeclaredMethod("updateAtlasConfigs");

    UpgradeCatalog250 upgradeCatalog250 = createMockBuilder(UpgradeCatalog250.class)
        .addMockedMethod(updateAmsConfigs)
        .addMockedMethod(updateKafkaConfigs)
        .addMockedMethod(updateHiveLlapConfigs)
        .addMockedMethod(addNewConfigurationsFromXml)
        .addMockedMethod(updateHIVEInteractiveConfigs)
        .addMockedMethod(updateTEZInteractiveConfigs)
        .addMockedMethod(updateTablesForZeppelinViewRemoval)
        .addMockedMethod(updateAtlasConfigs)
        .createMock();

    upgradeCatalog250.updateAMSConfigs();
    expectLastCall().once();

    upgradeCatalog250.addNewConfigurationsFromXml();
    expectLastCall().once();

    upgradeCatalog250.updateKafkaConfigs();
    expectLastCall().once();

    upgradeCatalog250.updateHIVEInteractiveConfigs();
    expectLastCall().once();

    upgradeCatalog250.updateTEZInteractiveConfigs();
    expectLastCall().once();

    upgradeCatalog250.updateHiveLlapConfigs();
    expectLastCall().once();

    upgradeCatalog250.updateTablesForZeppelinViewRemoval();
    expectLastCall().once();

    upgradeCatalog250.updateAtlasConfigs();
    expectLastCall().once();

    replay(upgradeCatalog250);

    upgradeCatalog250.executeDMLUpdates();

    verify(upgradeCatalog250);
  }

  @Test
  public void testAmsEnvUpdateConfigs() throws Exception{

    Map<String, String> oldPropertiesAmsEnv = new HashMap<String, String>() {
      {
        put("content", "\n" +
          "# AMS Collector heapsize\n" +
          "export AMS_COLLECTOR_HEAPSIZE={{metrics_collector_heapsize}}\n" +
          "\n" +
          "# HBase normalizer enabled\n" +
          "export AMS_HBASE_NORMALIZER_ENABLED={{ams_hbase_normalizer_enabled}}\n" +
          "\n" +
          "# HBase compaction policy enabled\n" +
          "export HBASE_FIFO_COMPACTION_POLICY_ENABLED={{ams_hbase_fifo_compaction_policy_enabled}}\n" +
          "\n" +
          "# HBase Tables Initialization check enabled\n" +
          "export AMS_HBASE_INIT_CHECK_ENABLED={{ams_hbase_init_check_enabled}}\n");
      }
    };
    Map<String, String> newPropertiesAmsEnv = new HashMap<String, String>() {
      {
        put("content", "\n" +
          "# AMS Collector heapsize\n" +
          "export AMS_COLLECTOR_HEAPSIZE={{metrics_collector_heapsize}}\n" +
          "\n" +
          "# HBase Tables Initialization check enabled\n" +
          "export AMS_HBASE_INIT_CHECK_ENABLED={{ams_hbase_init_check_enabled}}\n");
      }
    };
    EasyMockSupport easyMockSupport = new EasyMockSupport();

    Config mockAmsEnv = easyMockSupport.createNiceMock(Config.class);

    reset(clusters, cluster);
    expect(clusters.getClusters()).andReturn(new HashMap<String, Cluster>() {{
      put("normal", cluster);
    }}).once();
    expect(cluster.getDesiredConfigByType("ams-env")).andReturn(mockAmsEnv).atLeastOnce();
    expect(mockAmsEnv.getProperties()).andReturn(oldPropertiesAmsEnv).anyTimes();

    replay(clusters, mockAmsEnv, cluster);

    AmbariManagementControllerImpl controller = (AmbariManagementControllerImpl)createMockBuilder(AmbariManagementControllerImpl.class)
      .addMockedMethod("createConfiguration")
      .addMockedMethod("getClusters", new Class[] { })
      .addMockedMethod("createConfig")
      .withConstructor(actionManager, clusters, injector)
      .createNiceMock();

    Injector injector2 = easyMockSupport.createNiceMock(Injector.class);
    Capture<Map> propertiesCapture = EasyMock.newCapture();

    expect(injector2.getInstance(AmbariManagementController.class)).andReturn(controller).anyTimes();
    expect(controller.getClusters()).andReturn(clusters).anyTimes();
    expect(controller.createConfig(anyObject(Cluster.class), anyString(), capture(propertiesCapture), anyString(),
      anyObject(Map.class))).andReturn(config).once();

    replay(controller, injector2);
    new UpgradeCatalog250(injector2).updateAMSConfigs();
    easyMockSupport.verifyAll();

    Map<String, String> updatedProperties = propertiesCapture.getValue();
    assertTrue(Maps.difference(newPropertiesAmsEnv, updatedProperties).areEqual());
  }


  @Test
  public void testKafkaUpdateConfigs() throws Exception{

    Map<String, String> oldProperties = new HashMap<String, String>() {
      {
        put("kafka.timeline.metrics.host", "{{metric_collector_host}}");
        put("kafka.timeline.metrics.port", "{{metric_collector_port}}");
      }
    };
    Map<String, String> newProperties = new HashMap<String, String>() {
      {
        put("kafka.timeline.metrics.port", "{{metric_collector_port}}");
      }
    };
    EasyMockSupport easyMockSupport = new EasyMockSupport();
    Config mockKafkaBroker = easyMockSupport.createNiceMock(Config.class);

    reset(clusters, cluster);
    expect(clusters.getClusters()).andReturn(new HashMap<String, Cluster>() {{
      put("normal", cluster);
    }}).once();
    expect(cluster.getDesiredConfigByType("kafka-broker")).andReturn(mockKafkaBroker).atLeastOnce();
    expect(mockKafkaBroker.getProperties()).andReturn(oldProperties).anyTimes();

    replay(clusters, mockKafkaBroker, cluster);

    AmbariManagementControllerImpl controller = (AmbariManagementControllerImpl)createMockBuilder(AmbariManagementControllerImpl.class)
      .addMockedMethod("createConfiguration")
      .addMockedMethod("getClusters", new Class[] { })
      .addMockedMethod("createConfig")
      .withConstructor(actionManager, clusters, injector)
      .createNiceMock();

    Injector injector2 = easyMockSupport.createNiceMock(Injector.class);
    Capture<Map> propertiesCapture = EasyMock.newCapture();

    expect(injector2.getInstance(AmbariManagementController.class)).andReturn(controller).anyTimes();
    expect(controller.getClusters()).andReturn(clusters).anyTimes();
    expect(controller.createConfig(anyObject(Cluster.class), anyString(), capture(propertiesCapture), anyString(),
      anyObject(Map.class))).andReturn(config).once();

    replay(controller, injector2);
    new UpgradeCatalog250(injector2).updateKafkaConfigs();
    easyMockSupport.verifyAll();

    Map<String, String> updatedProperties = propertiesCapture.getValue();
    assertTrue(Maps.difference(newProperties, updatedProperties).areEqual());
  }

  @Test
  public void testUpdateAtlasConfigs() throws Exception {

    Map<String, String> oldHiveProperties = new HashMap<String, String>();
    Map<String, String> newHiveProperties = new HashMap<String, String>();

    oldHiveProperties.put("hive.atlas.hook", "false");
    newHiveProperties.put("hive.atlas.hook", "true");
    testUpdateAtlasHookConfig(oldHiveProperties, newHiveProperties, "hive-env");

    Map<String, String> oldStormProperties = new HashMap<String, String>();
    Map<String, String> newStormProperties = new HashMap<String, String>();
    oldStormProperties.put("storm.atlas.hook", "false");
    newStormProperties.put("storm.atlas.hook", "true");
    testUpdateAtlasHookConfig(oldStormProperties, newStormProperties, "storm-env");

    Map<String, String> oldFalconProperties = new HashMap<String, String>();
    Map<String, String> newFalconProperties = new HashMap<String, String>();
    oldFalconProperties.put("falcon.atlas.hook", "false");
    newFalconProperties.put("falcon.atlas.hook", "true");
    testUpdateAtlasHookConfig(oldFalconProperties, newFalconProperties, "falcon-env");

    Map<String, String> oldSqoopProperties = new HashMap<String, String>();
    Map<String, String> newSqoopProperties = new HashMap<String, String>();
    oldSqoopProperties.put("sqoop.atlas.hook", "false");
    newSqoopProperties.put("sqoop.atlas.hook", "true");
    testUpdateAtlasHookConfig(oldSqoopProperties, newSqoopProperties, "sqoop-env");
  }

  public void testUpdateAtlasHookConfig(Map<String, String> oldProperties, Map<String, String> newProperties, String configType) throws Exception {

    Map<String, Service> installedServices = new HashMap<String, Service>() {
      {
        put("ATLAS", null);
        put("HIVE", null);
        put("STORM", null);
        put("FALCON", null);
        put("SQOOP", null);
      }
    };

    EasyMockSupport easyMockSupport = new EasyMockSupport();

    reset(clusters, cluster);
    expect(clusters.getClusters()).andReturn(new HashMap<String, Cluster>() {{
      put("normal", cluster);
    }}).once();
    expect(cluster.getClusterName()).andReturn("cl1").once();
    expect(cluster.getServices()).andReturn(installedServices).atLeastOnce();

    Config mockAtlasConfig = easyMockSupport.createNiceMock(Config.class);
    expect(cluster.getDesiredConfigByType(configType)).andReturn(mockAtlasConfig).atLeastOnce();
    expect(mockAtlasConfig.getProperties()).andReturn(oldProperties).anyTimes();

    replay(clusters, mockAtlasConfig, cluster);

    AmbariManagementControllerImpl controller = (AmbariManagementControllerImpl)createMockBuilder(AmbariManagementControllerImpl.class)
      .addMockedMethod("createConfiguration")
      .addMockedMethod("getClusters", new Class[] { })
      .addMockedMethod("createConfig")
      .withConstructor(actionManager, clusters, injector)
      .createNiceMock();

    Injector injector2 = easyMockSupport.createNiceMock(Injector.class);
    Capture<Map> propertiesCapture = EasyMock.newCapture();

    expect(injector2.getInstance(AmbariManagementController.class)).andReturn(controller).anyTimes();
    expect(controller.getClusters()).andReturn(clusters).anyTimes();
    expect(controller.createConfig(anyObject(Cluster.class), anyString(), capture(propertiesCapture), anyString(),
      anyObject(Map.class))).andReturn(config).once();

    replay(controller, injector2);
    new UpgradeCatalog250(injector2).updateAtlasConfigs();
    easyMockSupport.verifyAll();

    Map<String, String> updatedProperties = propertiesCapture.getValue();
    assertTrue(Maps.difference(newProperties, updatedProperties).areEqual());
  }
}
