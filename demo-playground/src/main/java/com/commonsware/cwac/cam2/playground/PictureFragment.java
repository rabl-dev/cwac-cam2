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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;
import com.commonsware.cwac.cam2.AbstractCameraActivity;
import com.commonsware.cwac.cam2.CameraActivity;
import com.commonsware.cwac.cam2.Facing;
import com.commonsware.cwac.cam2.FlashMode;
import com.commonsware.cwac.cam2.FocusMode;
import com.commonsware.cwac.cam2.ZoomStyle;
import java.io.File;

public class PictureFragment extends PreferenceFragment {
  interface Contract {
    void takePicture(Intent i);
    void setOutput(Uri uri);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    addPreferencesFromResource(R.xml.prefs_picture);
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
    menu.findItem(R.id.video_activity).setVisible(true);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId()==R.id.take_picture) {
      takePicture();

      return(true);
    }
    else if (item.getItemId()==R.id.video_activity) {
      startActivity(new Intent(getActivity(), VideoActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    return super.onOptionsItemSelected(item);
  }

  private void takePicture() {
    SharedPreferences prefs=getPreferenceManager().getSharedPreferences();
    CameraActivity.IntentBuilder b=new CameraActivity.IntentBuilder(getActivity());

    if (!prefs.getBoolean("confirm", false)) {
      b.skipConfirm();
    }

    if (prefs.getBoolean("ffc", false)) {
      b.facing(Facing.FRONT);
    }
    else {
      b.facing(Facing.BACK);
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

    if (prefs.getBoolean("file", false)) {
      File f=new File(getActivity().getExternalFilesDir(null),
        "test.jpg");

      b.to(f);
      ((Contract)getActivity()).setOutput(Uri.fromFile(f));
    }

    if (prefs.getBoolean("mirrorPreview", false)) {
      b.mirrorPreview();
    }

    if (prefs.getBoolean("highQuality", false)) {
      b.quality(AbstractCameraActivity.Quality.HIGH);
    }
    else {
      b.quality(AbstractCameraActivity.Quality.LOW);
    }

    int rawFocusMode=
      Integer.valueOf(prefs.getString("focusMode", "-1"));

    switch (rawFocusMode) {
      case 0:
        b.focusMode(FocusMode.CONTINUOUS);
        break;
      case 1:
        b.focusMode(FocusMode.OFF);
        break;
      case 2:
        b.focusMode(FocusMode.EDOF);
        break;
    }

    if (prefs.getBoolean("debugSavePreview", false)) {
      b.debugSavePreviewFrame();
    }

    int rawFlashMode=
      Integer.valueOf(prefs.getString("flashMode", "-1"));

    switch (rawFlashMode) {
      case 0:
        b.flashMode(FlashMode.OFF);
        break;
      case 1:
        b.flashMode(FlashMode.ALWAYS);
        break;
      case 2:
        b.flashMode(FlashMode.AUTO);
        break;
      case 3:
        b.flashMode(FlashMode.REDEYE);
        break;
    }

    if (prefs.getBoolean("allowSwitchFlashMode", false)) {
      b.allowSwitchFlashMode();
    }

    int rawZoomStyle=
      Integer.valueOf(prefs.getString("zoomStyle", "0"));

    switch (rawZoomStyle) {
      case 1:
        b.zoomStyle(ZoomStyle.PINCH);
        break;

      case 2:
        b.zoomStyle(ZoomStyle.SEEKBAR);
        break;
    }

    String confirmationQuality=prefs.getString("confirmationQuality", null);

    if (confirmationQuality!=null &&
      !"Default".equals(confirmationQuality)) {
      b.confirmationQuality(Float.parseFloat(confirmationQuality));
    }

    b.onError(new ErrorResultReceiver());

    Intent result;

    if (prefs.getBoolean("useChooser", false)) {
      result=b.buildChooser("Choose a picture-taking thingy");
    }
    else {
      result=b.build();
    }

    ((Contract)getActivity()).takePicture(result);
  }

  @SuppressLint("ParcelCreator")
  private class ErrorResultReceiver extends ResultReceiver {
    public ErrorResultReceiver() {
      super(new Handler(Looper.getMainLooper()));
    }

    @Override
    protected void onReceiveResult(int resultCode,
                                   Bundle resultData) {
      super.onReceiveResult(resultCode, resultData);

      if (getActivity()!=null) {
        Toast
          .makeText(getActivity(), "We had an error",
            Toast.LENGTH_LONG)
          .show();
      }
    }
  }
}
