/**
 * Copyright (C) 2010-2014 Morgner UG (haftungsbeschr�nkt)
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.common;

import com.jayway.restassured.RestAssured;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.StructrConf;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.Services;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.GenericNode;
import org.structr.core.graph.GraphDatabaseCommand;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.RelationshipInterface;
import org.structr.core.property.PropertyMap;
import org.structr.files.ftp.FtpService;
import org.structr.module.JarConfigurationProvider;
import org.structr.rest.service.HttpService;
import org.structr.rest.servlet.JsonRestServlet;
import org.structr.web.auth.UiAuthenticator;
import org.structr.web.entity.User;
import org.structr.web.servlet.HtmlServlet;
import org.structr.websocket.servlet.WebSocketServlet;

//~--- classes ----------------------------------------------------------------
/**
 * Base class for all structr UI tests
 *
 * All tests are executed in superuser context
 *
 * @author Axel Morgner
 */
public class StructrUiTest extends TestCase {

	private static final Logger logger = Logger.getLogger(StructrUiTest.class.getName());

	//~--- fields ---------------------------------------------------------
	protected StructrConf config = new StructrConf();
	protected GraphDatabaseCommand graphDbCommand = null;
	protected SecurityContext securityContext = null;

	protected App app = null;

	// the jetty server
	private boolean running = false;
	protected String basePath;

	protected static final String prot = "http://";
//	protected static final String contextPath = "/";
	protected static final String restUrl = "/structr/rest";
	protected static final String htmlUrl = "/structr/html";
	protected static final String wsUrl = "/structr/ws";
	protected static final String host = "localhost";
	protected static final int httpPort = 8875;
	protected static final int ftpPort = 8876;

	protected static String baseUri;

	static {

		// check character set
		checkCharset();

		baseUri = prot + host + ":" + httpPort + htmlUrl + "/";
		// configure RestAssured
		RestAssured.basePath = restUrl;
		RestAssured.baseURI = prot + host + ":" + httpPort;
		RestAssured.port = httpPort;

	}

	//~--- methods --------------------------------------------------------
	@Override
	protected void setUp() throws Exception {
		setUp(null);
	}

	protected void setUp(final Map<String, Object> additionalConfig) {

