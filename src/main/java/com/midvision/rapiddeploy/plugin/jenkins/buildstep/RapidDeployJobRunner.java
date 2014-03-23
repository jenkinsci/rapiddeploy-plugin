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
import java.util.List;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.midvision.rapiddeploy.plugin.jenkins.RapidDeployConnector;

public class RapidDeployJobRunner extends Builder {

	private final String serverUrl;
	private final String authenticationToken;
	private final String project;	
	private final String environment;
	private final String packageName;
	private final Boolean asynchronousJob;
	public static final Log logger = LogFactory.getLog(RapidDeployJobRunner.class);
	

	@DataBoundConstructor
	public RapidDeployJobRunner(String serverUrl, String authenticationToken,
			String project, String environment, String packageName, Boolean asynchronousJob) {
		super();
		this.serverUrl = serverUrl;
		this.authenticationToken = authenticationToken;
		this.environment = environment;
		this.packageName = packageName;
		this.project = project;
		this.asynchronousJob = asynchronousJob;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,BuildListener listener) {			
		boolean success = true;
		listener.getLogger().println("Invoking RapidDeploy project deploy via path: " + serverUrl);
		try {
			String[] envObjects = environment.split("\\.");
			String output;
			if(environment.contains(".") && envObjects.length == 4){					
				output = RapidDeployConnector.invokeRapidDeployDeployment(authenticationToken, serverUrl, project, envObjects[0], envObjects[1], envObjects[2], envObjects[3], packageName);				
			} else if(environment.contains(".") && envObjects.length == 3){
				//support for RD v3.5+ - instance removed
				output = RapidDeployConnector.invokeRapidDeployDeployment(authenticationToken, serverUrl, project, envObjects[0], envObjects[1], null, envObjects[2], packageName);				
			} else{
				listener.getLogger().println("Exception: Invalid environment settings found! " + environment);
				throw new Exception("Invalid environment settings found!");
			}
			listener.getLogger().println("RapidDeploy job has successfully started!");
				
			if(!asynchronousJob){
				String jobId = RapidDeployConnector.extractJobId(output);
				if(jobId != null){
					listener.getLogger().println("Checking job status in every 30 seconds...");
					boolean runningJob = true;
					//sleep 30sec by default
					long milisToSleep = 30000;
					while(runningJob){
						Thread.sleep(milisToSleep);
						String jobDetails = RapidDeployConnector.pollRapidDeployJobDetails(authenticationToken, serverUrl, jobId);
						String jobStatus = RapidDeployConnector.extractJobStatus(jobDetails);
						
						listener.getLogger().println("Job status is " + jobStatus);
						if(jobStatus.equals("DEPLOYING") || jobStatus.equals("QUEUED") || 
								jobStatus.equals("STARTING") || jobStatus.equals("EXECUTING")){														
							listener.getLogger().println("Job is running, next check in 30 seconds..");
							milisToSleep = 30000;
						} else if(jobStatus.equals("REQUESTED") || jobStatus.equals("REQUESTED_SCHEDULED")){
							listener.getLogger().println("Job is in a REQUESTED state. Approval may be required in RapidDeploy to continue with execution, next check in 30 seconds..");
						} else if(jobStatus.equals("SCHEDULED")){
							listener.getLogger().println("Job is in a SCHEDULED state, execution will start in a future date, next check in 5 minutes..");
							listener.getLogger().println("Printing out job details");
							listener.getLogger().println(jobDetails);
							milisToSleep = 300000;
						} else{
						
							runningJob = false;
							listener.getLogger().println("Job is finished with status " + jobStatus);
							if(jobStatus.equals("FAILED") || jobStatus.equals("REJECTED") || 
									jobStatus.equals("CANCELLED") || jobStatus.equals("UNEXECUTABLE") || 
									jobStatus.equals("TIMEDOUT") || jobStatus.equals("UNKNOWN")){
								success = false;
							}
						}
					}
					} else{
						throw new Exception("Could not retrieve job id, running asynchronously!");
					}
					listener.getLogger().println();
					String logs = RapidDeployConnector.pollRapidDeployJobLog(authenticationToken, serverUrl, jobId);
					listener.getLogger().println(logs);
				} else{			
					listener.getLogger().println("Job is running asynchronously. You can check the results of the deployments here once finished: " + serverUrl + "/ws/feed/" + project + "/list/jobs");
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
	
	public Boolean getAsynchronousJob(){
		return asynchronousJob;
	}

	@Override
	public BuildStepDescriptor getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link RapidDeployJobRunner}. Used as a singleton. The
	 * class is marked as public so that it can be accessed from views.
	 */		

	
	public BuildStepMonitor getRequiredMonitorService() {
		// run anyway
		return BuildStepMonitor.NONE;
	}

	@Extension
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Builder> {
		
		public DescriptorImpl() {
			super(RapidDeployJobRunner.class);
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
							items.add("LATEST");
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
