/**
 * (C) Copyright IBM Corporation 2017, 2020.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openliberty.tools.common.arquillian.util;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Properties;

import org.junit.Test;
import org.xml.sax.SAXParseException;

import io.openliberty.tools.common.arquillian.util.ArquillianConfigurationException;
import io.openliberty.tools.common.arquillian.util.HttpPortUtil;

public class HttpPortUtilTest {
	
	private static final String SERVER_XML_BEGIN = 
			"<!-- Copyright (c) 2015 IBM Corp. Licensed under the Apache License, Version " +
			"	2.0 (the \"License\"); you may not use this file except in compliance with " +
			"	the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 " +
			"	Unless required by applicable law or agreed to in writing, software distributed " +
			"	under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES " +
			"	OR CONDITIONS OF ANY KIND, either express or implied. See the License for " +
			"	the specific language governing permissions and limitations under the License. -->" +
			"<server description=\"Sample Liberty server\">" +
			"	<!-- The features installed by the Liberty application accelerator will " +
			"		not appear here. Once the application is built the configured features can " +
			"		be found in the configDropins/defaults folder in the target/wlp folder. -->" +
			"	<!-- To extend the feature list either add the app accelerator Maven dependencies, " +
			"		add the features below or put a server.xml file in configDropins/overrides. " +
			"		Note: you may need to add Maven provided dependencies for use at compile " +
			"		time if not using app accelerator dependencies. -->" +
			"	<featureManager>" +
			"		<feature>localConnector-1.0</feature>" +
			"		<feature>jpa-2.1</feature>" +
			"		<feature>jaxrs-2.0</feature>" +
			"		<feature>ejbLite-3.2</feature>" +
			"	</featureManager>" +
			"	<dataSource id=\"mydb\" jndiName=\"jdbc/mydb\"" +
			"		type=\"javax.sql.ConnectionPoolDataSource\">" +
			"		<jdbcDriver javax.sql.ConnectionPoolDataSource=\"org.h2.jdbcx.JdbcDataSource\"" +
			"			javax.sql.DataSource=\"org.h2.jdbcx.JdbcDataSource\"" +
			"			javax.sql.XADataSource=\"org.h2.jdbcx.JdbcDataSource\" libraryRef=\"SharedLibrary_H2\" />" +
			"		<properties URL=\"jdbc:h2:~/liberty-crypto-db;DB_CLOSE_ON_EXIT=FALSE\"" +
			"			user=\"\" password=\"sa\" />" +
			"	</dataSource>" +
			"	<library id=\"SharedLibrary_H2\">" +
			"		<fileset dir=\"${shared.resource.dir}/h2\" id=\"Fileset_H2\" />" +
			"	</library>" +
			"	<cors domain=\"/myLibertyApp\" allowedOrigins=\"http://localhost:8081\"" +
			"		allowedMethods=\"GET, POST, PUT, DELETE, OPTIONS, HEAD\" allowedHeaders=\"origin, content-type, accept, authorization\"" +
			"		allowCredentials=\"true\" maxAge=\"1209600\" />";
	
	private static final String SERVER_XML_END = 
			"	<cdi12 enableImplicitBeanArchives=\"false\" />" +
			"</server>";
	
	private static final String BOOTSTRAP_PROPERTIES_PORTS = 
			"	<httpEndpoint httpPort=\"${default.httpPort}\" httpsPort=\"${default.https.port}\"" +
			"		id=\"defaultHttpEndpoint\" />";
	
	private static final String INTEGER_PORTS = 
			"	<httpEndpoint httpPort=\"9081\" httpsPort=\"9444\"" +
			"		id=\"defaultHttpEndpoint\" />";
	
	private static final String CONFIG_VARIABLE_XML = 
                        "<server><variable name=\"default.httpPort\" value=\"9084\"/></server>";
	
	private static final String CONFIG_VARIABLE_DEFAULT_XML = 
                        "<server><variable name=\"default.httpPort\" defaultValue=\"9084\"/></server>";
		
	private static final String CONFIG_VARIABLE_INVALID_XML = 
                        "<server><variable name=\"default.httpPort\" value=\"invalid\"/></server>";
	
	private static final String CONFIG_VARIABLE_NO_MATCH_XML = 
                        "<server><variable name=\"random\" value=\"random\"/></server>";
	
	@Test
	public void testDefaultHttpPort() throws Exception {
		String serverXML = SERVER_XML_BEGIN + SERVER_XML_END;
		assertTrue(HttpPortUtil.getHttpPortForServerXML(serverXML, null, null) == HttpPortUtil.DEFAULT_PORT);
	}
	
	@Test
	public void testHttpPortSetFromServerXML() throws Exception {
		String serverXML = SERVER_XML_BEGIN + INTEGER_PORTS +  SERVER_XML_END;
		assertTrue(HttpPortUtil.getHttpPortForServerXML(serverXML, null, null) == 9081);
	}
	
	@Test
	public void testHttpPortSetFromBootstrapProperties() throws Exception {
		String serverXML = SERVER_XML_BEGIN + BOOTSTRAP_PROPERTIES_PORTS +  SERVER_XML_END;
		Properties bootstrapProperties = new Properties();
		bootstrapProperties.setProperty("default.httpPort", "9082");
		assertTrue(HttpPortUtil.getHttpPortForServerXML(serverXML, bootstrapProperties, null) == 9082);
	}
	
	@Test
	public void testHttpPortSetFromConfigVariableXML() throws Exception {
		String serverXML = SERVER_XML_BEGIN + BOOTSTRAP_PROPERTIES_PORTS +  SERVER_XML_END;
		Properties bootstrapProperties = new Properties();
		bootstrapProperties.setProperty("default.httpPort", "9082");
		assertTrue(HttpPortUtil.getHttpPortForServerXML(serverXML, bootstrapProperties, CONFIG_VARIABLE_DEFAULT_XML) == 9084);
	}
	
	@Test
	public void testHttpPortSetFromConfigVariableXMLNoMatch() throws Exception {
		String serverXML = SERVER_XML_BEGIN + BOOTSTRAP_PROPERTIES_PORTS +  SERVER_XML_END;
		Properties bootstrapProperties = new Properties();
		bootstrapProperties.setProperty("default.httpPort", "9082");
		assertTrue(HttpPortUtil.getHttpPortForServerXML(serverXML, bootstrapProperties, CONFIG_VARIABLE_NO_MATCH_XML) == 9082);
	}
	
	@Test
	public void testHttpPortSetFromConfigVariableXMLOnly() throws Exception {
		String serverXML = SERVER_XML_BEGIN + BOOTSTRAP_PROPERTIES_PORTS +  SERVER_XML_END;
		assertTrue(HttpPortUtil.getHttpPortForServerXML(serverXML, null, CONFIG_VARIABLE_XML) == 9084);
	}
	
	@Test(expected = ArquillianConfigurationException.class)
	public void testHttpPortSetInvalidFromConfigVariableXML() throws Exception {
		String serverXML = SERVER_XML_BEGIN + BOOTSTRAP_PROPERTIES_PORTS +  SERVER_XML_END;
		HttpPortUtil.getHttpPortForServerXML(serverXML, null, CONFIG_VARIABLE_INVALID_XML);
	}
	
	@Test(expected = ArquillianConfigurationException.class)
	public void testHttpPortSetInvalidFromBootstrapProperties() throws Exception {
		String serverXML = SERVER_XML_BEGIN + BOOTSTRAP_PROPERTIES_PORTS +  SERVER_XML_END;
		Properties bootstrapProperties = new Properties();
		bootstrapProperties.setProperty("default.httpPort", "invalid");
		HttpPortUtil.getHttpPortForServerXML(serverXML, bootstrapProperties, null);
	}
	
	@Test(expected = ArquillianConfigurationException.class)
	public void testHttpPortErrorFromBootstrapProperties() throws Exception {
		String serverXML = SERVER_XML_BEGIN + BOOTSTRAP_PROPERTIES_PORTS +  SERVER_XML_END;
		HttpPortUtil.getHttpPortForServerXML(serverXML, null, null);
	}
	
	@Test(expected = FileNotFoundException.class)
	public void testMissingServerXMLFile() throws Exception {
		HttpPortUtil.getHttpPort(new File("somethingThatIsMissing"), null, null);
	}
	
	@Test(expected = SAXParseException.class)
	public void testInvalidServerXMLFile() throws Exception {
		HttpPortUtil.getHttpPortForServerXML(SERVER_XML_BEGIN, null, null);
	}

}
