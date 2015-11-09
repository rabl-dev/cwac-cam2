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
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.VideoView;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class VideoPlayerActivity extends Activity
  implements MediaPlayer.OnCompletionListener {
  private static final String STATE_OFFSET=
    "com.commonsware.cwac.cam2.playground.STATE_OFFSET";
  private VideoView vv;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    vv=new VideoView(this, null);
    setContentView(vv,
      new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT,
        Gravity.CENTER));
    vv.setVideoURI(getIntent().getData());
    vv.setOnCompletionListener(this);
    vv.start();

    if (savedInstanceState!=null) {
      vv.seekTo(savedInstanceState.getInt(STATE_OFFSET, 0));
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putInt(STATE_OFFSET, vv.getCurrentPosition());
  }

  @Override
  protected void onResume() {
    super.onResume();

    vv.resume();
  }

  @Override
  protected void onPause() {
    vv.pause();

    super.onPause();
  }

  @Override
  protected void onDestroy() {
    vv.stopPlayback();

    super.onDestroy();
  }

  @Override
  public void onCompletion(MediaPlayer mp) {
    finish();
  }
}
