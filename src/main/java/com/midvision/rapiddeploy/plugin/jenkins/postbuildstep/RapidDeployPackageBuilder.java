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

import com.midvision.rapiddeploy.plugin.jenkins.RapidDeployConnector;

public class RapidDeployPackageBuilder extends Notifier {

	private final String serverUrl;
	private final String authenticationToken;
	private final String project;
	private final boolean enableCustomPackageName;
	private final String packageName;
	private final String archiveExension;

	@DataBoundConstructor
	public RapidDeployPackageBuilder(String serverUrl, String authenticationToken, String project, boolean enableCustomPackageName, String packageName,
			String archiveExension) {
		super();
		this.serverUrl = serverUrl;
		this.authenticationToken = authenticationToken;
		this.project = project;
		this.enableCustomPackageName = enableCustomPackageName;
		this.packageName = packageName;
		this.archiveExension = archiveExension;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
		listener.getLogger().println("Invoking RapidDeploy package builder via path: " + serverUrl);
		try {
			RapidDeployConnector.invokeRapidDeployBuildPackage(getAuthenticationToken(), getServerUrl(), getProject(), getPackageName(), getArchiveExension());
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

	public String getArchiveExension() {
		return archiveExension;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		// run anyway
		return BuildStepMonitor.NONE;
	}

	/**
	 * Descriptor for {@link RapidDeployPackageBuilder}. Used as a singleton.
	 * The class is marked as public so that it can be accessed from views.
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

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

		public FormValidation doGetPackages(@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken, @QueryParameter("project") final String project) throws IOException,
				ServletException {
			try {
				String packageList = "";
				if (serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken) && project != null
						&& !"".equals(project)) {
					List<String> packageNames;
					try {
						packageNames = RapidDeployConnector.invokeRapidDeployListPackages(authenticationToken, serverUrl, project);
						if (packageNames != null && packageNames.size() > 0) {
							packageList = "<table>";
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
						} else {
							packageList = "There are no deployment packages for '" + project + "'.";
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				return FormValidation.okWithMarkup(packageList);
			} catch (Exception e) {
				return FormValidation.error("Client error : " + e.getMessage());
			}
		}

		/** ARCHIVE EXTENSION FIELD **/

		public FormValidation doCheckArchiveExension(@QueryParameter String value) throws IOException, ServletException {
			if (value.equals("jar") || value.equals("war") || value.equals("ear") || value.equals("tar") || value.equals("rar") || value.equals("zip")) {
				return FormValidation.ok();
			}
			return FormValidation.error("Please use one of the following values: jar, war, ear, tar, rar or zip.");
		}

		public ComboBoxModel doFillArchiveExensionItems() {
			ComboBoxModel items = new ComboBoxModel();
			items.add("jar");
			items.add("war");
			items.add("ear");
			items.add("tar");
			items.add("rar");
			items.add("zip");
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
