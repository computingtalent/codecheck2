package controllers;

import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import models.Config;
import models.PlayConfig;
import models.Util;
import play.libs.Json;
import play.libs.Jsonp;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Check extends Controller {
	
	private static Config config = PlayConfig.INSTANCE;
	
	// Classic HTML report, with optional callback for Sunita
	public CompletableFuture<Result> checkHTML() throws IOException {
		Map<String, String[]> formParams = request().body().asFormUrlEncoded();
		return CompletableFuture.supplyAsync(() -> {
			try {
		        Http.Cookie ccuCookie = request().cookie("ccu");
				String ccu = ccuCookie == null ? Util.createUID() : ccuCookie.value();					        

				Path submissionDir = Util.getDir(config, "submissions");				
		        Path tempDir = Util.createTempDirectory(submissionDir);
		        
		        String repo = "ext";
		        String problem = "";
		        String level = "check";
		        String callback = null;
		        for (String key : formParams.keySet()) {
		            String value = formParams.get(key)[0];
		            if (key.equals("repo"))
		                repo = value;
		            else if (key.equals("problem"))
		                problem = value;
		            else if (key.equals("level"))
		                level = value;
		            else if (key.equals("callback"))
		            	callback = value;
		            else
		                Util.write(tempDir, key, value);
		        }
		        Util.runLabrat(config, "html", repo, problem, level, tempDir.toAbsolutePath(), "User=" + ccu);
		        String report = Util.read(tempDir.resolve("report.html"));
		        if (callback != null) {
		        	// Replace download link with Save Score button
		        	String target = "<p class=\"score\">";
		        	int n = report.indexOf(target) + target.length();
		        	int n2 = report.indexOf("<", n);
		        	String score = report.substring(n, n2);
		        	n = report.indexOf("<p class=\"download\">", n2);
		        	target = "</p>";
		        	n2 = report.indexOf(target, n) + target.length();
		        	String buttonHTML = "<input id='submitScore' type='button' value='Submit score'>";
		        	report = report.substring(0, n) + buttonHTML + report.substring(n2);
		        	
		        	
		        	String buttonScriptTemplate = "<script src=''https://code.jquery.com/jquery-2.2.0.min.js''></script>" +
		   "<script>$(document).ready(function() '{'" +
		    "$(''#submitScore'').click(function()'{'" +
		      "$.getJSON(''{0}?callback=?'', '{'  score: ''{1}'' '}')" +
		      ".done(function(data) '{'" +
		        "if (data.received) $(''#submitScore'').prop(''disabled'', true);" +
		      "'}');" +
		    "'}');" + 
		  "'}');" +      
		  "</script>";
		        	String buttonScript = MessageFormat.format(buttonScriptTemplate, callback, score);
		        	n = report.indexOf("<title>");
		        	report = report.substring(0, n) + buttonScript + report.substring(n);        	
		        }
		        
		        int age = 180 * 24 * 60 * 60;
		        Http.Cookie newCookie = Http.Cookie.builder("ccu", ccu).withMaxAge(age).build();
		        return ok(report).withCookies(newCookie).as("text/html");
			}
			catch (Exception ex) {
				return internalServerError(Util.getStackTrace(ex));
			}
		});        
        
        
        // TODO: Delete tempDir
	}
		
	@BodyParser.Of(BodyParser.Json.class)
	public Result checkJson() throws IOException  {
		Path submissionDir = Util.getDir(config, "submissions");
        Path tempDir = Util.createTempDirectory(submissionDir);
	    JsonNode json = request().body().asJson();
	    Iterator<Map.Entry<String,JsonNode>> dirs = json.fields();
	    String repo = "ext";
	    String problem = null;
	    String level = "1";
	    String type = "json";
	    while (dirs.hasNext()) {
	    	Map.Entry<String, JsonNode> dirEntry = dirs.next();
	    	String key = dirEntry.getKey();
	    	JsonNode value = dirEntry.getValue();
	    	if ("repo".equals(key)) repo = value.textValue();
	    	else if ("problem".equals(key)) problem = value.textValue();
	    	else if ("level".equals(key)) level = value.textValue();
	    	else if ("type".equals(key)) type = value.textValue(); 
	    	else { 
	    		Path dir = tempDir.resolve(key);
	    		java.nio.file.Files.createDirectory(dir);
	    		Iterator<Map.Entry<String,JsonNode>> files = value.fields();
	    		while (files.hasNext()) {
	    			Map.Entry<String, JsonNode> fileEntry = files.next();	    		
	    			Util.write(dir, fileEntry.getKey(), fileEntry.getValue().textValue());
	    		}
	    	}
	    }
	    if (problem == null) // problem was submitted in JSON
	    	Util.runLabrat(config, type, repo, problem, level, tempDir.toAbsolutePath(), tempDir.resolve("submission").toAbsolutePath());
	    else
	    	Util.runLabrat(config, type, repo, problem, level, tempDir.resolve("submission").toAbsolutePath());
	    if ("html".equals(type))
	    	return ok(Util.read(tempDir.resolve("submission/report.html"))).as("text/html");
	    else if ("text".equals(type))
	    	return ok(Util.read(tempDir.resolve("submission/report.txt"))).as("text/plain");
	    else
	    	return ok(Util.read(tempDir.resolve("submission/report.json"))).as("application/json");
        // TODO: Delete tempDir	    
	}
	
	// From JS UI 	
	public CompletableFuture<Result> checkJsonp() throws IOException  {
		Map<String, String[]> queryParams = request().queryString();
		return CompletableFuture.supplyAsync(() -> {
			try {
				Path submissionDir = Util.getDir(config, "submissions");
				Path tempDir = Util.createTempDirectory(submissionDir);
				String repo = "ext";
				String problem = null;
				String level = "1";
				String type = "json";
				String callback = null;
				Path dir = tempDir.resolve("submission");
				java.nio.file.Files.createDirectory(dir);
				for (String key : queryParams.keySet()) {
					String value = queryParams.get(key)[0];
					if ("repo".equals(key)) repo = value;
					else if ("problem".equals(key)) problem = value;
					else if ("level".equals(key)) level = value;
					else if ("type".equals(key)) type = value;
					else if ("callback".equals(key)) callback = value;
					else
						Util.write(dir, key, value);
				}
				if (problem == null) // problem was submitted in JSON
					Util.runLabrat(config, type, repo, problem, level, tempDir.toAbsolutePath(), tempDir.resolve("submission").toAbsolutePath());
				else
					Util.runLabrat(config, type, repo, problem, level, tempDir.resolve("submission").toAbsolutePath());
				ObjectNode result = (ObjectNode) Json.parse(Util.read(tempDir.resolve("submission/report.json")));
				String reportZip = Util.base64(tempDir.resolve("submission"), "report.signed.zip");
				result.put("zip", reportZip);					
				
				if (callback == null)
					return ok(result);
				else
					return ok(Jsonp.jsonp(callback, result));
			} catch (Exception ex) {
				return internalServerError(Util.getStackTrace(ex));
			}
			// TODO: Delete tempDir		
		});		
	
	}
}