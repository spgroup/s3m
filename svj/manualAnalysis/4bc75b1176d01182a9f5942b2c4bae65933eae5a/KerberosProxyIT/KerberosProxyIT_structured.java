/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.test.functional; 

import static java.nio.charset.StandardCharsets.UTF_8; 
import static org.junit.Assert.assertEquals; 
import static org.junit.Assert.assertTrue; 

import java.io.File; 
import java.io.FileWriter; 
import java.io.IOException; 
import java.net.ConnectException; 
import java.net.InetAddress; 
import java.nio.ByteBuffer; 
import java.util.Collections; 
import java.util.HashMap; 
import java.util.List; 
import java.util.Map; 
import java.util.Properties; 

import org.apache.accumulo.cluster.ClusterUser; 
import org.apache.accumulo.core.client.security.tokens.KerberosToken; 
import org.apache.accumulo.core.client.security.tokens.PasswordToken; 
import org.apache.accumulo.core.conf.Property; 
import org.apache.accumulo.core.rpc.UGIAssumingTransport; 
import org.apache.accumulo.harness.AccumuloITBase; 
 
import org.apache.accumulo.harness.MiniClusterConfigurationCallback; 
import org.apache.accumulo.harness.MiniClusterHarness; 
import org.apache.accumulo.harness.TestingKdc; 
import org.apache.accumulo.minicluster.impl.MiniAccumuloClusterImpl; 
import org.apache.accumulo.minicluster.impl.MiniAccumuloConfigImpl; 
import org.apache.accumulo.proxy.Proxy; 
import org.apache.accumulo.proxy.ProxyServer; 
import org.apache.accumulo.proxy.thrift.AccumuloProxy; 
import org.apache.accumulo.proxy.thrift.AccumuloProxy.Client; 
import org.apache.accumulo.proxy.thrift.AccumuloSecurityException; 
import org.apache.accumulo.proxy.thrift.ColumnUpdate; 
import org.apache.accumulo.proxy.thrift.Key; 
import org.apache.accumulo.proxy.thrift.KeyValue; 
import org.apache.accumulo.proxy.thrift.ScanOptions; 
import org.apache.accumulo.proxy.thrift.ScanResult; 
import org.apache.accumulo.proxy.thrift.TimeType; 
import org.apache.accumulo.proxy.thrift.WriterOptions; 
import org.apache.accumulo.server.util.PortUtils; 
import org.apache.hadoop.conf.Configuration; 
import org.apache.hadoop.fs.CommonConfigurationKeysPublic; 
import org.apache.hadoop.security.UserGroupInformation; 
import org.apache.thrift.protocol.TCompactProtocol; 
import org.apache.thrift.transport.TSaslClientTransport; 
import org.apache.thrift.transport.TSocket; 
import org.apache.thrift.transport.TTransportException; 
import org.hamcrest.Description; 
import org.hamcrest.TypeSafeMatcher; 
import org.junit.After; 
import org.junit.AfterClass; 
import org.junit.Before; 
import org.junit.BeforeClass; 
import org.junit.Rule; 
import org.junit.Test; 
import org.junit.rules.ExpectedException; 
import org.slf4j.Logger; 
import org.slf4j.LoggerFactory; 

/**
 * Tests impersonation of clients by the proxy over SASL
 */
public  class  KerberosProxyIT  extends AccumuloITBase {
	
  

  private static final Logger log = LoggerFactory.getLogger(KerberosProxyIT.class);

	

  

  @Rule public ExpectedException thrown = ExpectedException.none();

	

  

  private static TestingKdc kdc;

	
  

  private static String krbEnabledForITs = null;

	
  

  private static File proxyKeytab;

	
  

  private static String hostname, proxyPrimary, proxyPrincipal;

	

  

  @Override protected int defaultTimeoutSeconds() {
    return 60 * 5;
  }

	

  

  @BeforeClass public static void startKdc() throws Exception {
    kdc = new TestingKdc();
    kdc.start();
    krbEnabledForITs = System.getProperty(MiniClusterHarness.USE_KERBEROS_FOR_IT_OPTION);
    if (null == krbEnabledForITs || !Boolean.parseBoolean(krbEnabledForITs)) {
      System.setProperty(MiniClusterHarness.USE_KERBEROS_FOR_IT_OPTION, "true");
    }
    proxyKeytab = new File(kdc.getKeytabDir(), "proxy.keytab");
    hostname = InetAddress.getLocalHost().getCanonicalHostName();
    proxyPrimary = "proxy";
    proxyPrincipal = proxyPrimary + "/" + hostname;
    kdc.createPrincipal(proxyKeytab, proxyPrincipal);
    proxyPrincipal = kdc.qualifyUser(proxyPrincipal);
  }

	

  

  @AfterClass public static void stopKdc() throws Exception {
    if (null != kdc) {
      kdc.stop();
    }
    if (null != krbEnabledForITs) {
      System.setProperty(MiniClusterHarness.USE_KERBEROS_FOR_IT_OPTION, krbEnabledForITs);
    }
    UserGroupInformation.setConfiguration(new Configuration(false));
  }

	

  

  private MiniAccumuloClusterImpl mac;

	
  

  private Process proxyProcess;

	
  

  private int proxyPort;

	

  

  @Before public void startMac() throws Exception {
    MiniClusterHarness harness = new MiniClusterHarness();
    mac = harness.create(getClass().getName(), testName.getMethodName(), new PasswordToken("unused"), new MiniClusterConfigurationCallback() {
      @Override public void configureMiniCluster(MiniAccumuloConfigImpl cfg, Configuration coreSite) {
        cfg.setNumTservers(1);
        Map<String, String> siteCfg = cfg.getSiteConfig();
        siteCfg.put(Property.INSTANCE_RPC_SASL_ALLOWED_USER_IMPERSONATION.getKey(), proxyPrincipal + ":" + kdc.getRootUser().getPrincipal());
        siteCfg.put(Property.INSTANCE_RPC_SASL_ALLOWED_HOST_IMPERSONATION.getKey(), "*");
        cfg.setSiteConfig(siteCfg);
      }
    }, kdc);
    mac.start();
    MiniAccumuloConfigImpl cfg = mac.getConfig();
    proxyProcess = startProxy(cfg);
    Configuration conf = new Configuration(false);
    conf.set(CommonConfigurationKeysPublic.HADOOP_SECURITY_AUTHENTICATION, "kerberos");
    UserGroupInformation.setConfiguration(conf);
    boolean success = false;
    ClusterUser rootUser = kdc.getRootUser();

<<<<<<< C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var1_4799533799878004060.java
    for (int i = 0; i < 10 && !success; i++) {
      UserGroupInformation ugi;
      try {
        ugi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(rootUser.getPrincipal(), rootUser.getKeytab().getAbsolutePath());
      } catch (IOException ex) {
        log.info("Login as root is failing", ex);
        Thread.sleep(3000);
        continue;
      }
      TSocket socket = new TSocket(hostname, proxyPort);
      log.info("Connecting to proxy with server primary \'" + proxyPrimary + "\' running on " + hostname);
      TSaslClientTransport transport = new TSaslClientTransport("GSSAPI", null, proxyPrimary, hostname, Collections.singletonMap("javax.security.sasl.qop", "auth"), null, socket);
      final UGIAssumingTransport ugiTransport = new UGIAssumingTransport(transport, ugi);
      try {
        ugiTransport.open();
        success = true;
      } catch (TTransportException e) {
        Throwable cause = e.getCause();
        if (null != cause && cause instanceof ConnectException) {
          log.info("Proxy not yet up, waiting");
          Thread.sleep(3000);
          continue;
        }
      } finally {
        if (null != ugiTransport) {
          ugiTransport.close();
        }
      }
    }
=======
    while (!success) {
      UserGroupInformation ugi;
      try {
        UserGroupInformation.loginUserFromKeytab(rootUser.getPrincipal(), rootUser.getKeytab().getAbsolutePath());
        ugi = UserGroupInformation.getCurrentUser();
      } catch (IOException ex) {
        log.info("Login as root is failing", ex);
        Thread.sleep(3000);
        continue;
      }
      TSocket socket = new TSocket(hostname, proxyPort);
      log.info("Connecting to proxy with server primary \'" + proxyPrimary + "\' running on " + hostname);
      TSaslClientTransport transport = new TSaslClientTransport("GSSAPI", null, proxyPrimary, hostname, Collections.singletonMap("javax.security.sasl.qop", "auth"), null, socket);
      final UGIAssumingTransport ugiTransport = new UGIAssumingTransport(transport, ugi);
      try {
        ugiTransport.open();
        success = true;
      } catch (TTransportException e) {
        Throwable cause = e.getCause();
        if (null != cause && cause instanceof ConnectException) {
          log.info("Proxy not yet up, waiting");
          Thread.sleep(3000);
          proxyProcess = checkProxyAndRestart(proxyProcess, cfg);
          continue;
        }
      } finally {
        if (null != ugiTransport) {
          ugiTransport.close();
        }
      }
    }
>>>>>>> C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var2_3092042001016068723.java

    assertTrue("Failed to connect to the proxy repeatedly", success);
  }

	

  /**
   * Starts the thrift proxy using the given MAConfig.
   *
   * @param cfg
   *          configuration for MAC
   * @return Process for the thrift proxy
   */
  private Process startProxy(MiniAccumuloConfigImpl cfg) throws IOException {
    File proxyPropertiesFile = generateNewProxyConfiguration(cfg);
    return mac.exec(Proxy.class, "-p", proxyPropertiesFile.getCanonicalPath());
  }
	

  /**
   * Generates a proxy configuration file for the MAC instance. Implicitly updates {@link #proxyPort} when choosing the port the proxy will listen on.
   *
   * @param cfg
   *          The MAC configuration
   * @return The proxy's configuration file
   */
  private File generateNewProxyConfiguration(MiniAccumuloConfigImpl cfg) throws IOException {
    // Chooses a new port for the proxy as side-effect
    proxyPort = PortUtils.getRandomFreePort();

    // Proxy configuration
    File proxyPropertiesFile = new File(cfg.getConfDir(), "proxy.properties");
    if (proxyPropertiesFile.exists()) {
      assertTrue("Failed to delete proxy.properties file", proxyPropertiesFile.delete());
    }
    Properties proxyProperties = new Properties();
    proxyProperties.setProperty("useMockInstance", "false");
    proxyProperties.setProperty("useMiniAccumulo", "false");
    proxyProperties.setProperty("protocolFactory", TCompactProtocol.Factory.class.getName());
    proxyProperties.setProperty("tokenClass", KerberosToken.class.getName());
    proxyProperties.setProperty("port", Integer.toString(proxyPort));
    proxyProperties.setProperty("maxFrameSize", "16M");
    proxyProperties.setProperty("instance", mac.getInstanceName());
    proxyProperties.setProperty("zookeepers", mac.getZooKeepers());
    proxyProperties.setProperty("thriftServerType", "sasl");
    proxyProperties.setProperty("kerberosPrincipal", proxyPrincipal);
    proxyProperties.setProperty("kerberosKeytab", proxyKeytab.getCanonicalPath());

    // Write out the proxy.properties file
    FileWriter writer = new FileWriter(proxyPropertiesFile);
    proxyProperties.store(writer, "Configuration for Accumulo proxy");
    writer.close();

    log.info("Created configuration for proxy listening on {}", proxyPort);

    return proxyPropertiesFile;
  }
	

  /**
   * Restarts the thrift proxy if the previous instance is no longer running. If the proxy is still running, this method does nothing.
   *
   * @param proxy
   *          The thrift proxy process
   * @param cfg
   *          The MAC configuration
   * @return The process for the Proxy, either the previous instance or a new instance.
   */
  private Process checkProxyAndRestart(Process proxy, MiniAccumuloConfigImpl cfg) throws IOException {
    try {
      // Get the return code
      proxy.exitValue();
    } catch (IllegalThreadStateException e) {
      log.info("Proxy is still running");
      // OK, process is still running, don't restart
      return proxy;
    }

    log.info("Restarting proxy because it is no longer alive");

    // We got a return code which means the proxy exited. We'll assume this is because it failed
    // to bind the port due to the known race condition between choosing a port and having the
    // proxy bind it.
    return startProxy(cfg);
  }
	

  

  @After public void stopMac() throws Exception {
    if (null != proxyProcess) {
      log.info("Destroying proxy process");
      proxyProcess.destroy();
      log.info("Waiting for proxy termination");
      proxyProcess.waitFor();
      log.info("Proxy terminated");
    }
    if (null != mac) {
      mac.stop();
    }
  }

	

  

  @Test public void testProxyClient() throws Exception {
    ClusterUser rootUser = kdc.getRootUser();
    UserGroupInformation ugi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(rootUser.getPrincipal(), rootUser.getKeytab().getAbsolutePath());
    TSocket socket = new TSocket(hostname, proxyPort);
    log.info("Connecting to proxy with server primary \'" + proxyPrimary + "\' running on " + hostname);
    TSaslClientTransport transport = new TSaslClientTransport("GSSAPI", null, proxyPrimary, hostname, Collections.singletonMap("javax.security.sasl.qop", "auth"), null, socket);
    final UGIAssumingTransport ugiTransport = new UGIAssumingTransport(transport, ugi);
    ugiTransport.open();
    AccumuloProxy.Client.Factory factory = new AccumuloProxy.Client.Factory();
    Client client = factory.getClient(new TCompactProtocol(ugiTransport), new TCompactProtocol(ugiTransport));
    ByteBuffer login = client.login(rootUser.getPrincipal(), Collections.<String, String>emptyMap());
    String table = "table";
    if (!client.tableExists(login, table)) {
      client.createTable(login, table, true, TimeType.MILLIS);
    }
    String writer = client.createWriter(login, table, new WriterOptions());
    Map<ByteBuffer, List<ColumnUpdate>> updates = new HashMap<>();
    ColumnUpdate update = new ColumnUpdate(ByteBuffer.wrap("cf1".getBytes(UTF_8)), ByteBuffer.wrap("cq1".getBytes(UTF_8)));
    update.setValue(ByteBuffer.wrap("value1".getBytes(UTF_8)));
    updates.put(ByteBuffer.wrap("row1".getBytes(UTF_8)), Collections.<ColumnUpdate>singletonList(update));
    update = new ColumnUpdate(ByteBuffer.wrap("cf2".getBytes(UTF_8)), ByteBuffer.wrap("cq2".getBytes(UTF_8)));
    update.setValue(ByteBuffer.wrap("value2".getBytes(UTF_8)));
    updates.put(ByteBuffer.wrap("row2".getBytes(UTF_8)), Collections.<ColumnUpdate>singletonList(update));
    client.update(writer, updates);
    client.flush(writer);
    client.closeWriter(writer);
    String scanner = client.createScanner(login, table, new ScanOptions());
    ScanResult results = client.nextK(scanner, 10);
    assertEquals(2, results.getResults().size());
    KeyValue kv = results.getResults().get(0);
    Key k = kv.key;
    ByteBuffer v = kv.value;
    assertEquals(ByteBuffer.wrap("row1".getBytes(UTF_8)), k.row);
    assertEquals(ByteBuffer.wrap("cf1".getBytes(UTF_8)), k.colFamily);
    assertEquals(ByteBuffer.wrap("cq1".getBytes(UTF_8)), k.colQualifier);
    assertEquals(ByteBuffer.wrap(new byte[0]), k.colVisibility);
    assertEquals(ByteBuffer.wrap("value1".getBytes(UTF_8)), v);
    kv = results.getResults().get(1);
    k = kv.key;
    v = kv.value;
    assertEquals(ByteBuffer.wrap("row2".getBytes(UTF_8)), k.row);
    assertEquals(ByteBuffer.wrap("cf2".getBytes(UTF_8)), k.colFamily);
    assertEquals(ByteBuffer.wrap("cq2".getBytes(UTF_8)), k.colQualifier);
    assertEquals(ByteBuffer.wrap(new byte[0]), k.colVisibility);
    assertEquals(ByteBuffer.wrap("value2".getBytes(UTF_8)), v);
    client.closeScanner(scanner);
    ugiTransport.close();
  }

	

  

  @Test public void testDisallowedClientForImpersonation() throws Exception {
    String user = testName.getMethodName();
    File keytab = new File(kdc.getKeytabDir(), user + ".keytab");
    kdc.createPrincipal(keytab, user);
    UserGroupInformation ugi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(user, keytab.getAbsolutePath());
    log.info("Logged in as " + ugi);
    thrown.expect(AccumuloSecurityException.class);
    thrown.expect(new ThriftExceptionMatchesPattern(".*Error BAD_CREDENTIALS.*"));
    thrown.expect(new ThriftExceptionMatchesPattern(".*Expected \'" + proxyPrincipal + "\' but was \'" + kdc.qualifyUser(user) + "\'.*"));
    TSocket socket = new TSocket(hostname, proxyPort);
    log.info("Connecting to proxy with server primary \'" + proxyPrimary + "\' running on " + hostname);
    TSaslClientTransport transport = new TSaslClientTransport("GSSAPI", null, proxyPrimary, hostname, Collections.singletonMap("javax.security.sasl.qop", "auth"), null, socket);
    final UGIAssumingTransport ugiTransport = new UGIAssumingTransport(transport, ugi);
    ugiTransport.open();
    AccumuloProxy.Client.Factory factory = new AccumuloProxy.Client.Factory();
    Client client = factory.getClient(new TCompactProtocol(ugiTransport), new TCompactProtocol(ugiTransport));
    try {
      client.login(kdc.qualifyUser(user), Collections.<String, String>emptyMap());
    }  finally {
      if (null != ugiTransport) {
        ugiTransport.close();
      }
    }
  }

	

  

  @Test public void testMismatchPrincipals() throws Exception {
    ClusterUser rootUser = kdc.getRootUser();
    thrown.expect(AccumuloSecurityException.class);
    thrown.expect(new ThriftExceptionMatchesPattern(ProxyServer.RPC_ACCUMULO_PRINCIPAL_MISMATCH_MSG));
    String user = testName.getMethodName();
    File keytab = new File(kdc.getKeytabDir(), user + ".keytab");
    kdc.createPrincipal(keytab, user);
    UserGroupInformation ugi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(user, keytab.getAbsolutePath());
    log.info("Logged in as " + ugi);
    TSocket socket = new TSocket(hostname, proxyPort);
    log.info("Connecting to proxy with server primary \'" + proxyPrimary + "\' running on " + hostname);
    TSaslClientTransport transport = new TSaslClientTransport("GSSAPI", null, proxyPrimary, hostname, Collections.singletonMap("javax.security.sasl.qop", "auth"), null, socket);
    final UGIAssumingTransport ugiTransport = new UGIAssumingTransport(transport, ugi);
    ugiTransport.open();
    AccumuloProxy.Client.Factory factory = new AccumuloProxy.Client.Factory();
    Client client = factory.getClient(new TCompactProtocol(ugiTransport), new TCompactProtocol(ugiTransport));
    try {
      client.login(rootUser.getPrincipal(), Collections.<String, String>emptyMap());
    }  finally {
      if (null != ugiTransport) {
        ugiTransport.close();
      }
    }
  }

	

  private static  class  ThriftExceptionMatchesPattern  extends TypeSafeMatcher<AccumuloSecurityException> {
		
    

  private String pattern;

		

    

  public ThriftExceptionMatchesPattern(String pattern) {
    this.pattern = pattern;
  }

		

    

  @Override protected boolean matchesSafely(AccumuloSecurityException item) {
    return item.isSetMsg() && item.msg.matches(pattern);
  }

		

    

  @Override public void describeTo(Description description) {
    description.appendText("matches pattern ").appendValue(pattern);
  }

		

    

  @Override protected void describeMismatchSafely(AccumuloSecurityException item, Description mismatchDescription) {
    mismatchDescription.appendText("does not match");
  }


	}

}

