package io.callstats.sdk;

import java.io.IOException;

import io.callstats.sdk.messages.BridgeEventMessage;
import io.callstats.sdk.messages.BridgeEventResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;


/**
 * The Class CallStats.
 *
 * @author Karthik Budigere
 */
public class CallStats {	
	
	/** The http client. */
	private CallStatsHttpClient httpClient;
	
	/** The app id. */
	private int appId;
	
	/** The app secret. */
	private String appSecret;
	
	/** The bridge id. */
	private String bridgeId;
	
	/** The listener. */
	private CallStatsInitListener listener;
	
	/** The authenticator. */
	private CallStatsAuthenticator authenticator;
	
	/** The logger. */
	private static final Logger logger = LogManager.getLogger("CallStats");
	
	/** The gson. */
	private Gson gson;
	
	/** The endpoint info. */
	private EndpointInfo endpointInfo;
	
	/** The is initialized. */
	private boolean isInitialized;
			
	/**
	 * Checks if is initialized.
	 *
	 * @return true, if is initialized
	 */
	public boolean isInitialized() {
	    return isInitialized;
	}

	/**
	 * Sets the initialized.
	 *
	 * @param isInitialized the new initialized
	 */
	private void setInitialized(boolean isInitialized) {
		this.isInitialized = isInitialized;
	}

	/**
	 * Instantiates a new call stats.
	 */
	public CallStats() {
		gson = new Gson();
	}
	
	/**
	 * Initialize callstats.
	 *
	 * @param appId the app id
	 * @param appSecret the app secret
	 * @param bridgeId the bridge id
	 * @param endpointInfo the endpoint info
	 * @param callStatsInitListener the call stats init listener
	 */
	public void initialize(final int appId, final String appSecret, final String bridgeId, final EndpointInfo endpointInfo, final CallStatsInitListener callStatsInitListener) {
		if (appId <= 0 || StringUtils.isBlank(appSecret) || StringUtils.isBlank(bridgeId) || endpointInfo == null || callStatsInitListener == null) {
			logger.error("initialize: Arguments cannot be null ");
			throw new IllegalArgumentException("initialize: Arguments cannot be null");
		}
		
		this.appId = appId;
		this.appSecret = appSecret;
		this.bridgeId = bridgeId;
		this.listener = callStatsInitListener;
		this.endpointInfo = endpointInfo;
		
		httpClient = new CallStatsHttpClient();
		authenticator = new CallStatsAuthenticator(appId, this.appSecret, bridgeId, httpClient, new CallStatsInitListener() {
			
			public void onInitialized(String msg) {
				listener.onInitialized(msg);
				setInitialized(true);
				CallStatsBridgeKeepAliveManager bridgeKeepAliveManager = new CallStatsBridgeKeepAliveManager(appId, bridgeId, authenticator.getToken(), httpClient);
				bridgeKeepAliveManager.startKeepAliveSender();
			}
			
			public void onError(CallStatsErrors error, String errMsg) {
				listener.onError(error, errMsg);;				
			}
		});		
		authenticator.doAuthentication();
		
//		CallStatsAsyncHttpClient httpClient1;
//		httpClient1 = new CallStatsAsyncHttpClient();	
//		authenticator.doAuthentication1(appId, appSecret, bridgeId,httpClient1);
	}
	
	
	/**
	 * Send call stats bridge event.
	 *
	 * @param bridgeStatusInfo the bridge status info
	 */
	public void sendCallStatsBridgeEvent(BridgeStatusInfo bridgeStatusInfo) {	
		if (isInitialized()) {			 
			long epoch = System.currentTimeMillis()/1000;				
			BridgeEventMessage eventMessage = new BridgeEventMessage(appId, bridgeId, CallStatsConst.CS_VERSION, CallStatsConst.END_POINT_TYPE, ""+epoch, authenticator.getToken(), bridgeStatusInfo, endpointInfo);
			String requestMessageString = gson.toJson(eventMessage);
			httpClient.sendAsyncHttpRequest(CallStatsConst.bridgeEventUrl, CallStatsConst.httpPostMethod, requestMessageString, new CallStatsHttpResponseListener() {
				public void onResponse(HttpResponse response) {
					
					int responseStatus = response.getStatusLine().getStatusCode();
					logger.debug("Response "+response.toString()+":"+responseStatus);
					BridgeEventResponse eventResponseMessage;
					try {
						String responseString = EntityUtils.toString(response.getEntity());
						eventResponseMessage  = gson.fromJson(responseString,BridgeEventResponse.class);	
					} catch (ParseException e) {						
						logger.error("ParseException "+e.getMessage(),e);
						throw new RuntimeException(e);
					} catch (IOException e) {
						logger.error("IO Execption "+e.getMessage(),e);
						throw new RuntimeException(e);					
					}
					if(responseStatus == 200) {
						logger.debug("Response status is "+eventResponseMessage.getStatus()+":"+eventResponseMessage.getReason());
					}
				}
				
				public void onFailure(Exception e) {
					logger.error("Response exception"+e.getMessage(),e);
				}
				
			});	
		} else {
			// TODO retransmission queue
			throw new UnsupportedOperationException("queueing not implemented yet");
		}
	}
	
}
