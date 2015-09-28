/***
 Copyright (c) 2015 CommonsWare, LLC

 Licensed under the Apache License, Version 2.0 (the "License"); you may
 not use this file except in compliance with the License. You may obtain
 a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.commonsware.cwac.cam2.playground;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import com.commonsware.cwac.cam2.AbstractCameraActivity;
import com.commonsware.cwac.cam2.VideoRecorderActivity;
import java.io.File;

public class VideoFragment extends PreferenceFragment {
  interface Contract {
    void takeVideo(Intent i);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    addPreferencesFromResource(R.xml.prefs_video);
    setHasOptionsMenu(true);
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    if (!(activity instanceof Contract)) {
      throw new IllegalStateException("Hosting activity does not implement Contract interface!");
    }
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.actions, menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId()==R.id.take_picture) {
      takePicture();

      return(true);
    }

    return super.onOptionsItemSelected(item);
  }

  private void takePicture() {
    SharedPreferences prefs=getPreferenceManager().getSharedPreferences();
    VideoRecorderActivity.IntentBuilder b=new VideoRecorderActivity.IntentBuilder(getActivity());

    b.to(new File(getActivity().getExternalFilesDir(null), "test.mp4"));

    if (prefs.getBoolean("highQuality", false)) {
      b.quality(VideoRecorderActivity.Quality.HIGH);
    }
    else {
      b.quality(VideoRecorderActivity.Quality.LOW);
    }

    if (prefs.getBoolean("ffc", false)) {
      b.facing(AbstractCameraActivity.Facing.FRONT);
    }
    else {
      b.facing(AbstractCameraActivity.Facing.BACK);
    }

    if (prefs.getBoolean("exact_match", false)) {
      b.facingExactMatch();
    }

    if (prefs.getBoolean("debug", false)) {
      b.debug();
    }

    if (prefs.getBoolean("updateMediaStore", false)) {
      b.updateMediaStore();
    }

    if (prefs.getBoolean("forceClassic", false)) {
      b.forceClassic();
    }

    String durationLimit=prefs.getString("durationLimit", null);

    if (durationLimit!=null) {
      b.durationLimit(Integer.parseInt(durationLimit));
    }

    String sizeLimit=prefs.getString("sizeLimit", null);

    if (sizeLimit!=null) {
      b.sizeLimit(Integer.parseInt(sizeLimit));
    }

    if (prefs.getBoolean("mirrorPreview", false)) {
      b.mirrorPreview();
    }

    int rawFocusMode=Integer.valueOf(
      prefs.getString("focusMode", "-1"));

    switch (rawFocusMode) {
      case 0:
        b.focusMode(AbstractCameraActivity.FocusMode.CONTINUOUS);
        break;
      case 1:
        b.focusMode(AbstractCameraActivity.FocusMode.OFF);
        break;
      case 2:
        b.focusMode(AbstractCameraActivity.FocusMode.EDOF);
        break;
    }

    ((Contract)getActivity()).takeVideo(b.build());
  }
}
