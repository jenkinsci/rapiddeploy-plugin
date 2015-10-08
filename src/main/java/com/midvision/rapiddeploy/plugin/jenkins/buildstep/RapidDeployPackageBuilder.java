package com.midvision.rapiddeploy.plugin.jenkins.buildstep;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.midvision.rapiddeploy.connector.RapidDeployConnector;

public class RapidDeployPackageBuilder extends Builder {

	private final String serverUrl;
	private final String authenticationToken;
	private final String project;
	private final boolean enableCustomPackageName;
	private final String packageName;
	private final String archiveExtension;

	private static final Log logger = LogFactory.getLog(RapidDeployPackageBuilder.class);

	@DataBoundConstructor
	public RapidDeployPackageBuilder(String serverUrl, String authenticationToken, String project, boolean enableCustomPackageName, String packageName,
			String archiveExtension) {
		super();
		this.serverUrl = serverUrl;
		this.authenticationToken = authenticationToken;
		this.project = project;
		this.enableCustomPackageName = enableCustomPackageName;
		this.packageName = packageName;
		this.archiveExtension = archiveExtension;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
		listener.getLogger().println("Invoking RapidDeploy deployment package builder...");
		listener.getLogger().println("  > Server URL: " + serverUrl);
		listener.getLogger().println("  > Project: " + project);
		listener.getLogger().println("  > Package name: " + packageName);
		listener.getLogger().println("  > Archive extension: " + archiveExtension);
		try {
			RapidDeployConnector.invokeRapidDeployBuildPackage(getAuthenticationToken(), getServerUrl(), getProject(), getPackageName(), getArchiveExtension(),
					true);
			listener.getLogger().println("Package build successfully requested!");
			return true;
		} catch (Exception e) {
			listener.getLogger().println("Call failed with error: " + e.getMessage());
			return false;
		}
	}

	public String getProject() {
		return project;
	}

	public String getServerUrl() {
		return serverUrl;
	}

	public String getAuthenticationToken() {
		return authenticationToken;
	}

	public boolean getEnableCustomPackageName() {
		return enableCustomPackageName;
	}

	public String getPackageName() {
		if (enableCustomPackageName) {
			return packageName;
		} else {
			return "";
		}
	}

	public String getArchiveExtension() {
		return archiveExtension;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	/**
	 * Descriptor for {@link RapidDeployPackageBuilder}. Used as a singleton.
	 * The class is marked as public so that it can be accessed from views.
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		final private static String NOT_EMPTY_MESSAGE = "Please set a value for this field!";
		final private static String NO_PROTOCOL_MESSAGE = "Please specify a protocol for the URL, e.g. \"http://\".";
		final private static String CONNECTION_BAD_MESSAGE = "Unable to establish connection.";
		final private static String WRONG_PROJECT_MESSAGE = "Wrong project selected, please reload the projects list.";

		private List<String> projects;
		private boolean newConnection = true;

		public DescriptorImpl() {
			super(RapidDeployPackageBuilder.class);
			load();
		}

		@SuppressWarnings("rawtypes")
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		@Override
		public String getDisplayName() {
			return "RapidDeploy deployment package build";
		}

		/** SERVER URL FIELD **/

		public FormValidation doCheckServerUrl(@QueryParameter String value) throws IOException, ServletException {
			logger.debug("doCheckServerUrl");
			newConnection = true;
			if (value.length() == 0) {
				return FormValidation.error(NOT_EMPTY_MESSAGE);
			} else if (!value.startsWith("http://") && !value.startsWith("https://")) {
				return FormValidation.warning(NO_PROTOCOL_MESSAGE);
			}
			return FormValidation.ok();
		}

		/** AUTHENTICATION TOKEN FIELD **/

		public FormValidation doCheckAuthenticationToken(@QueryParameter String value) throws IOException, ServletException {
			logger.debug("doCheckAuthenticationToken");
			newConnection = true;
			if (value.length() == 0) {
				return FormValidation.error(NOT_EMPTY_MESSAGE);
			}
			return FormValidation.ok();
		}

		/** LOAD PROJECTS BUTTON **/

		public FormValidation doLoadProjects(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken) throws IOException, ServletException {
			logger.debug("doLoadProjects");
			newConnection = true;
			if (getProjects(serverUrl, authenticationToken).isEmpty()) {
				return FormValidation.error(CONNECTION_BAD_MESSAGE);
			}
			return FormValidation.ok();
		}

		/** PROJECT FIELD **/

		public ListBoxModel doFillProjectItems(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken) {
			logger.debug("doFillProjectItems");
			ListBoxModel items = new ListBoxModel();
			for (String projectName : getProjects(serverUrl, authenticationToken)) {
				items.add(projectName);
			}
			return items;
		}

		/** SHOW PACKAGES BUTTON **/

		public FormValidation doGetPackages(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken, @QueryParameter("project") final String project) throws IOException,
				ServletException {
			logger.debug("doGetPackages");
			if (getProjects(serverUrl, authenticationToken).contains(project)) {
				List<String> packageNames = new ArrayList<String>();
				try {
					packageNames = RapidDeployConnector.invokeRapidDeployListPackages(authenticationToken, serverUrl, project);
				} catch (Exception e) {
					logger.warn(e.getMessage());
				}
				if (!packageNames.isEmpty()) {
					String packageList = "<table>";
					int index = 0;
					int limit = 10;
					for (String packageName : packageNames) {
						if (!"null".equals(packageName) && !packageName.startsWith("Deployment")) {
							packageList += "<tr><td class=\"setting-main\">";
							packageList += packageName;
							packageList += "</td></tr>";
							index++;
							if (index >= limit) {
								break;
							}
						}
					}
					packageList += "</table>";
					return FormValidation.okWithMarkup(packageList);
				}
			} else if (getProjects(serverUrl, authenticationToken).isEmpty()) {
				return FormValidation.error(CONNECTION_BAD_MESSAGE);
			} else {
				return FormValidation.warning(WRONG_PROJECT_MESSAGE);
			}
			return FormValidation.warning("No deployment package could be retrieved for '" + project + "'.");
		}

		/** ARCHIVE EXTENSION FIELD **/

		public ListBoxModel doFillArchiveExtensionItems() {
			logger.debug("doFillArchiveExtensionItems");
			ListBoxModel items = new ListBoxModel();
			items.add("jar");
			items.add("war");
			items.add("ear");
			items.add("tar");
			items.add("rar");
			items.add("zip");
			return items;
		}

		/** AUX **/

		/** Method to cache the projects to ease the form validation **/
		private synchronized List<String> getProjects(final String serverUrl, final String authenticationToken) {
			logger.debug("getProjects");
			if (projects == null || projects.isEmpty() || newConnection) {
				try {
					if (serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken)) {
						logger.debug("REQUEST TO WEB SERVICE GET PROJECTS...");
						projects = RapidDeployConnector.invokeRapidDeployListProjects(authenticationToken, serverUrl);
						newConnection = false;
						logger.debug("PROJECTS RETRIEVED: " + projects.size());
					} else {
						projects = new ArrayList<String>();
					}
				} catch (Exception e) {
					logger.warn(e.getMessage());
					projects = new ArrayList<String>();
				}
			}
			logger.debug("PROJECTS: " + projects.size());
			return projects;
		}
	}
}
