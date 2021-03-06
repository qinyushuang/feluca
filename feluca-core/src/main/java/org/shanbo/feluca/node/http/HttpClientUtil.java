package org.shanbo.feluca.node.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.shanbo.feluca.util.concurrent.ConcurrentExecutor;

import com.alibaba.fastjson.JSONObject;

public class HttpClientUtil {
	public static final String PROP_SO_TIMEOUT = "socketTimeout";
	// connection timeout measures in ms, closes a socket if connection
	public static final String PROP_CONNECTION_TIMEOUT = "connTimeout";
	private static HttpClientUtil instance = new HttpClientUtil();

	private HttpClient client ;

	private HttpClientUtil(){
		ThreadSafeClientConnManager mgr = new ThreadSafeClientConnManager();
		mgr.setDefaultMaxPerRoute(256);
		mgr.setMaxTotal(256);
		client = new DefaultHttpClient(mgr);
		client.getParams().setIntParameter(PROP_SO_TIMEOUT, 5000);
		client.getParams().setIntParameter(PROP_CONNECTION_TIMEOUT, 3000);
	}

	public static HttpClientUtil get(){
		return instance;
	}

	/**
	 * 
	 * @param encodedURL
	 * @param connTimeOut
	 * @param soTimeOut
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public String doGet(String encodedURL, long connTimeOut, long soTimeOut) throws ClientProtocolException, IOException{
		HttpGet get = new HttpGet(encodedURL);
		get.getParams().setLongParameter(PROP_SO_TIMEOUT, soTimeOut);
		get.getParams().setLongParameter(PROP_CONNECTION_TIMEOUT, connTimeOut);
		return client.execute(get, new BasicResponseHandler());
	}

	public String doGet(String encodedURL) throws ClientProtocolException, IOException{
		return doGet(encodedURL, 3000, 5000);
	}
	
	
	public String doPost(String encodedURL, byte[] body,long connTimeOut, long soTimeOut) throws ClientProtocolException, IOException{
		HttpPost post = new HttpPost(encodedURL);
		post.getParams().setLongParameter(PROP_SO_TIMEOUT, soTimeOut);
		post.getParams().setLongParameter(PROP_CONNECTION_TIMEOUT, connTimeOut);
		post.setEntity(new ByteArrayEntity(body));
		return client.execute(post, new BasicResponseHandler());
	}
	
	public String doPost(String encodedURL, String body) throws ClientProtocolException, IOException{
		return doPost(encodedURL, body.getBytes(), 3000, 5000);
	}
	
	
	public HttpClient getHttpClient(){
		return client; 
	}
	
	public HttpResponse doPost(HttpPost post) throws ClientProtocolException, IOException{
		HttpResponse execute = client.execute(post);
		return execute;
	}
	
	public static Map<String, String> distribGet(List<String> hostPorts, final String path) throws InterruptedException, ExecutionException{
		List<Callable<String>> getCallables = new ArrayList<Callable<String>>();
		for(final String hostPort : hostPorts){
			getCallables.add(new Callable<String>() {
				public String call() throws Exception {
					return get().doGet("http://" + hostPort + "/" +( path.startsWith("/")? path.substring(1): path));
				}
			});
		}
		List<String> response = ConcurrentExecutor.execute(getCallables);
		Map<String, String> result = new HashMap<String, String>();
		for(int i = 0; i < hostPorts.size(); i++){
			result.put(hostPorts.get(i)	, response.get(i));
		}
		return result;
	}
	
	public static Map<String, String> distribPost(List<String> hostPorts, final String path, final String body) throws InterruptedException, ExecutionException{
		List<Callable<String>> getCallables = new ArrayList<Callable<String>>();
		for(final String hostPort : hostPorts){
			getCallables.add(new Callable<String>() {
				public String call() throws Exception {
					return get().doPost("http://" + hostPort + "/" +( path.startsWith("/")? path.substring(1): path), body);
				}
			});
		}
		List<String> response = ConcurrentExecutor.execute(getCallables);
		Map<String, String> result = new HashMap<String, String>();
		for(int i = 0; i < hostPorts.size(); i++){
			result.put(hostPorts.get(i)	, response.get(i));
		}
		return result;
	}
	
}
