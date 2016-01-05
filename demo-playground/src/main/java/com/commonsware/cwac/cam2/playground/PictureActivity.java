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
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

public class PictureActivity extends Activity
    implements PictureFragment.Contract {
  private static final int REQUEST_CAMERA=1337;
  private static final String TAG_PLAYGROUND=PictureFragment.class.getCanonicalName();
  private static final String TAG_RESULT=ResultFragment.class.getCanonicalName();
  private static final String STATE_OUTPUT=
    "com.commonsware.cwac.cam2.playground.PictureActivity.STATE_OUTPUT";
  private PictureFragment playground=null;
  private ResultFragment result=null;
  private Uri output=null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (savedInstanceState!=null) {
      output=savedInstanceState.getParcelable(STATE_OUTPUT);
    }

    if (!Environment.MEDIA_MOUNTED
      .equals(Environment.getExternalStorageState())) {
      Toast
        .makeText(this, "Cannot access external storage!",
          Toast.LENGTH_LONG)
        .show();
      finish();
    }

    playground=(PictureFragment)getFragmentManager().findFragmentByTag(TAG_PLAYGROUND);
    result=(ResultFragment)getFragmentManager().findFragmentByTag(TAG_RESULT);

    if (playground==null) {
      playground=new PictureFragment();
      getFragmentManager()
          .beginTransaction()
          .add(android.R.id.content, playground, TAG_PLAYGROUND)
          .commit();
    }

    if (result==null) {
      result=ResultFragment.newInstance();
      getFragmentManager()
          .beginTransaction()
          .add(android.R.id.content, result, TAG_RESULT)
          .hide(result)
          .commit();
    }

    if (!playground.isVisible() && !result.isVisible()) {
      getFragmentManager()
          .beginTransaction()
          .hide(result)
          .show(playground)
          .commit();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putParcelable(STATE_OUTPUT, output);
  }

  @Override
  public void takePicture(Intent i) {
    startActivityForResult(i, REQUEST_CAMERA);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode==REQUEST_CAMERA) {
      if (resultCode == Activity.RESULT_OK) {
        Bitmap bitmap=data.getParcelableExtra("data");

        if (bitmap == null) {
          result.setImage(output);
        }
        else {
          result.setImage(bitmap);
        }

        getFragmentManager()
            .beginTransaction()
            .hide(playground)
            .show(result)
            .addToBackStack(null)
            .commit();
      }
    }
  }

  @Override
  public void setOutput(Uri uri) {
    output=uri;
  }
}
