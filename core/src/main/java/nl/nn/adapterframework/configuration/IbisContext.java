/*
   Copyright 2013, 2016-2019 Nationale-Nederlanden

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.configuration;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import nl.nn.adapterframework.configuration.classloaders.BasePathClassLoader;
import nl.nn.adapterframework.core.Adapter;
import nl.nn.adapterframework.core.IAdapter;
import nl.nn.adapterframework.extensions.graphviz.GraphvizEngine;
import nl.nn.adapterframework.http.RestServiceDispatcher;
import nl.nn.adapterframework.jdbc.migration.Migrator;
import nl.nn.adapterframework.lifecycle.IbisApplicationContext;
import nl.nn.adapterframework.receivers.JavaListener;
import nl.nn.adapterframework.util.AppConstants;
import nl.nn.adapterframework.util.ClassUtils;
import nl.nn.adapterframework.util.DateUtils;
import nl.nn.adapterframework.util.FlowDiagram;
import nl.nn.adapterframework.util.JdbcUtil;
import nl.nn.adapterframework.util.LogUtil;
import nl.nn.adapterframework.util.MessageKeeper;
import nl.nn.adapterframework.util.MessageKeeperMessage;

/**
 * Main entry point for creating and starting Ibis instances from
 * the configuration file.
 *
 * This class can not be created from the Spring context, because it
 * is the place where the Spring context is created.
 *
 *
 *
 * @author Tim van der Leeuw
 * @author Jaco de Groot
 * @since 4.8
 */
public class IbisContext extends IbisApplicationContext {
	private final static Logger LOG = LogUtil.getLogger(IbisContext.class);

	private final static Logger secLog = LogUtil.getLogger("SEC");

	private static final String INSTANCE_NAME = APP_CONSTANTS.getResolvedProperty("instance.name");
	private static final String APPLICATION_SERVER_TYPE_PROPERTY = "application.server.type";
	private static final long UPTIME = System.currentTimeMillis();

	static {
		String applicationServerType = System.getProperty(
				APPLICATION_SERVER_TYPE_PROPERTY);
		if (StringUtils.isNotEmpty(applicationServerType)) {
			if (applicationServerType.equalsIgnoreCase("WAS5")
					|| applicationServerType.equalsIgnoreCase("WAS6")) {
				ConfigurationWarnings configWarnings = ConfigurationWarnings
						.getInstance();
				String msg = "implementing value [" + applicationServerType
						+ "] of property [" + APPLICATION_SERVER_TYPE_PROPERTY
						+ "] as [WAS]";
				configWarnings.add(LOG, msg);
				System.setProperty(APPLICATION_SERVER_TYPE_PROPERTY, "WAS");
			} else if (applicationServerType.equalsIgnoreCase("TOMCAT6")) {
				ConfigurationWarnings configWarnings = ConfigurationWarnings
						.getInstance();
				String msg = "implementing value [" + applicationServerType
						+ "] of property [" + APPLICATION_SERVER_TYPE_PROPERTY
						+ "] as [TOMCAT]";
				configWarnings.add(LOG, msg);
				System.setProperty(APPLICATION_SERVER_TYPE_PROPERTY, "TOMCAT");
			}
		}
		if(!Boolean.parseBoolean(AppConstants.getInstance().getProperty("jdbc.convertFieldnamesToUppercase")))
			ConfigurationWarnings.getInstance().add(LOG, "DEPRECATED: jdbc.convertFieldnamesToUppercase is set to false, please set to true. XML field definitions of SQL senders will be uppercased!");

		String loadFileSuffix = AppConstants.getInstance().getProperty("ADDITIONAL.PROPERTIES.FILE.SUFFIX");
		if (StringUtils.isNotEmpty(loadFileSuffix))
			ConfigurationWarnings.getInstance().add(LOG, "DEPRECATED: SUFFIX [_"+loadFileSuffix+"] files are deprecated, property files are now inherited from their parent!");
	}

	private IbisManager ibisManager;
	private Map<String, MessageKeeper> messageKeepers = new HashMap<String, MessageKeeper>();
	private int messageKeeperSize = 10;
	private FlowDiagram flowDiagram;
	private ClassLoaderManager classLoaderManager = null;
	private static List<String> loadingConfigs = new ArrayList<String>();

	public void setDefaultApplicationServerType(String defaultApplicationServerType) {
		if (defaultApplicationServerType.equals(getApplicationServerType())) {
			ConfigurationWarnings configWarnings = ConfigurationWarnings
					.getInstance();
			String msg = "property ["
					+ APPLICATION_SERVER_TYPE_PROPERTY
					+ "] already has a default value ["
					+ defaultApplicationServerType + "]";
			configWarnings.add(LOG, msg);
		} else if (StringUtils.isEmpty(getApplicationServerType())) {
			// Resolve application.server.type in ServerSpecifics*.properties, SideSpecifics*.properties and StageSpecifics*.properties filenames
			APP_CONSTANTS.putAdditionalPropertiesFilesSubstVarsProperty(APPLICATION_SERVER_TYPE_PROPERTY, defaultApplicationServerType);
			// Resolve application.server.type in spring.xml filenames
			APP_CONSTANTS.putPropertyPlaceholderConfigurerProperty(APPLICATION_SERVER_TYPE_PROPERTY, defaultApplicationServerType);
		}
	}

	public static String getApplicationServerType() {
		return APP_CONSTANTS.getResolvedProperty(APPLICATION_SERVER_TYPE_PROPERTY);
	}

	/**
	 * Creates the Spring context, and load the configuration. Optionally  with
	 * a specific ClassLoader which might for example override the getResource
	 * method to load configuration and related resources from a different
	 * location from the standard classpath. In case basePath is not null the
	 * ClassLoader is wrapped in {@link BasePathClassLoader} to make it possible
	 * to reference resources in the configuration relative to the configuration
	 * file and have an extra resource override (resource is first resolved
	 * relative to the configuration, when not found it is resolved by the
	 * original ClassLoader.
	 * 
	 * @see ClassUtils#getResourceURL(ClassLoader, String)
	 * @see AppConstants#getInstance(ClassLoader)
	 */
	public void init() {
		init(true);
	}

	/**
	 * Creates the Spring context, and load the configuration. Optionally  with
	 * a specific ClassLoader which might for example override the getResource
	 * method to load configuration and related resources from a different
	 * location from the standard classpath. In case basePath is not null the
	 * ClassLoader is wrapped in {@link BasePathClassLoader} to make it possible
	 * to reference resources in the configuration relative to the configuration
	 * file and have an extra resource override (resource is first resolved
	 * relative to the configuration, when not found it is resolved by the
	 * original ClassLoader.
	 * 
	 * @see ClassUtils#getResourceURL(ClassLoader, String)
	 * @see AppConstants#getInstance(ClassLoader)
	 *
	 * @param reconnect retry startup when failures occur
	 */
	public synchronized void init(boolean reconnect) {
		try {
			long start = System.currentTimeMillis();

			LOG.info("Attempting to start IBIS application");
			createApplicationContext();
			LOG.debug("Created Ibis Application Context");

			MessageKeeper messageKeeper = new MessageKeeper();
			messageKeepers.put("*ALL*", messageKeeper);

			ibisManager = (IbisManager) getBean("ibisManager");
			ibisManager.setIbisContext(this);
			LOG.debug("Loaded IbisManager Bean");
			classLoaderManager = new ClassLoaderManager(this);

			AbstractSpringPoweredDigesterFactory.setIbisContext(this);

			try {
				flowDiagram = new FlowDiagram();
			} catch (IllegalStateException e) {
				log(null, null, "failed to initalize GraphVizEngine", MessageKeeperMessage.ERROR_LEVEL, e, true);
			}

			load();
			getMessageKeeper().setMaxSize(Math.max(messageKeeperSize, getMessageKeeper().size()));

			log("startup in " + (System.currentTimeMillis() - start) + " ms");
		}
		catch (Exception e) {
			//Catch all exceptions, the IBIS failed to startup...
			LOG.error("Failed to initialize IbisContext, retrying in 1 minute!", e);

			if(reconnect) {
				ibisContextReconnectThread = new Thread(new IbisContextRunnable(this));
				ibisContextReconnectThread.setName("ibisContextReconnectThread");
				ibisContextReconnectThread.start();
			}
		}
	}

	Thread ibisContextReconnectThread = null;

	/**
	 * Shuts down the IbisContext, and therefore the Spring context
	 * 
	 * @see #destroyApplicationContext()
	 */
	public synchronized void destroy() {
		long start = System.currentTimeMillis();
		if(ibisManager != null)
			ibisManager.shutdown();
		if(ibisContextReconnectThread != null)
			ibisContextReconnectThread.interrupt();
		destroyApplicationContext();
		log("shutdown in " + (System.currentTimeMillis() - start) + " ms");
	}

	public synchronized void reload(String configurationName) {
		unload(configurationName);
		load(configurationName);
	}

	public void unload(String configurationName) {
		Configuration configuration = ibisManager.getConfiguration(configurationName);
		if (configuration != null) {
			long start = System.currentTimeMillis();
			ibisManager.unload(configurationName);
			if (configuration.getAdapterService().getAdapters().size() > 0) {
				log("Not all adapters are unregistered: "
						+ configuration.getAdapterService().getAdapters(),
						MessageKeeperMessage.ERROR_LEVEL);
			}
			// Improve configuration reload performance. Probably because
			// garbage collection will be easier.
			configuration.setAdapterService(null);
			String configurationVersion = configuration.getVersion();
			String msg = "unload in " + (System.currentTimeMillis() - start) + " ms";
			log(configurationName, configurationVersion, msg);
			secLog.info("Configuration [" + configurationName + "] [" + configurationVersion+"] " + msg);
		} else {
			log("Configuration [" + configurationName + "] to unload not found",
					MessageKeeperMessage.WARN_LEVEL);
		}
		JdbcUtil.resetJdbcProperties();
	}

	/**
	 * Completely rebuilds the ibisContext and therefore also the Spring context
	 * 
	 * @see #destroy()
	 * @see #init()
	 */
	public synchronized void fullReload() {
		if (isLoadingConfigs()) {
			log("Skipping fullReload because one or more configurations are currently loading",
					MessageKeeperMessage.WARN_LEVEL);
			return;
		}

		destroy();
		Set<String> javaListenerNames = JavaListener.getListenerNames();
		if (javaListenerNames.size() > 0) {
			log("Not all java listeners are unregistered: " + javaListenerNames,
					MessageKeeperMessage.ERROR_LEVEL);
		}
		Set uriPatterns = RestServiceDispatcher.getInstance().getUriPatterns();
		if (uriPatterns.size() > 0) {
			log("Not all rest listeners are unregistered: " + uriPatterns,
					MessageKeeperMessage.ERROR_LEVEL);
		}
		Set mbeans = JmxMbeanHelper.getMBeans();
		if (mbeans != null && mbeans.size() > 0) {
			log("Not all JMX MBeans are unregistered: " + mbeans,
					MessageKeeperMessage.ERROR_LEVEL);
		}
		JdbcUtil.resetJdbcProperties();

		init();
	}

	/**
	 * Load all registered configurations
	 * @see #load(String)
	 */
	public void load() {
		try {
			loadingConfigs.add("*ALL*");
			load(null);
		} finally {
			loadingConfigs.remove("*ALL*");
		}
	}

	/**
	 * Loads, digests and starts the specified configuration, or all configurations
	 * 
	 * @param configurationName name of the configuration to load or null when you want to load all configurations
	 * 
	 * @see ClassLoaderManager#get(String)
	 * @see ConfigurationUtils#retrieveAllConfigNames(IbisContext)
	 * @see #digestClassLoaderConfiguration(ClassLoader, ConfigurationDigester, String, ConfigurationException)
	 */
	public void load(String configurationName) {
		boolean configFound = false;

		//We have an ordered list with all configurations, lets loop through!
		ConfigurationDigester configurationDigester = new ConfigurationDigester();

		Map<String, String> allConfigNamesItems = ConfigurationUtils.retrieveAllConfigNames(this);
		for (Entry<String, String> currentConfigNameItem : allConfigNamesItems.entrySet()) {
			String currentConfigurationName = currentConfigNameItem.getKey();
			String classLoaderType = currentConfigNameItem.getValue();

			if (configurationName == null || configurationName.equals(currentConfigurationName)) {
				configFound = true;

				ConfigurationException customClassLoaderConfigurationException = null;
				ClassLoader classLoader = null;
				try {
					classLoader = classLoaderManager.get(currentConfigurationName, classLoaderType);

					//An error occurred but we don't want to throw any exceptions.
					//Skip configuration digesting so it can be done at a later time.
					if(classLoader == null)
						continue;

				} catch (ConfigurationException e) {
					customClassLoaderConfigurationException = e;
				}

				try {
					loadingConfigs.add(currentConfigurationName);
					digestClassLoaderConfiguration(classLoader, configurationDigester, currentConfigurationName, customClassLoaderConfigurationException);
				} finally {
					loadingConfigs.remove(currentConfigurationName);
				}
			}
		}

		generateFlow();
		//Check if the configuration we try to reload actually exists
		if (!configFound) {
			log(configurationName, configurationName + " not found in ["+allConfigNamesItems.keySet().toString()+"]", MessageKeeperMessage.ERROR_LEVEL);
		}
	}

	public String getConfigurationFile(String currentConfigurationName) {
		String configurationFile = APP_CONSTANTS.getResolvedProperty(
				"configurations." + currentConfigurationName + ".configurationFile");
		if (configurationFile == null) {
			configurationFile = "Configuration.xml";
			if (!currentConfigurationName.equals(INSTANCE_NAME)) {
				configurationFile = currentConfigurationName + "/" + configurationFile;
			}
		}
		return configurationFile;
	}

	private void digestClassLoaderConfiguration(ClassLoader classLoader, 
			ConfigurationDigester configurationDigester, 
			String currentConfigurationName, 
			ConfigurationException customClassLoaderConfigurationException) {

		long start = System.currentTimeMillis();
		try {
			if(classLoader != null)
				classLoaderManager.reload(classLoader);
		} catch (ConfigurationException e) {
			customClassLoaderConfigurationException = e;
		}

		String configurationFile = getConfigurationFile(currentConfigurationName);
		String currentConfigurationVersion =
				getConfigurationVersion(AppConstants.getInstance(classLoader));
		Configuration configuration = null;
		ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
		if (classLoader != null) {
			Thread.currentThread().setContextClassLoader(classLoader);
		}
		try {
			configuration = new Configuration(new BasicAdapterServiceImpl());
			configuration.setName(currentConfigurationName);
			configuration.setVersion(currentConfigurationVersion);
			configuration.setIbisManager(ibisManager);
			ibisManager.addConfiguration(configuration);
			ConfigurationWarnings.getInstance().setActiveConfiguration(configuration);
			if (customClassLoaderConfigurationException == null) {

				if(AppConstants.getInstance(classLoader).getBoolean("jdbc.migrator.active", false)) {
					try {
						Migrator databaseMigrator = (Migrator) getBean("jdbcMigrator");
						databaseMigrator.setIbisContext(this);
						databaseMigrator.configure(currentConfigurationName, classLoader);
						databaseMigrator.update();
						databaseMigrator.close();
					}
					catch (Exception e) {
						log(currentConfigurationName, currentConfigurationVersion, e.getMessage(), MessageKeeperMessage.ERROR_LEVEL);
					}
				}

				configurationDigester.digestConfiguration(classLoader, configuration, configurationFile);
				if (currentConfigurationVersion == null) {
					currentConfigurationVersion = configuration.getVersion();
				} else if (!currentConfigurationVersion.equals(configuration.getVersion())) {
					log(currentConfigurationName, currentConfigurationVersion,
							"configuration version doesn't match Configuration version attribute: "
							+ configuration.getVersion(),
							MessageKeeperMessage.WARN_LEVEL);
				}
				if (!currentConfigurationName.equals(configuration.getName())) {
					log(currentConfigurationName, currentConfigurationVersion,
							"configuration name doesn't match Configuration name attribute: "
							+ configuration.getName(),
							MessageKeeperMessage.WARN_LEVEL);
					messageKeepers.put(configuration.getName(),
							messageKeepers.remove(currentConfigurationName));
				}

				String msg;
				if (configuration.isAutoStart()) {
					ibisManager.startConfiguration(configuration);
					msg = "startup in " + (System.currentTimeMillis() - start) + " ms";
				}
				else {
					msg = "configured in " + (System.currentTimeMillis() - start) + " ms";
				}
				log(currentConfigurationName, currentConfigurationVersion, msg);
				secLog.info("Configuration [" + currentConfigurationName + "] [" + currentConfigurationVersion+"] " + msg);
				generateFlows(configuration, currentConfigurationName, currentConfigurationVersion);
			} else {
				throw customClassLoaderConfigurationException;
			}
		} catch (ConfigurationException e) {
			configuration.setConfigurationException(e);
			log(currentConfigurationName, currentConfigurationVersion, " exception",
					MessageKeeperMessage.ERROR_LEVEL, e);
		} finally {
			Thread.currentThread().setContextClassLoader(originalClassLoader);
			ConfigurationWarnings.getInstance().setActiveConfiguration(null);
		}
	}

	private void generateFlows(Configuration configuration,
			String currentConfigurationName, String currentConfigurationVersion) {
		if (flowDiagram != null) {
			List<IAdapter> registeredAdapters = configuration
					.getRegisteredAdapters();
			for (Iterator<IAdapter> adapterIt = registeredAdapters.iterator(); adapterIt.hasNext();) {
				Adapter adapter = (Adapter) adapterIt.next();
				try {
					flowDiagram.generate(adapter);
				} catch (Exception e) {
					log(currentConfigurationName, currentConfigurationVersion,
							"error generating flowDiagram for adapter ["
									+ adapter.getName() + "]",
							MessageKeeperMessage.WARN_LEVEL, e);
				}
			}

			try {
				flowDiagram.generate(configuration);
			} catch (Exception e) {
				log(currentConfigurationName, currentConfigurationVersion,
						"error generating flowDiagram for configuration ["
								+ configuration.getName() + "]",
						MessageKeeperMessage.WARN_LEVEL, e);
			}
		}
	}

	private void generateFlow() {
		if (flowDiagram != null) {
			List<Configuration> configurations = ibisManager.getConfigurations();
			try {
				flowDiagram.generate(configurations);
			} catch (Exception e) {
				log("*ALL*", null, "error generating flowDiagram", MessageKeeperMessage.WARN_LEVEL, e);
			}
		}
	}

	public void log(String message) {
		log(null, null, message, MessageKeeperMessage.INFO_LEVEL, null, true);
	}

	public void log(String message, String level) {
		log(null, null, message, level, null, true);
	}

	public void log(String configurationName, String configurationVersion, String message) {
		log(configurationName, configurationVersion, message, MessageKeeperMessage.INFO_LEVEL);
	}

	public void log(String configurationName, String configurationVersion, String message, String level) {
		log(configurationName, configurationVersion, message, level, null, false);
	}

	public void log(String configurationName, String configurationVersion, String message, String level, Exception e) {
		log(configurationName, configurationVersion, message, level, e, false);
	}

	private void log(String configurationName, String configurationVersion, String message, String level, Exception e, boolean allOnly) {
		String key;
		if (allOnly || configurationName == null) {
			key = "*ALL*";
		} else {
			key = configurationName;
		}
		MessageKeeper messageKeeper = messageKeepers.get(key);
		if (messageKeeper == null) {
			messageKeeper = new MessageKeeper(messageKeeperSize < 1 ? 1 : messageKeeperSize);
			messageKeepers.put(key, messageKeeper);
		}
		String m;
		String version;
		if (configurationName != null) {
			m = "Configuration [" + configurationName + "] ";
			version = configurationVersion;
		} else {
			m = "Application [" + INSTANCE_NAME + "] ";
			version = getApplicationVersion();
		}
		if (version != null) {
			m = m + "[" + version + "] ";
		}
		m = m + message;
		if (level.equals(MessageKeeperMessage.ERROR_LEVEL)) {
			LOG.info(m, e);
		} else if (level.equals(MessageKeeperMessage.WARN_LEVEL)) {
			LOG.warn(m, e);
		} else {
			LOG.info(m, e);
		}
		if (e != null) {
			m = m + ": " + e.getMessage();
		}
		messageKeeper.add(m, level);
		if (!allOnly) {
			log(configurationName, configurationVersion, message, level, e, true);
		}
	}

	/**
	 * Get MessageKeeper for a specific configuration. The MessageKeeper is not
	 * stored at the Configuration object instance to prevent messages being
	 * lost after configuration reload.
	 * @return MessageKeeper for '*ALL*' configurations
	 */
	public MessageKeeper getMessageKeeper() {
		return getMessageKeeper("*ALL*");
	}

	/**
	 * Get MessageKeeper for a specific configuration. The MessageKeeper is not
	 * stored at the Configuration object instance to prevent messages being
	 * lost after configuration reload.
	 * @param configurationName configuration name to get the MessageKeeper object from
	 * @return MessageKeeper for specified configurations
	 */
	public MessageKeeper getMessageKeeper(String configurationName) {
		return messageKeepers.get(configurationName);
	}

	public IbisManager getIbisManager() {
		return ibisManager;
	}

	public String getApplicationName() {
		return APP_CONSTANTS.getProperty("instance.name", null);
	}

	public String getApplicationVersion() {
		return getVersion(APP_CONSTANTS, "instance.version", "instance.build_id");
	}

	public String getFrameworkVersion() {
		return APP_CONSTANTS.getProperty("application.version", null);
	}

	public String getConfigurationVersion(Properties properties) {
		return getVersion(properties, "configuration.version", "configuration.timestamp");
	}

	public String getVersion(Properties properties, String versionKey, String timestampKey) {
		String version = null;
		if (StringUtils.isNotEmpty(properties.getProperty(versionKey))) {
			version = properties.getProperty(versionKey);
			if (StringUtils.isNotEmpty(properties.getProperty(timestampKey))) {
				version = version + "_" + properties.getProperty(timestampKey);
			}
		}
		return version;
	}
	
	public Date getUptimeDate() {
		return new Date(UPTIME);
	}
	
	public String getUptime() {
		return getUptime(DateUtils.FORMAT_GENERICDATETIME);
	}
	
	public String getUptime(String dateFormat) {
		return DateUtils.format(getUptimeDate(), dateFormat);
	}

	public boolean isLoadingConfigs() {
		return !loadingConfigs.isEmpty();
	}
	
	public static void main(String[] args) {
		IbisContext ibisContext = new IbisContext();
		ibisContext.init();
	}
}
