/**
 * Copyright (c) 2015 CommonsWare, LLC
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.commonsware.cwac.cam2;

import android.app.ActionBar;
import android.app.Fragment;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.LinkedList;

import de.greenrobot.event.EventBus;

/**
 * Fragment for displaying a camera preview, with hooks to allow
 * you (or the user) to take a picture.
 */
public class CustomCameraFragment extends Fragment {
    private static final String ARG_OUTPUT = "output";
    private static final String ARG_STATE = "state";
    private static final String ARG_UPDATE_MEDIA_STORE = "updateMediaStore";
    private static final String ARG_IS_VIDEO = "isVideo";
    private static final String ARG_VIDEO_QUALITY = "quality";
    private static final String ARG_SIZE_LIMIT = "sizeLimit";
    private static final String ARG_DURATION_LIMIT = "durationLimit";
    private CameraController ctlr;
    private LinearLayout okButton;
    private LinearLayout problemButton;
    private TextView okButtonText;
    private TextView problemButtonText;
    private ViewGroup previewStack;
    private View progress;
    private boolean neutralMode;
    private boolean isVideoRecording = false;
    private State state;
    public static String outPutState = "";

    public static CustomCameraFragment newPictureInstance(Uri output,
                                                          boolean updateMediaStore, String state) {
        CustomCameraFragment f = new CustomCameraFragment();
        Bundle args = new Bundle();

        args.putParcelable(ARG_OUTPUT, output);
        args.putBoolean(ARG_UPDATE_MEDIA_STORE, updateMediaStore);
        args.putBoolean(ARG_IS_VIDEO, false);
        args.putString(ARG_STATE, state);
        f.setArguments(args);

        return (f);
    }

    public static CustomCameraFragment newVideoInstance(Uri output,
                                                        boolean updateMediaStore,
                                                        int quality, int sizeLimit,
                                                        int durationLimit) {
        CustomCameraFragment f = new CustomCameraFragment();
        Bundle args = new Bundle();

        args.putParcelable(ARG_OUTPUT, output);
        args.putBoolean(ARG_UPDATE_MEDIA_STORE, updateMediaStore);
        args.putBoolean(ARG_IS_VIDEO, true);
        args.putInt(ARG_VIDEO_QUALITY, quality);
        args.putInt(ARG_SIZE_LIMIT, sizeLimit);
        args.putInt(ARG_DURATION_LIMIT, durationLimit);
        f.setArguments(args);

        return (f);
    }

    /**
     * Standard fragment entry point.
     *
     * @param savedInstanceState State of a previous instance
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        String stringState = getArguments().getString(ARG_STATE);
        if (TextUtils.isEmpty(stringState)) {
            state = State.NORMAL;
        } else {
            state = State.valueOf(stringState);
        }
    }

    /**
     * Standard lifecycle method, passed along to the CameraController.
     */
    @Override
    public void onStart() {
        super.onStart();

        EventBus.getDefault().register(this);

        if (ctlr != null) {
            ctlr.start();
        }
    }

    @Override
    public void onHiddenChanged(boolean isHidden) {
        super.onHiddenChanged(isHidden);

        if (!isHidden) {
            ActionBar ab = getActivity().getActionBar();

            if (ab != null) {
                ab.setBackgroundDrawable(getActivity()
                        .getResources()
                        .getDrawable(R.drawable.cwac_cam2_action_bar_bg_transparent));
                ab.setTitle("");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ab.setDisplayHomeAsUpEnabled(false);
                } else {
                    ab.setDisplayShowHomeEnabled(false);
                    ab.setHomeButtonEnabled(false);
                }
            }

            if (okButton != null) {
                okButton.setEnabled(true);
            }

            if (problemButton != null) {
                problemButton.setEnabled(true);
            }
        }
    }

    /**
     * Standard lifecycle method, for when the fragment moves into
     * the stopped state. Passed along to the CameraController.
     */
    @Override
    public void onStop() {
        if (ctlr != null) {
            ctlr.stop();
        }

        EventBus.getDefault().unregister(this);

        super.onStop();
    }

    /**
     * Standard lifecycle method, for when the fragment is utterly,
     * ruthlessly destroyed. Passed along to the CameraController,
     * because why should the fragment have all the fun?
     */
    @Override
    public void onDestroy() {
        if (ctlr != null) {
            ctlr.destroy();
        }

        super.onDestroy();
    }

    /**
     * Standard callback method to create the UI managed by
     * this fragment.
     *
     * @param inflater           Used to inflate layouts
     * @param container          Parent of the fragment's UI (eventually)
     * @param savedInstanceState State of a previous instance
     * @return the UI being managed by this fragment
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.cwac_fragment_camera, container, false);

        previewStack = (ViewGroup) v.findViewById(R.id.cwac_cam2_preview_stack);
        progress = v.findViewById(R.id.cwac_cam2_progress);

        okButton = (LinearLayout) v.findViewById(R.id.btn_camera_ok);
        problemButton = (LinearLayout) v.findViewById(R.id.btn_camera_problem);
        okButtonText = (TextView) v.findViewById(R.id.tv_camera_ok);
        problemButtonText = (TextView) v.findViewById(R.id.tv_camera_problem);

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                outPutState = State.OK.name();
                performCameraAction();
            }
        });

        problemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (neutralMode) {
                    outPutState = State.NEUTRAL.name();
                } else {
                    outPutState = State.PROBLEM.name();
                }
                performCameraAction();
            }
        });

        //changeMenuIconAnimation((FloatingActionMenu) v.findViewById(R.id.cwac_cam2_settings));

        onHiddenChanged(false); // hack, since this does not get
        // called on initial display

        okButton.setClickable(false);
        problemButton.setClickable(false);

        // Set buttons and texts if we already have a status
        switch (state) {
            case NORMAL:
                break;
            case OK:
                okButton.setPadding(0, 0, 0, 0);
                okButtonText.setText(R.string.camera_ok_cont);
                problemButton.setVisibility(View.GONE);
                break;
            case PROBLEM:
                problemButton.setPadding(0, 0, 0, 0);
                problemButtonText.setText(R.string.camera_problem_cont);
                okButton.setVisibility(View.GONE);
                break;
            case NEUTRAL:
                neutralMode = true;
                okButton.setPadding(0, 0, 0, 0);
                okButtonText.setCompoundDrawablesWithIntrinsicBounds(0,
                        R.drawable.ic_camera_neutral, 0, 0);
                okButtonText.setText("");
                problemButton.setVisibility(View.GONE);
                break;
        }

        if (ctlr != null && ctlr.getNumberOfCameras() > 0) {
            prepController();
        }

        return (v);
    }

    /**
     * @return the CameraController this fragment delegates to
     */
    public CameraController getController() {
        return (ctlr);
    }

    /**
     * Establishes the controller that this fragment delegates to
     *
     * @param ctlr the controller that this fragment delegates to
     */
    public void setController(CameraController ctlr) {
        this.ctlr = ctlr;
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(CameraController.ControllerReadyEvent event) {
        if (event.isEventForController(ctlr)) {
            prepController();
        }
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(CameraEngine.OpenedEvent event) {
        progress.setVisibility(View.GONE);
        okButton.setClickable(true);
        problemButton.setClickable(true);
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(CameraEngine.VideoTakenEvent event) {
        if (getArguments().getBoolean(ARG_UPDATE_MEDIA_STORE, false)) {
            final Context app = getActivity().getApplicationContext();
            Uri output = getArguments().getParcelable(ARG_OUTPUT);
            final String path = output.getPath();

            new Thread() {
                @Override
                public void run() {
                    SystemClock.sleep(2000);
                    MediaScannerConnection.scanFile(app,
                            new String[]{path}, new String[]{"video/mp4"},
                            null);
                }
            }.start();
        }

        isVideoRecording = false;
        // TODO what?
//        fabPicture.setImageResource(R.drawable.cwac_cam2_ic_videocam);
//        fabPicture.setColorNormalResId(R.color.cwac_cam2_picture_fab);
//        fabPicture.setColorPressedResId(R.color.cwac_cam2_picture_fab_pressed);
    }

    protected void performCameraAction() {
        if (isVideo()) {
            recordVideo();
        } else {
            takePicture();
        }
    }

    private void takePicture() {
        Uri output = getArguments().getParcelable(ARG_OUTPUT);

        PictureTransaction.Builder b = new PictureTransaction.Builder();

        if (output != null) {
            b.toUri(getActivity(), output,
                    getArguments().getBoolean(ARG_UPDATE_MEDIA_STORE, false));
        }

        okButton.setClickable(false);
        problemButton.setClickable(false);
        ctlr.takePicture(b.build());
    }

    private void recordVideo() {
        if (isVideoRecording) {
            try {
                ctlr.stopVideoRecording();
            } catch (Exception e) {
                Log.e(getClass().getSimpleName(), "Exception stopping recording of video", e);
                // TODO: um, do something here
            }
        } else {
            try {
                VideoTransaction.Builder b = new VideoTransaction.Builder();
                Uri output = getArguments().getParcelable(ARG_OUTPUT);

                b.to(new File(output.getPath()))
                        .quality(getArguments().getInt(ARG_VIDEO_QUALITY, 1))
                        .sizeLimit(getArguments().getInt(ARG_SIZE_LIMIT, 0))
                        .durationLimit(getArguments().getInt(ARG_DURATION_LIMIT, 0));

                ctlr.recordVideo(b.build());
                isVideoRecording = true;
                // TODO what?
//                fabPicture.setImageResource(R.drawable.cwac_cam2_ic_stop);
//                fabPicture.setColorNormalResId(R.color.cwac_cam2_recording_fab);
//                fabPicture.setColorPressedResId(R.color.cwac_cam2_recording_fab_pressed);
            } catch (Exception e) {
                Log.e(getClass().getSimpleName(), "Exception recording video", e);
                // TODO: um, do something here
            }
        }
    }

    private boolean isVideo() {
        return (getArguments().getBoolean(ARG_IS_VIDEO, false));
    }

    private void prepController() {
        LinkedList<CameraView> cameraViews = new LinkedList<CameraView>();
        CameraView cv = (CameraView) previewStack.getChildAt(0);

        cameraViews.add(cv);

        for (int i = 1; i < ctlr.getNumberOfCameras(); i++) {
            cv = new CameraView(getActivity());
            cv.setVisibility(View.INVISIBLE);
            previewStack.addView(cv);
            cameraViews.add(cv);
        }

        ctlr.setCameraViews(cameraViews);
    }

    public enum State {
        NORMAL, OK, PROBLEM, NEUTRAL
    }
}
