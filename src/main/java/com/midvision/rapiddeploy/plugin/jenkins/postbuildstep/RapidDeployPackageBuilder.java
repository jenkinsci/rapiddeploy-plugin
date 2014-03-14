package com.midvision.rapiddeploy.plugin.jenkins.postbuildstep;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
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

public class RapidDeployPackageBuilder extends Notifier {

	private final String serverUrl;
	private final String authenticationToken;
	private final String project;	
	private final boolean enableCustomPackageName;
	private final String packageName;
	private final String archiveExension;
	public static final Log logger = LogFactory.getLog(RapidDeployPackageBuilder.class);
	

	@DataBoundConstructor
	public RapidDeployPackageBuilder(String serverUrl, String authenticationToken,
			String project, boolean enableCustomPackageName, String packageName, String archiveExension) {
		super();
		this.serverUrl = serverUrl;
		this.authenticationToken = authenticationToken;
		this.project = project;
		this.enableCustomPackageName = enableCustomPackageName;
		this.packageName = packageName;		
		this.archiveExension = archiveExension;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,BuildListener listener) {			

		listener.getLogger().println("Invoking RapidDeploy project deploy via path: " + serverUrl);
		listener.getLogger().println(project);
		listener.getLogger().println("enable custom package name: " + enableCustomPackageName);
		listener.getLogger().println(packageName);
		listener.getLogger().println(archiveExension);
		try {						
			String output = RapidDeployConnector.invokeRapidDeployBuildPackage(getAuthenticationToken(), getServerUrl(), getProject(), getPackageName(), getArchiveExension());
			listener.getLogger().println(output);
				
			listener.getLogger().println("Check the results of the deployments here: " + serverUrl + "/ws/feed/" + project + "/list/jobs");			
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
		if(enableCustomPackageName){
			return packageName;
		} else{
			return "";
		}
	}
	
	public String getArchiveExension(){
		return archiveExension;
	}

	@Override
	public BuildStepDescriptor getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link RapidDeployPackageBuilder}. Used as a singleton. The
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
			super(RapidDeployPackageBuilder.class);
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
						
		public FormValidation doGetPackages(@QueryParameter("serverUrl") final String serverUrl, @QueryParameter("authenticationToken") final String authenticationToken, @QueryParameter("project") final String project) throws IOException, ServletException {
		    try {
		    	String packageList = "";
		    	if(serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken) && project != null && !"".equals(project)){
					List<String> packageNames;
					try {								
						packageNames = RapidDeployConnector.invokeRapidDeployListPackages(authenticationToken, serverUrl, project);
						if(packageNames != null && packageNames.size()>0){
							packageList = "<table>";
							int index = 0;
							int limit = 10;
							for(String packageName : packageNames){
								if(!"null".equals(packageName)){
									packageList += "<tr><td class=\"setting-main\">";
									packageList += packageName;
									packageList += "</td></tr>";
									index++;
									if(index >= limit){
										break;
									}
								}							
							}
							packageList += "</table>";
						} else{
							packageList = "No deployment packages exist";
						}
					} catch (Exception e) {					
						e.printStackTrace();
					}				
				}
		    	
		        return FormValidation.okWithMarkup(packageList);
		    } catch (Exception e) {
		        return FormValidation.error("Client error : "+e.getMessage());
		    }
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			return true;
		}

//		@Override
//		public boolean configure(StaplerRequest req, JSONObject json)
//				throws FormException {
//						
//			save();
//			return super.configure(req, json);
//		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "RapidDeploy package build";
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
						
		
		public ListBoxModel doFillArchiveExensionItems() {
			ListBoxModel items = new ListBoxModel();
			items.add("jar");
			items.add("war");
			items.add("ear");			
			items.add("tar");
			items.add("rar");
			items.add("zip");			
			return items;
		}

	}
}
