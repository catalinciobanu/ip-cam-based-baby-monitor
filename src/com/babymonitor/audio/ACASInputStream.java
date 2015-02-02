package com.babymonitor.audio;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;

public class ACASInputStream extends DataInputStream {
	private AudioHeader mHeader = new AudioHeader();
	private byte[] mDataBuffer = null;
	private int mDataBufferSize = 0;
	
	
	public ACASInputStream(InputStream streaming) {super(new BufferedInputStream(streaming)); }
    
    private boolean readHeader() {
    	try {
    		readFully(mHeader.getBuffer(), 0, AudioHeader.HEADER_LENGTH);
    		return true;
    	} catch (Exception e) {
    		return false;
    	}
    }
    
    private boolean readData() {
    	try {
    		readFully(mDataBuffer);
    	}
    	catch(Exception e) {
    		return false;
    	}
    	
    	return true;
    }
    
    public AudioHeader getHeader() {
    	return mHeader;
    }
    
    public byte[] readAudioFrame() {
    	if (!readHeader())
    		return null;
    	
    	if (mDataBufferSize < mHeader.getDataLength()) {
    		mDataBuffer = new byte[mHeader.getDataLength()];
    		mDataBufferSize = mHeader.getDataLength();
    	}
    	
    	if (!readData())
    		return null;
    	
    	return mDataBuffer;
    }

}
