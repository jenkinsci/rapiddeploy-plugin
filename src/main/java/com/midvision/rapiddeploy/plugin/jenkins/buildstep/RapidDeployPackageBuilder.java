package com.midvision.rapiddeploy.plugin.jenkins.buildstep;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.midvision.rapiddeploy.connector.RapidDeployConnector;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class RapidDeployPackageBuilder extends Builder {

	private final String serverUrl;
	private final String authenticationToken;
	private final String project;
	private final boolean enableCustomPackageName;
	private String packageName;
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
		if (StringUtils.isNotBlank(packageName)) {
			packageName = replaceParametersPlaceholders(packageName, build, listener);
		}

		listener.getLogger().println("Invoking RapidDeploy deployment package builder...");
		listener.getLogger().println("  > Server URL: " + serverUrl);
		listener.getLogger().println("  > Project: " + project);
		listener.getLogger().println("  > Package name: " + packageName);
		listener.getLogger().println("  > Archive extension: " + archiveExtension);
		listener.getLogger().println();
		try {
			String output = RapidDeployConnector.invokeRapidDeployBuildPackage(getAuthenticationToken(), getServerUrl(), getProject(), getPackageName(),
					getArchiveExtension(), false, true);
			boolean success = true;
			final String jobId = RapidDeployConnector.extractJobId(output);
			if (jobId != null) {
				listener.getLogger().println("Checking job status every 30 seconds...");
				boolean runningJob = true;
				long milisToSleep = 30000L;
				while (runningJob) {
					Thread.sleep(milisToSleep);
					final String jobDetails = RapidDeployConnector.pollRapidDeployJobDetails(authenticationToken, serverUrl, jobId);
					final String jobStatus = RapidDeployConnector.extractJobStatus(jobDetails);
					listener.getLogger().println("Job status: " + jobStatus);
					if ((jobStatus.equals("DEPLOYING")) || (jobStatus.equals("QUEUED")) || (jobStatus.equals("STARTING")) || (jobStatus.equals("EXECUTING"))) {
						listener.getLogger().println("Job running, next check in 30 seconds...");
						milisToSleep = 30000L;
					} else if ((jobStatus.equals("REQUESTED")) || (jobStatus.equals("REQUESTED_SCHEDULED"))) {
						listener.getLogger().println(
								"Job in a REQUESTED state. Approval may be required in RapidDeploy "
										+ "to continue with the execution, next check in 30 seconds...");
					} else if (jobStatus.equals("SCHEDULED")) {
						listener.getLogger().println("Job in a SCHEDULED state, the execution will start in a future date, next check in 5 minutes...");
						listener.getLogger().println("Printing out job details: ");
						listener.getLogger().println(jobDetails);
						milisToSleep = 300000L;
					} else {
						runningJob = false;
						listener.getLogger().println("Job finished with status: " + jobStatus);
						if ((jobStatus.equals("FAILED")) || (jobStatus.equals("REJECTED")) || (jobStatus.equals("CANCELLED"))
								|| (jobStatus.equals("UNEXECUTABLE")) || (jobStatus.equals("TIMEDOUT")) || (jobStatus.equals("UNKNOWN"))) {
							success = false;
						}
					}
				}
			} else {
				throw new RuntimeException("Could not retrieve job id, running asynchronously!");
			}
			final String logs = RapidDeployConnector.pollRapidDeployJobLog(authenticationToken, serverUrl, jobId);
			if (!success) {
				throw new RuntimeException("RapidDeploy job failed. Please check the output." + System.getProperty("line.separator") + logs);
			}
			listener.getLogger().println("RapidDeploy job successfully run. Please check the output.");
			listener.getLogger().println();
			listener.getLogger().println(logs);
			return true;
		} catch (Exception e) {
			listener.getLogger().println("Call failed with error: " + e.getMessage());
			return false;
		}
	}

	private String replaceParametersPlaceholders(String paramStr, AbstractBuild<?, ?> build, BuildListener listener) {
		listener.getLogger().println("Replacing job parameters for '" + paramStr + "'");

		// First we need to retrieve all the placeholders: '${xxx}'
		Pattern pattern = Pattern.compile("\\$\\{[^\\$\\{\\}]+\\}");
		// Then we need to extract the string inside the placeholder
		Pattern inPattern = Pattern.compile("\\$\\{(.+)\\}");

		String group;
		String replaceStr;
		Matcher matcher = pattern.matcher(paramStr);
		Matcher inMatcher;

		// We iterate over the placeholders found
		while (matcher.find()) {
			group = matcher.group();
			listener.getLogger().println("Job parameter found: " + group);
			inMatcher = inPattern.matcher(group);
			// Obtain the string inside the placeholder
			if (inMatcher.matches()) {
				try {
					// Get the value of the parameter
					replaceStr = build.getEnvironment(listener).get(inMatcher.group(1));
					listener.getLogger().println("Job parameter value retrieved: " + replaceStr);
					// If the value is not blank, replace the parameter
					if (StringUtils.isNotBlank(replaceStr)) {
						listener.getLogger()
								.println("Retrieved value '" + replaceStr + "' from job parameter '" + group + "'");
						paramStr = paramStr.replace(group, replaceStr);
					} else {
						listener.getLogger().println("WARNING: job parameter not found '" + group + "'");
					}
				} catch (Exception e) {
					listener.getLogger().println("WARNING: Unable to retrieve the job parameter '" + group + "'");
					listener.getLogger().println("         " + e.getMessage());
				}
			}
		}
		listener.getLogger().println("Replaced value '" + paramStr + "'");
		return paramStr;
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
