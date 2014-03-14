package com.a30corner.screenrecoder;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaMuxer.OutputFormat;
import android.util.Log;
import android.view.Surface;

public class ScreenRecoder {
	private static final String DISPLAY_NAME = "ScreenRecoder";
	private final DisplayManager mDisplayManager;

	private int videoTrackIndex;

	private VirtualDisplayThread mVirtualDisplayThread;

	
	private volatile boolean started;

	public ScreenRecoder(Context context) {

		mDisplayManager = (DisplayManager) context
				.getSystemService(Context.DISPLAY_SERVICE);
	}

	public synchronized void start(String path) {
		if (!started) {			
			mVirtualDisplayThread = new VirtualDisplayThread(720,
					1280, 320,path);
			mVirtualDisplayThread.start();
			started = true;
		}
	}

	public synchronized void stop() {
		if (started) {
			started = false;
			mVirtualDisplayThread.quit();
		}

	}
	
	public boolean isStarted(){
		return started;
	}
	

	private final class VirtualDisplayThread extends Thread {
		private static final int TIMEOUT_USEC = 1000000;
		private static final int BIT_RATE = 6000000;
		private static final int FRAME_RATE = 30;
		private static final int I_FRAME_INTERVAL = 10;
		
		private final int mWidth;
		private final int mHeight;
		private final int mDensityDpi;
		private MediaMuxer muxer;
		
		private volatile boolean mQuitting;

		private boolean mMuxerStarted;

		public VirtualDisplayThread(int width, int height, int densityDpi,String path) {
			mWidth = width;
			mHeight = height;
			mDensityDpi = densityDpi;
			
			try {
				muxer = new MediaMuxer(path, OutputFormat.MUXER_OUTPUT_MPEG_4);
			} catch (IOException e) {
				Log.e("sam", e.getMessage(), e);

			}
			
		}

		@Override
		public void run() {
			MediaFormat format = MediaFormat.createVideoFormat("video/avc",
					mWidth, mHeight);
			format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
					MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
			format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
			format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
			format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
					I_FRAME_INTERVAL);

			MediaCodec codec = MediaCodec.createEncoderByType("video/avc");
			codec.configure(format, null, null,
					MediaCodec.CONFIGURE_FLAG_ENCODE);
			Surface surface = codec.createInputSurface();
			codec.start();

			VirtualDisplay virtualDisplay = mDisplayManager
					.createVirtualDisplay(DISPLAY_NAME, mWidth, mHeight,
							mDensityDpi, surface,
							DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
			
			if (virtualDisplay != null) {
				stream(codec);
				virtualDisplay.release();
			}

			codec.signalEndOfInputStream();
			codec.stop();
		}

		public void quit() {
			mQuitting = true;
		}

		private void stream(MediaCodec codec) {
			BufferInfo info = new BufferInfo();
			ByteBuffer[] buffers = null;

			while (!mQuitting) {
				int index = codec.dequeueOutputBuffer(info, TIMEOUT_USEC);
				if (index >= 0) {
					if (buffers == null) {
						buffers = codec.getOutputBuffers();
					}

					ByteBuffer buffer = buffers[index];
					buffer.limit(info.offset + info.size);
					buffer.position(info.offset);

					muxer.writeSampleData(videoTrackIndex, buffer, info);

					codec.releaseOutputBuffer(index, false);
				} else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
					if (mMuxerStarted) {
						throw new RuntimeException("format changed twice");
					}
					
					MediaFormat newFormat = codec.getOutputFormat();

					// now that we have the Magic Goodies, start the muxer
					videoTrackIndex = muxer.addTrack(newFormat);
					muxer.start();

					mMuxerStarted = true;

					buffers = null;
				} else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
					buffers = null;
				} else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
					Log.e("sam", "Codec dequeue buffer timed out.");
				}
			}
		
			muxer.stop();
			muxer.release();
		}
	}
}
