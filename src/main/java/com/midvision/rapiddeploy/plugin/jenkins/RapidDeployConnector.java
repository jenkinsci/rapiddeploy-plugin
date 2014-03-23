package com.midvision.rapiddeploy.plugin.jenkins;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class RapidDeployConnector {
	
	
	public static String invokeRapidDeployDeployment(String authenticationToken, String serverUrl, String projectName, String server, String environment, String instance, String application, String packageName) throws Exception{
		String deploymentUrl = buildDeploymentUrl(serverUrl, projectName, server, environment, instance, application, packageName);		
		String output = callRDServerPutReq(deploymentUrl, authenticationToken);
		return output;
	}
	
	public static String invokeRapidDeployBuildPackage(String authenticationToken, String serverUrl, String projectName, String packageName, String archiveExension) throws Exception{
		String deploymentUrl = buildPackageBuildUrl(serverUrl, projectName, packageName, archiveExension);		
		String output = callRDServerPutReq(deploymentUrl, authenticationToken);
		return output;
	}
	
	public static String pollRapidDeployJobDetails(String authenticationToken, String serverUrl, String jobId) throws Exception{
		String deploymentUrl = buildJobStatusUrl(serverUrl, jobId);		
		String output = callRDServerGetReq(deploymentUrl, authenticationToken);
		return output;
	}
	
	public static String pollRapidDeployJobLog(String authenticationToken, String serverUrl, String jobId) throws Exception{
		String deploymentUrl = buildJobLogUrl(serverUrl, jobId);		
		String output = callRDServerGetReq(deploymentUrl, authenticationToken);
		return output;
	}
	
	public static List<String> invokeRapidDeployListProjects(String authenticationToken, String serverUrl) throws Exception{
		String projectListUrl = buildProjectListQueryUrl(serverUrl, authenticationToken);		
		String output = callRDServerGetReq(projectListUrl, authenticationToken);
		return extractTagValueFromXml(output, "name");						
	}
	
	public static List<String> invokeRapidDeployListEnvironments(String authenticationToken, String serverUrl, String projectName) throws Exception{
		String environmentListUrl = buildEnvironmentListQueryUrl(serverUrl, authenticationToken, projectName);		
		String output = callRDServerGetReq(environmentListUrl, authenticationToken);
		return extractTagValueFromXml(output, "span");
	}
	
	public static List<String> invokeRapidDeployListPackages(String authenticationToken, String serverUrl, String projectName, String server, String environment, String instance) throws Exception{
		String packageListUrl = buildPackageListQueryUrl(serverUrl, authenticationToken, projectName, server, environment, instance);		
		String output = callRDServerGetReq(packageListUrl, authenticationToken);
		return extractTagValueFromXml(output, "span");
	}
	
	public static List<String> invokeRapidDeployListPackages(String authenticationToken, String serverUrl, String projectName) throws Exception{
		String packageListUrl = buildPackageListQueryUrl(serverUrl, authenticationToken, projectName);		
		String output = callRDServerGetReq(packageListUrl, authenticationToken);
		return extractTagValueFromXml(output, "span");
	}
	
	private static String buildDeploymentUrl(String serverUrl, String projectName, String server, String environment, String instance, String application, String packageName) {
		StringBuilder url = new StringBuilder("");
		if (!serverUrl.startsWith("http://")) {
			url.append("http://");
		}
		url.append(serverUrl).append("/ws/deployment/");
		url.append(projectName).append("/runjob/deploy/");
		url.append(server).append("/");
		url.append(environment).append("/");
		url.append(instance).append("/");
		url.append(application);
		url.append("?returnLogFile=true");
		if (packageName != null && !"".equals(packageName)
				&& !"latest".equals(packageName.toLowerCase())) {
			url.append("&packageName=").append(packageName);
		}
		return url.toString();
	}
	
	private static String buildPackageBuildUrl(String serverUrl, String projectName, String packageName, String archiveExension) {
		StringBuilder url = new StringBuilder("");
		if (!serverUrl.startsWith("http://")) {
			url.append("http://");
		}
		url.append(serverUrl).append("/ws/deployment/");
		url.append(projectName).append("/package/create?packageName=");
		url.append(packageName == null? "" : packageName).append("&archiveExension=").append(archiveExension);		
		
		return url.toString();
	}
	
	private static String buildJobStatusUrl(String serverUrl, String jobId) {
		StringBuilder url = new StringBuilder("");
		if (!serverUrl.startsWith("http://")) {
			url.append("http://");
		}
		url.append(serverUrl).append("/ws/deployment/display/job/" + jobId );					
		return url.toString();
	}
	
	private static String buildJobLogUrl(String serverUrl, String jobId) {
		StringBuilder url = new StringBuilder("");
		if (!serverUrl.startsWith("http://")) {
			url.append("http://");
		}
		url.append(serverUrl).append("/ws/deployment/showlog/job/" + jobId );					
		return url.toString();
	}
	
	private static String buildProjectListQueryUrl(String serverUrl, String authenticationToken) {
		StringBuilder url = new StringBuilder("");
		if (!serverUrl.startsWith("http://")) {
			url.append("http://");
		}
		url.append(serverUrl).append("/ws/project/list");		
		
		return url.toString();
	}

	private static String buildEnvironmentListQueryUrl(String serverUrl, String authenticationToken, String projectName) {
		StringBuilder url = new StringBuilder("");
		if (!serverUrl.startsWith("http://")) {
			url.append("http://");
		}
		url.append(serverUrl).append("/ws/project/" + projectName + "/list");		
		
		return url.toString();
	}
	
	private static String buildPackageListQueryUrl(String serverUrl, String authenticationToken, String projectName, String server, String environment, String instance) {
		StringBuilder url = new StringBuilder("");
		if (!serverUrl.startsWith("http://")) {
			url.append("http://");
		}
		url.append(serverUrl).append("/ws/deployment/" + projectName + "/package/list/" + server + "/" + environment + "/" + instance);		
		return url.toString();
	}
	
	private static String buildPackageListQueryUrl(String serverUrl, String authenticationToken, String projectName) {
		StringBuilder url = new StringBuilder("");
		if (!serverUrl.startsWith("http://")) {
			url.append("http://");
		}
		url.append(serverUrl).append("/ws/deployment/" + projectName + "/package/list");		
		return url.toString();
	}
	
	private static String callRDServerPutReq(String url, String authenticationToken) throws Exception {
		DefaultHttpClient httpClient = new DefaultHttpClient();
		HttpPut putRequest = new HttpPut(url);
		putRequest.addHeader("Authorization", authenticationToken);
		HttpResponse response = httpClient.execute(putRequest);
		InputStream responseOutput = response.getEntity().getContent();
		int status = response.getStatusLine().getStatusCode();
		//if error status code returned
		if(status >= 400 && status < 500){
			throw new Exception(response.getStatusLine().toString() + "\nError calling RapidDeploy server on url:"  + url + "\nCause: " + getInputstreamContent(responseOutput));
		}
						
		return getInputstreamContent(responseOutput);
	}
	
	private static String callRDServerGetReq(String url, String authenticationToken) throws Exception {
		DefaultHttpClient httpClient = new DefaultHttpClient();
		HttpGet getRequest = new HttpGet(url);
		getRequest.addHeader("Authorization", authenticationToken);
		HttpResponse response = httpClient.execute(getRequest);
		InputStream responseOutput = response.getEntity().getContent();
		int status = response.getStatusLine().getStatusCode();
		//if error status code returned
		if(status >= 400 && status < 500){
			throw new Exception(response.getStatusLine().toString() + "\nError calling RapidDeploy server on url:"  + url + "\nCause: " + getInputstreamContent(responseOutput));
		}
		return getInputstreamContent(responseOutput);
	}

	private static String getInputstreamContent(InputStream inputstream) throws IOException{
		String output = "";
		String line;
		final byte buf[] = new byte[1024];
		int nread;
		while ((nread = inputstream.read(buf)) > 0) {
			line = new String(buf, 0, nread);
			output += line;
		}
		return output;
	}		
	
	public static List<String> extractTagValueFromXml(String xmlContent, String tagName) throws Exception{
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document document = builder.parse(new InputSource(new StringReader(xmlContent)));
		Element rootElement = document.getDocumentElement();
		
		List<String> outputValues = new ArrayList<String>();
        NodeList list = rootElement.getElementsByTagName(tagName);
        if (list != null && list.getLength() > 0) {
        	for(int i = 0; i<list.getLength(); i++){
            NodeList subList = list.item(i).getChildNodes();

	            if (subList != null && subList.getLength() > 0) {
	            	for(int j = 0; j<subList.getLength(); j++){
	            		outputValues.add(subList.item(j).getNodeValue());
	            	}
	            }
        	}
        }
        return outputValues;
	}
	
	
	public static String extractJobStatus(String responseOutput) throws Exception{
		String jobStatus = null;
		List<String> responseData = extractTagValueFromXml(responseOutput, "span");
		for(int i=0; i< responseData.size(); i++){
			if(responseData.get(i).equals("Display Details Job Status") && responseData.size() >= (i+1)){
				jobStatus = responseData.get(i+1);
			}
		}
		return jobStatus;
	}
	
	public static String extractJobId(String responseOutput) throws Exception{
		String jobId = null;
		List<String> responseData = extractTagValueFromXml(responseOutput, "span");
		for(int i=0; i< responseData.size(); i++){
			if(responseData.get(i).equals("Deployment Job ID") && responseData.size() >= (i+1)){
				jobId = responseData.get(i+1);
			}
		}
		return jobId;
	}
}
