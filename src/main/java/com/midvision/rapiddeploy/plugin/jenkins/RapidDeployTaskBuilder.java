package com.midvision.rapiddeploy.plugin.jenkins;
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

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;


public class RapidDeployTaskBuilder extends Notifier {

    private final String serverUrl;
    private final String authenticationToken;
    private final String projectName;
    private final String server;
    private final String environment;
    private final String instance;
    private final String application;
    private final String packageName;   
    
    @DataBoundConstructor
    public RapidDeployTaskBuilder(String serverUrl, String authenticationToken,
    		String projectName, String server,
			String environment, String instance,
			String application,	String packageName) {
		super();
		this.serverUrl = serverUrl;
		this.authenticationToken = authenticationToken; 
		this.projectName = projectName;
		this.server = server;
		this.environment = environment;
		this.instance = instance;
		this.application = application;
		this.packageName = packageName;
	}	
    
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {        	 	
   	 	String url = buildUrl();
   	 	listener.getLogger().println("Invoking RapidDeploy project deploy via path: " + url);   	 	
   	 	try {
			int retVal = invokeRDServerDeployment(url);
			listener.getLogger().println("Call returned with code: " + retVal);
			if(retVal == HttpStatus.SC_OK){
				return true;
			} else{
				return false;
			}
		} catch (Exception e) {
			listener.getLogger().println("Call failed with error: " + e.getMessage());
			return false;
		}   	 	   	 
    }

    public String getServerUrl() {
		return serverUrl;
	}
    

	public String getAuthenticationToken() {
		return authenticationToken;
	}

	public String getProjectName() {
		return projectName;
	}



	public String getServer() {
		return server;
	}



	public String getEnvironment() {
		return environment;
	}



	public String getInstance() {
		return instance;
	}



	public String getApplication() {
		return application;
	}



	public String getPackageName() {
		return packageName;
	}

	
    @Override
    public BuildStepDescriptor getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }
   
    /**
     * Descriptor for {@link RapidDeployTaskBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.        
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {        
        //check serverurl field
    	public FormValidation doCheckServerUrl(@QueryParameter String value)
                throws IOException, ServletException {
            return checkNotEmpty(value);
        }
    	//check authenticationToken field
    	public FormValidation doCheckAuthenticationToken(@QueryParameter String value)
                throws IOException, ServletException {
            return checkNotEmpty(value);
        }
    	//check projectName field
    	public FormValidation doCheckProjectName(@QueryParameter String value)
                throws IOException, ServletException {
            return checkNotEmpty(value);
        }    	
    	//check server field
    	public FormValidation doCheckServer(@QueryParameter String value)
                throws IOException, ServletException {
            return checkNotEmpty(value);
        }
    	//check environment field
    	public FormValidation doCheckEnvironment(@QueryParameter String value)
                throws IOException, ServletException {
            return checkNotEmpty(value);
        }
    	//check instance field
    	public FormValidation doCheckInstance(@QueryParameter String value)
                throws IOException, ServletException {
            return checkNotEmpty(value);
        }
    	//check application field
    	public FormValidation doCheckApplication(@QueryParameter String value)
                throws IOException, ServletException {
            return checkNotEmpty(value);
        }    	

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "RapidDeploy project deploy";
        }  
        
        public FormValidation checkNotEmpty(String value){
        	if (value.length() == 0){
                return FormValidation.error("Please set a value for this field!");
            }
            return FormValidation.ok();
        }
    }
    
    private int invokeRDServerDeployment(String url) throws Exception{      	
    	DefaultHttpClient httpClient = new DefaultHttpClient();
    	HttpPut putRequest = new HttpPut(url);
    	putRequest.addHeader("Authorization", authenticationToken);    	
		HttpResponse response = httpClient.execute(putRequest);
		return response.getStatusLine().getStatusCode();				 
    }
        
    private String buildUrl(){
    	StringBuilder url = new StringBuilder("");
    	if(!this.getServerUrl().startsWith("http://")){
    		url.append("http://");
    	}
    	url.append(serverUrl).append("/ws/deployment/");
    	url.append(projectName).append("/runjob/deploy/");
    	url.append(server).append("/");
    	url.append(environment).append("/");
    	url.append(instance).append("/");
    	url.append(application);
    	url.append("?returnLogFile=true");
    	if(packageName != null && !"".equals(packageName) && !"snapshot".equals(packageName.toLowerCase())){
    		url.append("&packageName=").append(packageName);
    	}
    	return url.toString();
    }

	public BuildStepMonitor getRequiredMonitorService() {
		// run anyway
		return BuildStepMonitor.NONE;
	}
}

