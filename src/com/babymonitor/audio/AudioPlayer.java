package com.babymonitor.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class AudioPlayer {
	
	private AudioPlayerProcessor processor;
    private Thread thread;
	private ACASInputStream mIn = null;
	private boolean mRun = false;
	private AudioTrack mAudioTrack = null;
	private int mAudioTrackBufferSize = 0;
	
	public AudioPlayer() {
		processor = new AudioPlayerProcessor();
	}
	
	public class AudioPlayerProcessor implements Runnable {
		public void run() {
			byte[] buffer = null;
			while (mRun) {
				buffer = mIn.readAudioFrame();
				if (buffer == null)
					continue;
				AudioHeader header = mIn.getHeader();
				
				if (mAudioTrackBufferSize < header.getDataLength()) {
					if (mAudioTrack != null)
						mAudioTrack.stop();
					
					mAudioTrackBufferSize = 2 * AudioTrack.getMinBufferSize(header.getSampleRate(), mapChannels(header.getChannels()), mapFormat(header.getSampleBits()));
					
					if (mAudioTrackBufferSize < header.getDataLength()) 
						mAudioTrackBufferSize = 2 * header.getDataLength();
					
					mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, header.getSampleRate(), mapChannels(header.getChannels()), mapFormat(header.getSampleBits()), mAudioTrackBufferSize, AudioTrack.MODE_STREAM);
					mAudioTrack.play();
				}
				
				mAudioTrack.write(buffer, 0, header.getDataLength());
			}
		}
	}
	
	public void setSource(ACASInputStream source) { 
		mIn = source; 
        startPlayback();
	}
	
	public void stopPlayback() {
		if (mRun) {
	        mRun = false;
	        boolean retry = true;
	        while(retry) {
	            try {
	                thread.join();
	                retry = false;
	            } catch (InterruptedException e) {}
	        }
	        try {
	        	mIn.close();
	        }catch(Exception e){}
	        
	        if (mAudioTrack != null) {
	        	mAudioTrack.stop();
	        	mAudioTrack = null;
	        	
	        	mAudioTrackBufferSize = 0;
	        }
	        	
	        mIn = null;
    	}
	}

	private void startPlayback() {
		if(mIn != null) {
            mRun = true;
            thread = new Thread(processor);
            thread.start();
        }
	}
	
	private int mapChannels(short channels) {
		return channels == AudioHeader.CHANNEL_MONO ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
	}
	
	private int mapFormat(short sampleBits) {
		return sampleBits == AudioHeader.ENCODING_PCM_8BIT? AudioFormat.ENCODING_PCM_8BIT : AudioFormat.ENCODING_PCM_16BIT;
	}
}
