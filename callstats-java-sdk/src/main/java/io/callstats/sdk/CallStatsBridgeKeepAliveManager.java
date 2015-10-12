package io.callstats.sdk;

import io.callstats.sdk.messages.BridgeKeepAliveMessage; 
import io.callstats.sdk.messages.BridgeKeepAliveResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * The Class CallStatsBridgeKeepAliveManager.
 */
public class CallStatsBridgeKeepAliveManager {
	
	/** The Constant keepAliveEventUrl. */
	private static final String keepAliveEventUrl = "/o/callStatsKeepAliveEvent";
	
	/** The app id. */
	private int appId;
	
	/** The bridge id. */
	private String bridgeId;
	
	/** The token. */
	private String token;
	
	/** The Constant scheduler. */
	private static final ScheduledExecutorService scheduler = 
			  Executors.newSingleThreadScheduledExecutor();
	
	/** The gson. */
	private Gson gson;
	
	/** The http client. */
	private CallStatsHttpClient httpClient;
	
	private CallStatsBridgeKeepAliveStatusListener keepAliveStatusListener;
	
	Future<?> future;
	
	/** The Constant logger. */
	private static final Logger logger = LogManager.getLogger("CallStatsBridgeKeepAliveManager");
	
	private int keepAliveInterval; 
	
	/**
	 * Instantiates a new call stats bridge keep alive manager.
	 *
	 * @param appId the app id
	 * @param bridgeId the bridge id
	 * @param token the token
	 * @param httpClient the http client
	 */
	public CallStatsBridgeKeepAliveManager(int appId,
			String bridgeId, String token,final CallStatsHttpClient httpClient,CallStatsBridgeKeepAliveStatusListener keepAliveStatusListener) {
		super();
		Properties prop = new Properties();
		InputStream input = null;
	     
    
		input = getClass().getClassLoader().getResourceAsStream(CallStatsConst.CallStatsJavaSDKPropertyFileName);
		if (input != null){
			try {
				prop.load(input);
				keepAliveInterval = Integer.parseInt(prop.getProperty("CallStats.keepAliveInterval"));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		this.appId = appId;
		this.bridgeId = bridgeId;
		this.token = token;
		this.httpClient = httpClient;
		this.keepAliveStatusListener = keepAliveStatusListener;
		gson = new Gson();
		
	}
	
	/**
	 * Stop keep alive sender.
	 */
	public void stopKeepAliveSender() {
		logger.info("Stoping keepAlive Sender");
		future.cancel(true);
	}
	
	
	/**
	 * Shuts keep alive sender.
	 */
	public void shutDownKeepAliveSender() {
		logger.info("Shutting down keepAlive Sender");
		scheduler.shutdownNow();
	}

	/**
	 * Start keep alive sender.
	 */
	public void startKeepAliveSender() {
		logger.info("Starting keepAlive Sender");
		future = scheduler.scheduleAtFixedRate(new Runnable() {			
			public void run() {
				sendKeepAliveBridgeMessage(appId,bridgeId,token,httpClient);
			}
		}, 0,keepAliveInterval,TimeUnit.MILLISECONDS);
	}
	
	
	/**
	 * Send keep alive bridge message.
	 *
	 * @param appId the app id
	 * @param bridgeId the bridge id
	 * @param token the token
	 * @param httpClient the http client
	 */
	private void sendKeepAliveBridgeMessage(int appId, String bridgeId, String token, CallStatsHttpClient httpClient) {
		long epoch = System.currentTimeMillis()/1000;	
		String apiTS = ""+epoch;
		BridgeKeepAliveMessage message = new BridgeKeepAliveMessage(appId, bridgeId, CallStatsConst.CS_VERSION, apiTS, token);
		String requestMessageString = gson.toJson(message);
		httpClient.sendAsyncHttpRequest(keepAliveEventUrl,CallStatsConst.httpPostMethod, requestMessageString,new CallStatsHttpResponseListener() {
			public void onResponse(HttpResponse response) {
				
				int responseStatus = response.getStatusLine().getStatusCode();
				logger.info("Response "+response.toString()+":"+responseStatus);
				BridgeKeepAliveResponse keepAliveResponse;
				try {
					String responseString = EntityUtils.toString(response.getEntity());
					keepAliveResponse  = gson.fromJson(responseString,BridgeKeepAliveResponse.class);	
				} catch (ParseException e) {						
					e.printStackTrace();
					throw new RuntimeException(e);
				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException(e);					
				} catch (JsonSyntaxException e) {
					logger.error("Json Syntax Exception "+e.getMessage(),e);
					e.printStackTrace();
					throw new RuntimeException(e);
				}
				if(responseStatus == 200) {
					logger.info("Response status is "+keepAliveResponse.getStatus()+":"+keepAliveResponse.getReason());
					if (keepAliveResponse.getStatus().equals("Error") && keepAliveResponse.getReason().contains("Invalid client token")) {
						stopKeepAliveSender();
						keepAliveStatusListener.onKeepAliveError(CallStatsErrors.AUTH_ERROR,keepAliveResponse.getReason() );
					}	
				}
			}
			
			public void onFailure(Exception e) {
				logger.info("Response exception"+e.toString());
			}
			
		});	
	}
}