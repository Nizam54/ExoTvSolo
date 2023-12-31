/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package cs.nzm.exotv2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.leanback.app.VideoSupportFragment;
import androidx.leanback.app.VideoSupportFragmentGlueHost;
import androidx.leanback.widget.PlaybackControlsRow;

import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.material.snackbar.Snackbar;


public class PlayerFragment extends VideoSupportFragment {

//        private static final String URL = "https://player.vimeo.com/external/263887588.mp4.m3u8";
//    private static final String URL = "https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8";
    private static final String URL = "https://vz-fa6a66b7-f16.b-cdn.net/9c53dea5-510a-4c5d-9682-a82cf3da0c3c/playlist.m3u8";
    private static final String SUBTITLE = "https://vz-fa6a66b7-f16.b-cdn.net/9c53dea5-510a-4c5d-9682-a82cf3da0c3c/captions/EN.vtt";
    public static final String TAG = "VideoConsExoPlayer";
    private VideoMediaPlayerGlue<ExoPlayerAdapter> mMediaPlayerGlue;
    final VideoSupportFragmentGlueHost mHost = new VideoSupportFragmentGlueHost(this);


    AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener
            = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int state) {
        }
    };
    private ExoPlayerAdapter playerAdapter;

    private final int interval = 5000; // 5 Seconds
    private Handler handler = new Handler();
    private Runnable runnable;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playerAdapter = new ExoPlayerAdapter(getActivity());
        playerAdapter.setRepeatAction(PlaybackControlsRow.RepeatAction.INDEX_NONE);
        mMediaPlayerGlue = new VideoMediaPlayerGlue<>(getActivity(), playerAdapter);
        mMediaPlayerGlue.setHost(mHost);
        AudioManager audioManager = (AudioManager) getActivity()
                .getSystemService(Context.AUDIO_SERVICE);
        if (audioManager.requestAudioFocus(mOnAudioFocusChangeListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "video player cannot obtain audio focus!");
        }

        MediaMetaData intentMetaData = getActivity().getIntent().getParcelableExtra(
                PlayerActivity.TAG);
        if (intentMetaData != null) {
            mMediaPlayerGlue.setTitle(intentMetaData.getMediaTitle());
            mMediaPlayerGlue.setSubtitle(intentMetaData.getMediaArtistName());
            if (intentMetaData.getStartPosition() > 0) {
                playerAdapter.initialPosition = intentMetaData.getStartPosition();
            }
            mMediaPlayerGlue.getPlayerAdapter().setDataSource(
                    Uri.parse(intentMetaData.getMediaSourcePath()), intentMetaData.getmMediaSubsUri());
            if (intentMetaData.isLive()) {
                mMediaPlayerGlue.setSeekProvider(null);
                mMediaPlayerGlue.setSeekEnabled(false);
            } else {
                mMediaPlayerGlue.setSeekProvider(new PlaybackSeekMetadataDataProvider(getActivity(), intentMetaData.getMediaSourcePath(), 10000));
            }
            if (intentMetaData.getPositionCallback() != null) {
                runnable = () -> {
                    try {
                        Log.d("Nzm","timer hit:"+playerAdapter.mPlayer.getCurrentPosition());
                        intentMetaData.getPositionCallback().onPositionUpdated(playerAdapter.mPlayer.getCurrentPosition());
                    } finally {
                        handler.postDelayed(runnable, interval);
                    }
                };
            }
        } else {
            mMediaPlayerGlue.setTitle("Clear hls - Angel one");
            mMediaPlayerGlue.setSubtitle("Example with subs and quality");
            mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(URL), Uri.parse(SUBTITLE));
            mMediaPlayerGlue.setSeekProvider(new PlaybackSeekMetadataDataProvider(getActivity(), URL, 10000));
        }
        mMediaPlayerGlue.playWhenPrepared();
        hideControlsOverlay(false);
        setBackgroundType(BG_LIGHT);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        SubtitleView subtitleView = view.findViewById(R.id.leanback_subtitles);
        if (playerAdapter != null) {
            playerAdapter.setSubtitleView(subtitleView);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (runnable != null) {
            handler.postDelayed(runnable, interval);
        }
    }

    @Override
    public void onPause() {
        if (getActivity() != null) {
            SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putLong(PlayerActivity.PLAYER_RESUME_POS, playerAdapter.mPlayer.getCurrentPosition());
            editor.apply();
        }
        if (mMediaPlayerGlue != null) {
            mMediaPlayerGlue.pause();
        }
        if (runnable != null) {
            handler.removeCallbacks(runnable);
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        if (getActivity() != null) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(PlayerActivity.PLAYER_RESUME_POS, playerAdapter.mPlayer.getCurrentPosition());
            getActivity().setResult(Activity.RESULT_OK, resultIntent);
        }
        super.onStop();
    }

    @Override
    protected void onError(int errorCode, CharSequence errorMessage) {
        super.onError(errorCode, errorMessage);
        Snackbar.make(getView(), "Failed to playback", Snackbar.LENGTH_LONG)
                .show();
    }
}
