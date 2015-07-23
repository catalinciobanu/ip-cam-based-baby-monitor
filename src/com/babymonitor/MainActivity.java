package com.babymonitor;

import java.io.InputStream;

import com.babymonitor.audio.ACASInputStream;
import com.babymonitor.audio.AudioPlayer;
import com.babymonitor.video.MjpegInputStream;
import com.babymonitor.video.MjpegView;
import ip.cam.babymonitor.R;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements LocalService.UIUpdater, AuthenticationDialogFragment.AuthenticationDialogListener{
	
	private final String TAG = MainActivity.class.toString();
	private final String USERNAME_PREFERENCES_KEY_NAME = "user";
	private final String PASSWORD_PREFERENCES_KEY_NAME = "pwd";
	private final String REMEMBER_PREFERENCES_KEY_NAME = "remember";
	
	public static String mUsername;
	public static String mPassword;
	private boolean mRemember;
	
	// How much to wait after triggered events were cleared before putting device to sleep 
	private final long SLEEP_WHEN_IDLE_AFTER = 5000;

	private Runnable mFallAsleep;
	
	private Handler mMainHandler;

	// Flag indicating whether we have called bind on the service
	boolean mIsBound;
	
	PowerManager mPowerManager;
	WakeLock mWakeLock;
	WifiManager mWifiManager;
	WifiLock mWifiLock;
	
	private LocalService mBoundService;
	
	private AudioPlayer mAudioPlayer;

	private ServiceConnection mConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  Because we have bound to a explicit
	        // service that we know is running in our own process, we can
	        // cast its IBinder to a concrete class and directly access it.
	        mBoundService = ((LocalService.LocalBinder)service).getService();
	        
	        mBoundService.setUIUpdater(MainActivity.this);
	    }

	    public void onServiceDisconnected(ComponentName className) {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        // Because it is running in our same process, we should never
	        // see this happen.
	        mBoundService = null;
	    }
	};

	void doBindService() {
	    // Establish a connection with the service.  We use an explicit
	    // class name because we want a specific service implementation that
	    // we know will be running in our own process (and thus won't be
	    // supporting component replacement by other applications).
	    bindService(new Intent(MainActivity.this, 
	            LocalService.class), mConnection, Context.BIND_AUTO_CREATE);
	    mIsBound = true;
	}

	void doUnbindService() {
	    if (mIsBound) {
	        // Detach our existing connection.
	        unbindService(mConnection);
	        mIsBound = false;
	    }
	}
	
	void ensureVisible(boolean visible) {
		if (!visible) {
			if (mWakeLock.isHeld())
        		mWakeLock.release();
        	
    		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		else {
			if (!mWakeLock.isHeld())
				mWakeLock.acquire();
			
	        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// Get a handler that can be used to post to the main thread
    	mMainHandler = new Handler(getMainLooper());
    	
    	mFallAsleep = new Runnable() {
    		@Override
            public void run() {
    			ensureVisible(false);
            }
        };
        
        mAudioPlayer = new AudioPlayer();
		
		MjpegView videoView = (MjpegView)findViewById(R.id.videoView);
        videoView.setDisplayMode(MjpegView.SIZE_BEST_FIT);
        videoView.showFps(false);
		
		final Button button = (Button) findViewById(R.id.turn_off_led);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	mBoundService.turnOffLed();
            }
        });
        
        mPowerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
    	mWakeLock = mPowerManager.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), TAG);
    	mWakeLock.setReferenceCounted(false);
    	
    	mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    	mWifiLock = mWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);
    	mWifiLock.setReferenceCounted(false);
        
        loadAuthenticationData();
        
        if (mRemember) {
        	doBindService();
    		mWifiLock.acquire();
        }
        else
        	showAuthDialog();
	}
	
	@Override
    public void onPause() {
    	super.onPause();
    	
    	mAudioPlayer.stopPlayback();
    	
		MjpegView videoView = (MjpegView)findViewById(R.id.videoView);
        videoView.stopPlayback();
    }
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (mBoundService != null)
			mBoundService.initiateStreaming();
	}
	
	@Override
	protected void onDestroy() {
	    super.onDestroy();
	    doUnbindService();
	    
	    if (mWifiLock.isHeld())
    		mWifiLock.release();
	    
	    ensureVisible(false);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_exit) {
			finish();
			return true;
		}
		else if (id == R.id.action_clear_auth) {
			clearAuthenticationData();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void updateStatus(String status) {
		((TextView)findViewById(R.id.status)).setText(status);
	}
	
	@Override
	public void enableTurnOffLed() {
		((Button)findViewById(R.id.turn_off_led)).setEnabled(true);
	}
	
	@Override
	public void startAudioStreaming(InputStream streaming) {
		// Set the stream
		mAudioPlayer.setSource(new ACASInputStream(streaming));
	}
	
	@Override
	public void startVideoStreaming(InputStream streaming) {
		MjpegView videoView = (MjpegView)findViewById(R.id.videoView);
		
		// Set the stream
        videoView.setSource(new MjpegInputStream(streaming));
	}
	
	@Override
	public void onEventTrigger() {
		
		mMainHandler.removeCallbacks(mFallAsleep);
			
		ensureVisible(true);
	}
	
	@Override
	public void onEventIdle() {
    	mMainHandler.postDelayed(mFallAsleep, SLEEP_WHEN_IDLE_AFTER);
	}
	
	private void loadAuthenticationData() {
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		mUsername = settings.getString(USERNAME_PREFERENCES_KEY_NAME, "admin");
		mPassword = settings.getString(PASSWORD_PREFERENCES_KEY_NAME, "1234");
		mRemember = settings.getBoolean(REMEMBER_PREFERENCES_KEY_NAME, false);
	}
	
	private void saveAuthenticationData() {
		// We need an Editor object to make preference changes.
		// All objects are from android.context.Context
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		
		editor.putString(USERNAME_PREFERENCES_KEY_NAME, mUsername);
		editor.putString(PASSWORD_PREFERENCES_KEY_NAME, mPassword);
		editor.putBoolean(REMEMBER_PREFERENCES_KEY_NAME, mRemember);
	
		// Commit the edits!
		editor.commit();
	}
	
	private void clearAuthenticationData() {
		// We need an Editor object to make preference changes.
		// All objects are from android.context.Context
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		
		editor.clear();
			
		// Commit the edits!
		editor.commit();
	}
	
	private void showAuthDialog() {
        // Create an instance of the authentication dialog fragment and show it
        DialogFragment dialog = new AuthenticationDialogFragment();
        dialog.show(getFragmentManager(), "AuthDialogFragment");
    }

    // The dialog fragment receives a reference to this Activity through the
    // Fragment.onAttach() callback, which it uses to call the following methods
    // defined by the NoticeDialogFragment.NoticeDialogListener interface
    @Override
    public void onAuthenticationDialogPositiveClick(String username, String password, boolean remember) {
        // User touched the dialog's positive button
    	mUsername = username;
    	mPassword = password;
    	mRemember = remember;
    	if (remember)
    		saveAuthenticationData();
    	
    	doBindService();
		mWifiLock.acquire();
    }

    @Override
    public void onAuthenticationDialogNegativeClick() {
        // User touched the dialog's negative button
        finish();
    }
}
