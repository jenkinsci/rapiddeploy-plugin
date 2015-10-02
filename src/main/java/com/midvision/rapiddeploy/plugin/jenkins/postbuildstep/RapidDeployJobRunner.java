package com.midvision.rapiddeploy.plugin.jenkins.postbuildstep;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.midvision.rapiddeploy.connector.RapidDeployConnector;

public class RapidDeployJobRunner extends Notifier {

	private final String serverUrl;
	private final String authenticationToken;
	private final String project;
	private final String environment;
	private final String packageName;
	private final Boolean asynchronousJob;

	@DataBoundConstructor
	public RapidDeployJobRunner(String serverUrl, String authenticationToken, String project, String environment, String packageName, Boolean asynchronousJob) {
		super();
		this.serverUrl = serverUrl;
		this.authenticationToken = authenticationToken;
		this.environment = environment;
		this.packageName = packageName;
		this.project = project;
		this.asynchronousJob = asynchronousJob;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
		boolean success = true;
		listener.getLogger().println("Invoking RapidDeploy project deploy via path: " + serverUrl);
		try {
			String output = "";
			try {
				output = RapidDeployConnector.invokeRapidDeployDeploymentPollOutput(authenticationToken, serverUrl, project, environment, packageName, true);
			} catch (Exception e) {
				throw new Exception("Invalid environment settings found! " + environment);
			}
			listener.getLogger().println("RapidDeploy job has successfully started!");

			if (!asynchronousJob) {
				String jobId = RapidDeployConnector.extractJobId(output);
				if (jobId != null) {
					listener.getLogger().println("Checking job status in every 30 seconds...");
					boolean runningJob = true;
					// sleep 30sec by default
					long milisToSleep = 30000;
					while (runningJob) {
						Thread.sleep(milisToSleep);
						String jobDetails = RapidDeployConnector.pollRapidDeployJobDetails(authenticationToken, serverUrl, jobId);
						String jobStatus = RapidDeployConnector.extractJobStatus(jobDetails);

						listener.getLogger().println("Job status is " + jobStatus);
						if (jobStatus.equals("DEPLOYING") || jobStatus.equals("QUEUED") || jobStatus.equals("STARTING") || jobStatus.equals("EXECUTING")) {
							listener.getLogger().println("Job is running, next check in 30 seconds..");
							milisToSleep = 30000;
						} else if (jobStatus.equals("REQUESTED") || jobStatus.equals("REQUESTED_SCHEDULED")) {
							listener.getLogger()
									.println(
											"Job is in a REQUESTED state. Approval may be required in RapidDeploy to continue with execution, next check in 30 seconds..");
						} else if (jobStatus.equals("SCHEDULED")) {
							listener.getLogger().println("Job is in a SCHEDULED state, execution will start in a future date, next check in 5 minutes..");
							listener.getLogger().println("Printing out job details");
							listener.getLogger().println(jobDetails);
							milisToSleep = 300000;
						} else {
							runningJob = false;
							listener.getLogger().println("Job is finished with status " + jobStatus);
							if (jobStatus.equals("FAILED") || jobStatus.equals("REJECTED") || jobStatus.equals("CANCELLED") || jobStatus.equals("UNEXECUTABLE")
									|| jobStatus.equals("TIMEDOUT") || jobStatus.equals("UNKNOWN")) {
								success = false;
							}
						}
					}
				} else {
					throw new Exception("Could not retrieve job id, running asynchronously!");
				}
				listener.getLogger().println();
				String logs = RapidDeployConnector.pollRapidDeployJobLog(authenticationToken, serverUrl, jobId);
				listener.getLogger().println(logs);
			} else {
				listener.getLogger().println(
						"Job is running asynchronously. You can check the results of the deployments here once finished: " + serverUrl + "/ws/feed/" + project
								+ "/list/jobs");
			}
			return success;
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

	public String getEnvironment() {
		return environment;
	}

	public String getPackageName() {
		return packageName;
	}

	public Boolean getAsynchronousJob() {
		return asynchronousJob;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		// run anyway
		return BuildStepMonitor.NONE;
	}

	/**
	 * Descriptor for {@link RapidDeployJobRunner}. Used as a singleton. The
	 * class is marked as public so that it can be accessed from views.
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		public DescriptorImpl() {
			super(RapidDeployJobRunner.class);
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
			return "RapidDeploy project deploy";
		}

		/** SERVER URL FIELD **/

		public FormValidation doCheckServerUrl(@QueryParameter String value) throws IOException, ServletException {
			return checkNotEmpty(value);
		}

		/** AUTHENTICATION TOKEN FIELD **/

		public FormValidation doCheckAuthenticationToken(@QueryParameter String value) throws IOException, ServletException {
			return checkNotEmpty(value);
		}

		/** PROJECT FIELD **/

		public FormValidation doCheckProject(@QueryParameter String value) throws IOException, ServletException {
			return checkNotEmpty(value);
		}

		public ComboBoxModel doFillProjectItems(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken) {
			ComboBoxModel items = new ComboBoxModel();
			if (serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken)) {
				List<String> projects;
				try {
					projects = RapidDeployConnector.invokeRapidDeployListProjects(authenticationToken, serverUrl);
					for (String projectName : projects) {
						items.add(projectName);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return items;
		}

		/** ENVIRONMENT FIELD **/

		public FormValidation doCheckEnvironment(@QueryParameter String value) throws IOException, ServletException {
			return checkNotEmpty(value);
		}

		public ComboBoxModel doFillEnvironmentItems(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken, @QueryParameter("project") final String project) {
			ComboBoxModel items = new ComboBoxModel();
			if (serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken) && project != null
					&& !"".equals(project)) {
				List<String> environments;
				try {
					environments = RapidDeployConnector.invokeRapidDeployListEnvironments(authenticationToken, serverUrl, project);
					for (String environmentName : environments) {
						if (!environmentName.contains("Project [") && !environmentName.contains("domainxml")) {
							items.add(environmentName);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return items;
		}

		/** PACKAGE FIELD **/

		public ComboBoxModel doFillPackageNameItems(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken, @QueryParameter("project") final String project,
				@QueryParameter("environment") final String environment) {
			ComboBoxModel items = new ComboBoxModel();
			if (serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken) && project != null
					&& !"".equals(project) && environment != null && !"".equals(environment)) {
				if (environment.contains(".")) {
					String[] envObjects = environment.split("\\.");
					List<String> packageNames;
					try {
						items.add("LATEST");
						if (environment.contains(".") && envObjects.length == 4) {
							packageNames = RapidDeployConnector.invokeRapidDeployListPackages(authenticationToken, serverUrl, project, envObjects[0],
									envObjects[1], envObjects[2]);
						} else if (environment.contains(".") && envObjects.length == 3) {
							// support for RD v3.5+ - instance removed
							packageNames = RapidDeployConnector.invokeRapidDeployListPackages(authenticationToken, serverUrl, project, envObjects[0],
									envObjects[1], null);
						} else {
							throw new Exception("Invalid environment settings found!");
						}
						for (String packageName : packageNames) {
							if (!"null".equals(packageName) && !packageName.startsWith("Deployment")) {
								items.add(packageName);
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			return items;
		}

		/** AUX **/

		public FormValidation checkNotEmpty(String value) {
			if (value.length() == 0) {
				return FormValidation.error("Please set a value for this field!");
			}
			return FormValidation.ok();
		}
	}
}
