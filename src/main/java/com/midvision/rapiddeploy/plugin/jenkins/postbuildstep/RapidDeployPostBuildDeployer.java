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
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.midvision.rapiddeploy.plugin.jenkins.RapidDeployConnector;

public class RapidDeployPostBuildDeployer extends Notifier {

	private final String serverUrl;
	private final String authenticationToken;
	private final String project;	
	private final String environment;
	private final String packageName;
	public static final Log logger = LogFactory.getLog(RapidDeployPostBuildDeployer.class);
	

	@DataBoundConstructor
	public RapidDeployPostBuildDeployer(String serverUrl, String authenticationToken,
			String project, String environment, String packageName) {
		super();
		this.serverUrl = serverUrl;
		this.authenticationToken = authenticationToken;
		this.environment = environment;
		this.packageName = packageName;
		this.project = project;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,BuildListener listener) {			

		listener.getLogger().println("Invoking RapidDeploy project deploy via path: " + serverUrl);
		try {
			String[] envObjects = environment.split("\\.");
			if(environment.contains(".") && envObjects.length>3){			
				String output = RapidDeployConnector.invokeRapidDeployDeployment(authenticationToken, serverUrl, project, envObjects[0], envObjects[1], envObjects[2], envObjects[3], packageName);				
				listener.getLogger().println(output);
				
				listener.getLogger().println("Check the results of the deployments here: " + serverUrl + "/ws/feed/" + project + "/list/jobs");			
				return true;			
			} else {
				listener.getLogger().println("Exception: Invalid environment settings found! " + environment);
				throw new Exception("Invalid environment settings found!");
			}
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

	@Override
	public BuildStepDescriptor getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link RapidDeployPostBuildDeployer}. Used as a singleton. The
	 * class is marked as public so that it can be accessed from views.
	 */		

	
	public BuildStepMonitor getRequiredMonitorService() {
		// run anyway
		return BuildStepMonitor.NONE;
	}

	@Extension
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Publisher> {
		
		public DescriptorImpl() {
			super(RapidDeployPostBuildDeployer.class);
			load();
		}

		// check serverurl field
		public FormValidation doCheckServerUrl(@QueryParameter String value)
				throws IOException, ServletException {
			return checkNotEmpty(value);
		}

		// check authenticationToken field
		public FormValidation doCheckAuthenticationToken(
				@QueryParameter String value) throws IOException,
				ServletException {
			return checkNotEmpty(value);
		}
				
		public FormValidation doGetProjects(
				@QueryParameter("serverUrl") final String serverUrl,
				@QueryParameter("authenticationToken") final String authenticationToken,
				@QueryParameter("project") final String project)
				throws IOException, ServletException {			
			
			return FormValidation.ok();
		}

		public FormValidation doGetEnvironments(
				@QueryParameter("environment") final String environment)
				throws IOException, ServletException {
			
			return FormValidation.ok();
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			return true;
		}


		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "RapidDeploy project deploy";
		}

		public FormValidation checkNotEmpty(String value) {
			if (value.length() == 0) {
				return FormValidation
						.error("Please set a value for this field!");
			}
			return FormValidation.ok();
		}

		public ListBoxModel doFillProjectItems(@QueryParameter("serverUrl") final String serverUrl, @QueryParameter("authenticationToken") final String authenticationToken) {
			ListBoxModel items = new ListBoxModel();
			if(serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken)){
				List<String> projects;
				try {
					projects = RapidDeployConnector.invokeRapidDeployListProjects(authenticationToken, serverUrl);
					for(String projectName : projects){
						items.add(projectName);
					}
				} catch (Exception e) {					
					e.printStackTrace();
				}				
			}			
			return items;
		}

		public ListBoxModel doFillEnvironmentItems(@QueryParameter("serverUrl") final String serverUrl, @QueryParameter("authenticationToken") final String authenticationToken, @QueryParameter("project") final String project) {
			ListBoxModel items = new ListBoxModel();
			if(serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken) && project != null && !"".equals(project)){
				List<String> environments;
				try {
					environments = RapidDeployConnector.invokeRapidDeployListEnvironments(authenticationToken, serverUrl, project);
					for(String environmentName : environments){
						if(!environmentName.contains("Project [")){
							items.add(environmentName);
						}
					}
				} catch (Exception e) {					
					e.printStackTrace();
				}				
			}			
			return items;
		}
		
		public ListBoxModel doFillPackageNameItems(@QueryParameter("serverUrl") final String serverUrl, @QueryParameter("authenticationToken") final String authenticationToken, @QueryParameter("project") final String project, @QueryParameter("environment") final String environment) {
			ListBoxModel items = new ListBoxModel();
			if(serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken) && project != null && !"".equals(project) && environment != null && !"".equals(environment)){
				if(environment.contains(".")){
					String[] envObjects = environment.split("\\.");
					if(envObjects.length>3){
						List<String> packageNames;
						try {
							items.add("SNAPSHOT");
							packageNames = RapidDeployConnector.invokeRapidDeployListPackages(authenticationToken, serverUrl, project, envObjects[0], envObjects[1], envObjects[2]);
							for(String packageName : packageNames){
								if(!"null".equals(packageName)){
									items.add(packageName);
								}
							}
						} catch (Exception e) {					
							e.printStackTrace();
						}				
					}
				}
			}
			return items;
		}

	}
}
