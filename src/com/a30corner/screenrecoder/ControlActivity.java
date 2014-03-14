
package com.a30corner.screenrecoder;

import com.a30corner.screenrecoder.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ToggleButton;

public class ControlActivity extends Activity {
	private ScreenRecoder mDisplaySourceService;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.source_activity);
		
		mDisplaySourceService = new ScreenRecoder(this);

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		stopServices();
	}

	private void startServices() {

		mDisplaySourceService.start("/sdcard/temp.mp4");
	}

	private void stopServices() {
		if (mDisplaySourceService != null && mDisplaySourceService.isStarted()) {
			mDisplaySourceService.stop();
			mDisplaySourceService = null;
		}
	}

	public void onToggleClicked(View view) {
		boolean on = ((ToggleButton) view).isChecked();
		if (on) {
			startServices();
		} else {
			stopServices();
		}
	}

}
