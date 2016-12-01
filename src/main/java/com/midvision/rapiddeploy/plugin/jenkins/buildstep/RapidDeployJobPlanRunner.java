package com.midvision.rapiddeploy.plugin.jenkins.buildstep;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.midvision.rapiddeploy.connector.RapidDeployConnector;

public class RapidDeployJobPlanRunner extends Builder {

    private final String serverUrl;
    private final String authenticationToken;
    private final String jobPlan;
    private final Boolean asynchronousJob;
    private final Boolean showFullLogs;

    private static final Log logger = LogFactory.getLog(RapidDeployJobPlanRunner.class);

    @DataBoundConstructor
    public RapidDeployJobPlanRunner(String serverUrl, String authenticationToken, String jobPlan, Boolean asynchronousJob, Boolean showFullLogs) {
        super();
        this.serverUrl = serverUrl;
        this.authenticationToken = authenticationToken;
        this.jobPlan = jobPlan;
        this.asynchronousJob = asynchronousJob;
        this.showFullLogs = showFullLogs;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        listener.getLogger().println("Invoking RapidDeploy project deploy via path...");
        listener.getLogger().println("  > Server URL: " + serverUrl);
        listener.getLogger().println("  > jobPlan: " + jobPlan);
        listener.getLogger().println("  > Asynchronous? " + asynchronousJob);
        listener.getLogger().println("  > Show Full Logs? " + showFullLogs);
        listener.getLogger().println();
        String jobPlanId = jobPlan.substring(jobPlan.indexOf("[")+1,jobPlan.indexOf("]"));
        try {
            String output = RapidDeployConnector.invokeRapidDeployJobPlanPollOutput(authenticationToken, serverUrl, jobPlanId, true);
            if (!asynchronousJob) {
                String jobDetails = "";
                boolean success = true;
                final String jobId = RapidDeployConnector.extractJobId(output);
                if (jobId != null) {
                    listener.getLogger().println("Checking job status every 30 seconds...");
                    boolean runningJob = true;
                    long milisToSleep = 30000L;
                    while (runningJob) {
                        Thread.sleep(milisToSleep);
                        jobDetails = RapidDeployConnector.pollRapidDeployJobDetails(authenticationToken, serverUrl, jobId);
                        final String jobStatus = RapidDeployConnector.extractJobStatus(jobDetails);
                        listener.getLogger().println("Job status: " + jobStatus);
                        if ((jobStatus.equals("DEPLOYING")) || (jobStatus.equals("QUEUED")) || (jobStatus.equals("STARTING"))
                                || (jobStatus.equals("EXECUTING"))) {
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
                final StringBuilder fullLogs = new StringBuilder();
                fullLogs.append(logs).append("\n");

                if(showFullLogs){
                  List<String> includedJobIds = RapidDeployConnector.extractIncludedJobIdsUnderPipelineJob(jobDetails);
                  for(String internalJobId : includedJobIds){
                    fullLogs.append("LOGS RELATED TO JOB ID: ").append(internalJobId).append("\n");
                    fullLogs.append(RapidDeployConnector.pollRapidDeployJobLog(authenticationToken, serverUrl, internalJobId));
                  }
                }
                if (!success) {
                    throw new RuntimeException("RapidDeploy job failed. Please check the output." + System.getProperty("line.separator") + logs);
                }
                listener.getLogger().println("RapidDeploy job successfully run. Please check the output.");
                listener.getLogger().println();
                listener.getLogger().println(fullLogs.toString());
            }
            return true;
        } catch (Exception e) {
            listener.getLogger().println("Call failed with error: " + e.getMessage());
            return false;
        }
    }



    public String getServerUrl() {
        return serverUrl;
    }

    public String getJobPlan() {
        return jobPlan;
    }

    public String getAuthenticationToken() {
        return authenticationToken;
    }

    public Boolean getAsynchronousJob() {
        return asynchronousJob;
    }

    public Boolean getShowFullLogs() {
      return showFullLogs;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Descriptor for {@link RapidDeployJobRunner}. Used as a singleton. The
     * class is marked as public so that it can be accessed from views.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        final private static String NOT_EMPTY_MESSAGE = "Please set a value for this field!";
        final private static String NO_PROTOCOL_MESSAGE = "Please specify a protocol for the URL, e.g. \"http://\".";
        final private static String CONNECTION_BAD_MESSAGE = "Unable to establish connection.";

        private List<String> jobPlans;
        // private List<String> projects;
        private boolean newConnection = true;

        public DescriptorImpl() {
            super(RapidDeployJobPlanRunner.class);
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
            return "RapidDeploy job plan runner";
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

        public FormValidation doLoadJobPlans(@QueryParameter("serverUrl") final String serverUrl,
              @QueryParameter("authenticationToken") final String authenticationToken) throws IOException, ServletException {

                logger.debug("doLoadJobPlans");
                newConnection = true;
                if(getJobPlans(serverUrl, authenticationToken).isEmpty()){
                  return FormValidation.error(CONNECTION_BAD_MESSAGE);
                }
                return FormValidation.ok();
        }


        public ListBoxModel doFillJobPlanItems(@QueryParameter("serverUrl") final String serverUrl,
                @QueryParameter("authenticationToken") final String authenticationToken) {
                  logger.debug("doFillJobPlans");
                  ListBoxModel items = new ListBoxModel();
                  for(String jobPlan : getJobPlans(serverUrl, authenticationToken)) {
                    items.add(jobPlan);
                  }
                  return items;
        }

        private synchronized List<String> getJobPlans(final String serverUrl, final String authenticationToken) {
            logger.debug("getJobPlans");
            if(jobPlans == null || jobPlans.isEmpty() || newConnection) {
              try {
                jobPlans = new ArrayList<String>();
                if (serverUrl != null && !"".equals(serverUrl) && authenticationToken != null && !"".equals(authenticationToken)) {
                    logger.debug("REQUEST TO WEB SERVICE GET JOB PLANS...");
                    String jobPlansCallOutput = RapidDeployConnector.invokeRapidDeployJobPlans(authenticationToken, serverUrl);
                    Map<String, String> jobPlansExtracted = RapidDeployConnector.extractJobPlansFromXml(jobPlansCallOutput);
                    for(String jobPlanDesc : jobPlansExtracted.values()){
                      jobPlans.add(jobPlanDesc);
                    }
                    newConnection = false;
                    logger.debug("JOB PLANS RETRIEVED: " + jobPlans.size());
                } else {
                    jobPlans = new ArrayList<String>();
                }
              } catch (Exception e) {
                  logger.warn(e.getMessage());
                  jobPlans = new ArrayList<String>();
              }
            }
            logger.debug("JOB PLANS: " + jobPlans.size());
            return jobPlans;
        }
    }
}
