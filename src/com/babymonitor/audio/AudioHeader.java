package com.babymonitor.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class AudioHeader {
	
	// Audio format list 
	public static final short AFMT_AAC = 4096;
    public static final short AFMT_AC3 = 1024;
    public static final short AFMT_ALAW = 8192;
    public static final short AFMT_AMR = 2048;
    public static final short AFMT_A_LAW = 2;
    public static final short AFMT_IMA_ADPCM = 4;
    public static final short AFMT_MPEG = 512;
    public static final short AFMT_MS_ADPCM = 0;
    public static final short AFMT_MU_LAW = 1;
    public static final short AFMT_S16_BE = 32;
    public static final short AFMT_S16_LE = 16;
    public static final short AFMT_S8 = 64;
    public static final short AFMT_U16_BE = 256;
    public static final short AFMT_U16_LE = 128;
    public static final short AFMT_U8 = 8;
    public static final short AFMT_UNKNOW = -1;
    
    
    // Sample rate list
    public static final int SR_11_025K = 11025;
    public static final int SR_16K = 16000;
    public static final int SR_22_05K = 22050;
    public static final int SR_44_1K = 44100;
    public static final int SR_48K = 48000;
    public static final int SR_8K = 8000;
    
    public static final int ACS_HEADER = 0xf6010000;
    public static final short CHANNEL_MONO = 1;
    public static final short CHANNEL_STEREO = 2;
    public static final short ENCODING_PCM_16BIT = 16;
    public static final short ENCODING_PCM_8BIT = 8;
    
    public static final int HEADER_LENGTH = 40;
    
    private ByteBuffer mHeader;
    
    public AudioHeader() {
    	mHeader = ByteBuffer.wrap(new byte[HEADER_LENGTH]);
    	Arrays.fill(mHeader.array(), (byte)0);
    	mHeader.order(ByteOrder.LITTLE_ENDIAN);
        mHeader.limit(HEADER_LENGTH);
    }
    
    public byte[] getBuffer() {
    	return mHeader.array();
    }
    
    public int getHeaderId()
    {
        return mHeader.getInt(0);
    }
    
    public int getHeaderLength()
    {
        return mHeader.getInt(4);
    }
    
    public int getDataLength()
    {
        return mHeader.getInt(8);
    }
    
    public short getFormat()
    {
        return mHeader.getShort(28);
    }
    
    public short getChannels()
    {
        return mHeader.getShort(30);
    }
    
    public short getSampleRate()
    {
        return mHeader.getShort(32);
    }
    
    public short getSampleBits()
    {
        return mHeader.getShort(34);
    }

}
