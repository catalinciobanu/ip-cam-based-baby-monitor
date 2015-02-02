package com.babymonitor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import ip.cam.babymonitor.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class LocalService extends Service {
    private final String TAG = LocalService.class.toString();

	private NotificationManager mNotificationManager;
    
    private NsdManager mNsdManager;
    private NsdManager.ResolveListener mResolveListener;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdServiceInfo mNetworkService;
    private boolean mDiscoveryServiceOn = false;

    // Unique Identification Number for the Notifications
    // We use it on Notification start, and to cancel it
    private int NOTIFICATION_MONITORING_ON = R.string.monitoring_on;
    private int NOTIFICATION_AUDIO_DETECTED = R.string.audio_detected;
    private int NOTIFICATION_MOTION_DETECTED = R.string.motion_detected;
    
    private final String AUDIO_DETECTED_ON = "audio_detected=on";
    private final String AUDIO_DETECTED_OFF = "audio_detected=off";
    
    private final String MOTION_DETECTED_ON = "motion_detected_1=on";
    private final String MOTION_DETECTED_OFF = "motion_detected_1=off";
    
    private byte mDetectedEvents = 0;
    
    private final byte AUDIO_EVENT = 1;
    private final byte MOTION_EVENT = 2;
    
    
    // Web event specific section
    private final String CAM_WEB_EVENT_URL_SCHEME = "http";
    private final String CAM_WEB_EVENT_URL_FOLDER = "eng";
    private final String CAM_WEB_EVENT_URL_PAGE = "web_event.cgi";
    
    // MJPEG streaming specific section
    private final String CAM_VIDEO_STREAM_URL_SCHEME = "http";
    private final String CAM_VIDEO_STREAM_URL_FOLDER = "video";
    private final String CAM_VIDEO_STREAM_URL_PAGE = "mjpg.cgi";
    
    // ACAS streaming specific section
    private final String CAM_AUDIO_STREAM_URL_SCHEME = "http";
    private final String CAM_AUDIO_STREAM_URL_FOLDER = "audio";
    private final String CAM_AUDIO_STREAM_URL_PAGE = "ACAS.cgi";
    
    // Turn off LED specific section
    private final String CAM_TURN_OFF_LED_URL_SCHEME = "http";
    private final String CAM_TURN_OFF_LED_URL_FOLDER = "config";
    private final String CAM_TURN_OFF_LED_URL_PAGE = "led.cgi";
    private final String CAM_TURN_OFF_LED_KEY_NAME = "led";
    private final String CAM_TURN_OFF_LED_VALUE_OFF = "off";
    private final String CAM_TURN_OFF_LED_VALUE_ON = "on";
    
    private final String SERVICE_TYPE = "_http._tcp.";
    private final String SERVICE_NAME = "DCS-825L";
    
    public interface UIUpdater {
    	void updateStatus(String status);
    	void enableTurnOffLed();
    	void startAudioStreaming(InputStream streaming);
    	void startVideoStreaming(InputStream streaming);
    	void onEventTrigger();
    	void onEventIdle();
    }
    
    private UIUpdater mUIUpdater;
    
    private Handler mMainHandler;
    
    // This is the retrieved (from upnp service) IP address of the cam
    private String mCamHost;
    
    void updateStatus(final int resId) {
    	mMainHandler.post(new Runnable() {
            @Override
            public void run() {
            	if (mUIUpdater != null)
            		mUIUpdater.updateStatus(getString(resId));
            	else // The ui updater is not ready yet so postpone this for later (when it will be ready)
            		mMainHandler.post(this);
            }
        });
    }
    
    void onEventsTriggered() {
    	mMainHandler.post(new Runnable() {
            @Override
            public void run() {
            	if (mUIUpdater != null)
            		mUIUpdater.onEventTrigger();
            	else // The ui updater is not ready yet so postpone this for later (when it will be ready)
            		mMainHandler.post(this);
            }
        });
    }
    
    void onEventsCleared() {
    	mMainHandler.post(new Runnable() {
            @Override
            public void run() {
            	if (mUIUpdater != null)
            		mUIUpdater.onEventIdle();
            	else // The ui updater is not ready yet so postpone this for later (when it will be ready)
            		mMainHandler.post(this);
            }
        });
    }

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        LocalService getService() {
            return LocalService.this;
        }
    }
    
    public void setUIUpdater(UIUpdater uiUpdater) {
    	mUIUpdater = uiUpdater;
    }
    
    public void turnOffLed() {
    	if (mCamHost != null) {
    		new Thread(new Runnable() {
    	        public void run() {
    	        	DefaultHttpClient client = new DefaultHttpClient();
    	    		Credentials credentials = new UsernamePasswordCredentials(MainActivity.mUsername, MainActivity.mPassword);
    	    		client.getCredentialsProvider().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), credentials);

    	    		try {
    	    			// The cam seems to have a bug in that it doesn't know the real status of the led (on/off)
    	    			// In order to set if to off the workaround is to first set it to on and then to off
    	    			
    	    			updateStatus(R.string.turning_led_on);
    	    			
    	    			Uri.Builder uriBuilder = new Uri.Builder();
    	    			uriBuilder.scheme(CAM_TURN_OFF_LED_URL_SCHEME)
    	    		    	.authority(mCamHost)
    	    		    	.appendPath(CAM_TURN_OFF_LED_URL_FOLDER)
    	    		    	.appendPath(CAM_TURN_OFF_LED_URL_PAGE)
    	    				.appendQueryParameter(CAM_TURN_OFF_LED_KEY_NAME, CAM_TURN_OFF_LED_VALUE_ON);
    	    		    HttpGet getRequest = new HttpGet(uriBuilder.build().toString());

    	    		    HttpResponse response = client.execute(getRequest);
    	    		    int statusCode = response.getStatusLine().getStatusCode();
    	    		    if (statusCode != HttpStatus.SC_OK)
    	    		        throw new Exception();
    	    		    
    	    		    updateStatus(R.string.camera_led_on);
    	    		    
    	    		    // Now set it to off
    	    		    
    	    		    updateStatus(R.string.turning_led_off);
    	    		    
    	    		    uriBuilder = new Uri.Builder();
    	    			uriBuilder.scheme(CAM_TURN_OFF_LED_URL_SCHEME)
    	    		    	.authority(mCamHost)
    	    		    	.appendPath(CAM_TURN_OFF_LED_URL_FOLDER)
    	    		    	.appendPath(CAM_TURN_OFF_LED_URL_PAGE)
    	    				.appendQueryParameter(CAM_TURN_OFF_LED_KEY_NAME, CAM_TURN_OFF_LED_VALUE_OFF);
    	    		    getRequest = new HttpGet(uriBuilder.build().toString());

    	    		    response = client.execute(getRequest);
    	    		    statusCode = response.getStatusLine().getStatusCode();
    	    		    if (statusCode != HttpStatus.SC_OK)
    	    		        throw new Exception();
    	    		    
    	    		    updateStatus(R.string.camera_led_off);
    	    		} catch (Exception e) {
    	    			updateStatus(R.string.camera_led_switch_error);
    	    		}
    	        }
    	    }).start();
    	}
    }
    
    public void initiateStreaming() {
    	if (mCamHost != null) {
    		// Audio streaming
    		new Thread(new Runnable() {
    	        public void run() {
    	        	DefaultHttpClient client = new DefaultHttpClient();
    	    		Credentials credentials = new UsernamePasswordCredentials(MainActivity.mUsername, MainActivity.mPassword);
    	    		client.getCredentialsProvider().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), credentials);

    	    		try {
    	    			Uri.Builder uriBuilder = new Uri.Builder();
    	    			uriBuilder.scheme(CAM_AUDIO_STREAM_URL_SCHEME)
    	    		    	.authority(mCamHost)
    	    		    	.appendPath(CAM_AUDIO_STREAM_URL_FOLDER)
    	    		    	.appendPath(CAM_AUDIO_STREAM_URL_PAGE);
    	    		    HttpGet getRequest = new HttpGet(uriBuilder.build().toString());

    	    		    HttpResponse response = client.execute(getRequest);
    	    		    int statusCode = response.getStatusLine().getStatusCode();
    	    		    if (statusCode != HttpStatus.SC_OK)
    	    		        throw new Exception();
    	    		    final InputStream streaming = response.getEntity().getContent();
    	    		    
    	    		    mMainHandler.post(new Runnable() {
    	                    @Override
    	                    public void run() {
    	                    	// Start audio streaming
    	                    	if (mUIUpdater != null)
    	                    		mUIUpdater.startAudioStreaming(streaming);
    	                    }
    	                });
    	    		    
    	    		} catch (Exception e) {
    	    			
    	    		}
    	        }
    	    }).start();
    		
    		// Video streaming
    		new Thread(new Runnable() {
    	        public void run() {
    	        	DefaultHttpClient client = new DefaultHttpClient();
    	    		Credentials credentials = new UsernamePasswordCredentials(MainActivity.mUsername, MainActivity.mPassword);
    	    		client.getCredentialsProvider().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), credentials);

    	    		try {
    	    			Uri.Builder uriBuilder = new Uri.Builder();
    	    			uriBuilder.scheme(CAM_VIDEO_STREAM_URL_SCHEME)
    	    		    	.authority(mCamHost)
    	    		    	.appendPath(CAM_VIDEO_STREAM_URL_FOLDER)
    	    		    	.appendPath(CAM_VIDEO_STREAM_URL_PAGE);
    	    		    HttpGet getRequest = new HttpGet(uriBuilder.build().toString());

    	    		    HttpResponse response = client.execute(getRequest);
    	    		    int statusCode = response.getStatusLine().getStatusCode();
    	    		    if (statusCode != HttpStatus.SC_OK)
    	    		        throw new Exception();
    	    		    final InputStream streaming = response.getEntity().getContent();
    	    		    
    	    		    mMainHandler.post(new Runnable() {
    	                    @Override
    	                    public void run() {
    	                    	// Start video streaming
    	                    	if (mUIUpdater != null)
    	                    		mUIUpdater.startVideoStreaming(streaming);
    	                    }
    	                });
    	    		    
    	    		} catch (Exception e) {
    	    			
    	    		}
    	        }
    	    }).start();
    	}
    }
    
    private void initializeDiscoveryListener() {
        mDiscoveryListener = new NsdManager.DiscoveryListener() {

            @Override
            public void onDiscoveryStarted(String regType) {
            	mDiscoveryServiceOn = true;
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.d(TAG, "Service discovery success" + service);
                if (!service.getServiceType().equals(SERVICE_TYPE)) {
                    Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
                } else if (service.getServiceName().equals(SERVICE_NAME)){
                    mNsdManager.resolveService(service, mResolveListener);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.e(TAG, "service lost" + service);
                if (mNetworkService == service) {
                	mNetworkService = null;
                	mCamHost = null;
                }
            }
            
            @Override
            public void onDiscoveryStopped(String serviceType) {
            	mDiscoveryServiceOn = false;
            	Log.i(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }
        };
    }

    public void initializeResolveListener() {
        mResolveListener = new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Resolve failed" + errorCode);
                mNsdManager.stopServiceDiscovery(mDiscoveryListener);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Resolve Succeeded. " + serviceInfo);

                mNetworkService = serviceInfo;
                
                mCamHost = mNetworkService.getHost().getHostAddress();
                
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                    	// Start streaming
                    	initiateStreaming();
                    	
                    	// Enable the 'turn off led' button
                    	if (mUIUpdater != null)
                    		mUIUpdater.enableTurnOffLed();
                    	// Start the processing task
                    	mProcessingTask.execute(mCamHost);
                    }
                });
                
                if (mDiscoveryServiceOn)
                	mNsdManager.stopServiceDiscovery(mDiscoveryListener);
            }
        };
    }
    
    private AsyncTask<String, Void, Void> mProcessingTask = new AsyncTask<String, Void, Void>() {
    	@Override
        protected Void doInBackground(String... params) {
    		DefaultHttpClient client = new DefaultHttpClient();
    		Credentials credentials = new UsernamePasswordCredentials(MainActivity.mUsername, MainActivity.mPassword);
    		client.getCredentialsProvider().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), credentials);

    		try {
    			Uri.Builder uriBuilder = new Uri.Builder();
    			uriBuilder.scheme(CAM_WEB_EVENT_URL_SCHEME)
    		    	.authority(params[0])
    		    	.appendPath(CAM_WEB_EVENT_URL_FOLDER)
    		    	.appendPath(CAM_WEB_EVENT_URL_PAGE);
    		    HttpGet getRequest = new HttpGet(uriBuilder.build().toString());

    		    HttpResponse response = client.execute(getRequest);
    		    final int statusCode = response.getStatusLine().getStatusCode();
    		    if (statusCode != HttpStatus.SC_OK)
    		        throw new Exception();
    		    
    		    BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
    		    
    		    updateStatus(R.string.camera_connected);
    		    
    		    while (!isCancelled()) {
    		    	while (reader.ready() && !isCancelled()) {
    		    		String line = reader.readLine();
    		    		byte previousDetectedEventsValue = mDetectedEvents;
    		    		if (line.equals(AUDIO_DETECTED_ON)) {
    		    			mDetectedEvents |= AUDIO_EVENT;
    		    			LocalService.this.showAudioDetectedNotification();
    		    		} else if (line.equals(MOTION_DETECTED_ON)) {
    		    			mDetectedEvents |= MOTION_EVENT;
    		    			LocalService.this.showMotionDetectedNotification();
    		    		} else if (line.equals(AUDIO_DETECTED_OFF)) {
    		    			mDetectedEvents &= ~AUDIO_EVENT;
    		    		} else if (line.equals(MOTION_DETECTED_OFF)) {
    		    			mDetectedEvents &= ~MOTION_EVENT;
    		    		}
    		    		
    		    		if (previousDetectedEventsValue != mDetectedEvents) {
    		    			if (mDetectedEvents == 0)
    		    				onEventsCleared();
    		    			else if (previousDetectedEventsValue == 0)
    		    				onEventsTriggered();
    		    		}
    		    	}
    		    	
    		    	Thread.sleep(100);
    		    }
    		    updateStatus(R.string.camera_disconnected);
    		} catch (Exception e) {
    			updateStatus(R.string.camera_disconnected);
    			showStatusNotification(R.string.monitoring_off, true);
    			onEventsTriggered();
    		}
    		return null;
    	}
    };

    @Override
    public void onCreate() {
    	// Get a handler that can be used to post to the main thread
    	mMainHandler = new Handler(getMainLooper());
    	
    	mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    	mNsdManager = (NsdManager) getSystemService(NSD_SERVICE);
    	
    	initializeResolveListener();
        initializeDiscoveryListener();
        
        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
        
        // Display a notification about us starting.  We put an icon in the status bar
        showStatusNotification(R.string.monitoring_on, false);
    }

    @Override
    public void onDestroy() {
    	// Stop the discovery service (if on)
    	if (mDiscoveryServiceOn)
    		mNsdManager.stopServiceDiscovery(mDiscoveryListener);
    	
    	// Stop the processing task
    	mProcessingTask.cancel(false);
    	
    	// Dismiss all the notifications
    	dismissNotifications();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients
    private final IBinder mBinder = new LocalBinder();

    /**
     * Show a status notification
     */
    private void showStatusNotification(final int resId, boolean noticeable) {
        CharSequence text = getText(resId);
        
        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        // Set the icon, scrolling text
        Notification notification = new Notification.Builder(this)
        	.setContentIntent(contentIntent)
        	.setContentTitle(getText(R.string.cam_monitoring_label))
        	.setContentText(text)
        	.setSmallIcon(R.drawable.ic_launcher)
        	.getNotification();
        
        if (noticeable)
        	notification.defaults = Notification.DEFAULT_ALL;

        // Send the notification
        mNotificationManager.notify(NOTIFICATION_MONITORING_ON, notification);
    }
    
    /**
     * Dismiss all the previously shown notifications (monitoring is off now)
     */
    
    private void dismissNotifications() {
    	// Cancel the persistent notifications
        mNotificationManager.cancel(NOTIFICATION_MONITORING_ON);
        mNotificationManager.cancel(NOTIFICATION_MOTION_DETECTED);
        mNotificationManager.cancel(NOTIFICATION_AUDIO_DETECTED);
    }
    
    /**
     * Show a notification when audio is detected
     */
    private void showAudioDetectedNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.audio_detected);
        
        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        // Set the icon, scrolling text
        Notification notification = new Notification.Builder(this)
        	.setContentIntent(contentIntent)
        	.setContentTitle(getText(R.string.cam_monitoring_label))
        	.setContentText(text)
        	.setSmallIcon(R.drawable.audio_indicator)
        	.getNotification(); 

        notification.defaults = Notification.DEFAULT_ALL;
        
        // Send the notification
        mNotificationManager.notify(NOTIFICATION_AUDIO_DETECTED, notification);
    }
    
    /**
     * Show a notification when motion is detected
     */
    private void showMotionDetectedNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.motion_detected);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);
        
        // Set the icon, scrolling text
        Notification notification = new Notification.Builder(this)
        	.setContentIntent(contentIntent)
        	.setContentTitle(getText(R.string.cam_monitoring_label))
        	.setContentText(text)
        	.setSmallIcon(R.drawable.motion_indicator)
        	.getNotification();
        
        notification.defaults = Notification.DEFAULT_ALL;

        // Send the notification
        mNotificationManager.notify(NOTIFICATION_MOTION_DETECTED, notification);
    }
}
