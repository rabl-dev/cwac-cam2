/***
 * Copyright (c) 2015-2016 CommonsWare, LLC
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
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
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
    private static final String ARG_SIZE_LIMIT = "sizeLimit";
    private static final String ARG_DURATION_LIMIT = "durationLimit";
    private static final String ARG_QUALITY = "quality";
    private static final String ARG_ZOOM_STYLE = "zoomStyle";
    private static final int PINCH_ZOOM_DELTA = 20;

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
    private boolean mirrorPreview = false;
    private ScaleGestureDetector scaleDetector;
    private boolean inSmoothPinchZoom = false;

    public static CustomCameraFragment newPictureInstance(Uri output,
                                                          boolean updateMediaStore,
                                                          int quality,
                                                          ZoomStyle zoomStyle,
                                                          String state) {
        CustomCameraFragment f = new CustomCameraFragment();
        Bundle args = new Bundle();

        args.putParcelable(ARG_OUTPUT, output);
        args.putBoolean(ARG_UPDATE_MEDIA_STORE, updateMediaStore);
        args.putInt(ARG_QUALITY, quality);
        args.putBoolean(ARG_IS_VIDEO, false);
        args.putSerializable(ARG_ZOOM_STYLE, zoomStyle);
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
        args.putInt(ARG_QUALITY, quality);
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
        scaleDetector = new ScaleGestureDetector(getActivity(), scaleListener);
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
     * Indicates if we should mirror the preview or not. Defaults
     * to false.
     *
     * @param mirror true if we should horizontally mirror the
     *               preview, false otherwise
     */
    public void setMirrorPreview(boolean mirror) {
        this.mirrorPreview = mirror;
    }

    /**
     * Standard lifecycle method, for when the fragment moves into
     * the stopped state. Passed along to the CameraController.
     */
    @Override
    public void onStop() {
        if (ctlr != null) {
            try {
                ctlr.stop();
            } catch (Exception e) {
                ctlr.postError(ErrorConstants.ERROR_STOPPING, e);
                Log.e(getClass().getSimpleName(), "Exception stopping controller", e);
            }
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

        okButton.setEnabled(false);
        problemButton.setEnabled(false);

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
        ctlr.setQuality(getArguments().getInt(ARG_QUALITY, 1));
    }

    public void shutdown() {
        if (isVideoRecording) {
            stopVideoRecording(true);
        } else {
            progress.setVisibility(View.VISIBLE);

            if (ctlr!=null) {
                try {
                    ctlr.stop();
                } catch (Exception e) {
                    ctlr.postError(ErrorConstants.ERROR_STOPPING, e);
                    Log.e(getClass().getSimpleName(), "Exception stopping controller", e);
                }
            }
        }
    }

    public void onEventMainThread(CameraController.ControllerReadyEvent event) {
        if (event.isEventForController(ctlr)) {
            prepController();
        }
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(CameraEngine.OpenedEvent event) {
        if (event.exception == null) {
            okButton.setEnabled(true);
            problemButton.setEnabled(true);
            progress.setVisibility(View.GONE);

            if (ctlr.supportsZoom()) {
                if (getZoomStyle() == ZoomStyle.PINCH) {
                    previewStack.setOnTouchListener(
                            new View.OnTouchListener() {
                                @Override
                                public boolean onTouch(View v, MotionEvent event) {
                                    return (scaleDetector.onTouchEvent(event));
                                }
                            });
                }
            } else {
                previewStack.setOnTouchListener(null);
            }
        } else {
            ctlr.postError(ErrorConstants.ERROR_OPEN_CAMERA, event.exception);
            getActivity().finish();
        }
    }

    private ZoomStyle getZoomStyle() {
        ZoomStyle result = (ZoomStyle) getArguments().getSerializable(ARG_ZOOM_STYLE);

        if (result == null) {
            result = ZoomStyle.NONE;
        }

        return (result);
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(CameraEngine.VideoTakenEvent event) {
        isVideoRecording = false;

        if (event.exception == null) {
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
        } else if (getActivity().isFinishing()) {
            shutdown();
        } else {
            ctlr.postError(ErrorConstants.ERROR_VIDEO_TAKEN, event.exception);
            getActivity().finish();
        }
    }

    public void onEventMainThread(CameraEngine.SmoothZoomCompletedEvent event) {
        inSmoothPinchZoom = false;
    }

    protected void performCameraAction() {
        takePicture();
    }

    private void takePicture() {
        Uri output = getArguments().getParcelable(ARG_OUTPUT);

        PictureTransaction.Builder b = new PictureTransaction.Builder();

        if (output != null) {
            b.toUri(getActivity(), output,
                    getArguments().getBoolean(ARG_UPDATE_MEDIA_STORE, false));
        }

        okButton.setEnabled(false);
        problemButton.setEnabled(false);
        ctlr.takePicture(b.build());
    }

    private void recordVideo() {
        if (isVideoRecording) {
            stopVideoRecording(false);
        } else {
            try {
                VideoTransaction.Builder b =
                        new VideoTransaction.Builder();
                Uri output = getArguments().getParcelable(ARG_OUTPUT);

                b.to(new File(output.getPath()))
                        .quality(getArguments().getInt(ARG_QUALITY, 1))
                        .sizeLimit(getArguments().getInt(ARG_SIZE_LIMIT, 0))
                        .durationLimit(
                                getArguments().getInt(ARG_DURATION_LIMIT, 0));

                ctlr.recordVideo(b.build());
                isVideoRecording = true;
            } catch (Exception e) {
                ctlr.postError(ErrorConstants.ERROR_STOPPING_VIDEO, e);
                Log.e(getClass().getSimpleName(), "Exception recording video", e);
            }
        }
    }

    private void prepController() {
        LinkedList<CameraView> cameraViews = new LinkedList<CameraView>();
        CameraView cv = (CameraView) previewStack.getChildAt(0);

        cv.setMirror(mirrorPreview);
        cameraViews.add(cv);

        for (int i = 1; i < ctlr.getNumberOfCameras(); i++) {
            cv = new CameraView(getActivity());
            cv.setVisibility(View.INVISIBLE);
            cv.setMirror(mirrorPreview);
            previewStack.addView(cv);
            cameraViews.add(cv);
        }

        ctlr.setCameraViews(cameraViews);
    }

    public void stopVideoRecording() {
        stopVideoRecording(true);
    }

    private void stopVideoRecording(boolean abandon) {
        try {
            ctlr.stopVideoRecording(abandon);
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Exception stopping recording of video", e);
            // TODO: um, do something here
        } finally {
            isVideoRecording = false;
        }
    }

    private ScaleGestureDetector.OnScaleGestureListener scaleListener =
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public void onScaleEnd(ScaleGestureDetector detector) {
                    float scale = detector.getScaleFactor();
                    int delta;

                    if (scale > 1.0f) {
                        delta = PINCH_ZOOM_DELTA;
                    } else if (scale < 1.0f) {
                        delta = -1 * PINCH_ZOOM_DELTA;
                    } else {
                        return;
                    }

                    if (!inSmoothPinchZoom) {
                        if (ctlr.changeZoom(delta)) {
                            inSmoothPinchZoom = true;
                        }
                    }
                }
            };

    private SeekBar.OnSeekBarChangeListener seekListener =
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar,
                                              int progress,
                                              boolean fromUser) {
                    if (fromUser) {
                        if (ctlr.setZoom(progress)) {
                            seekBar.setEnabled(false);
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // no-op
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // no-op
                }
            };

    public enum State {
        NORMAL, OK, PROBLEM, NEUTRAL
    }
}
