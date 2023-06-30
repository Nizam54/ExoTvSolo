/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cs.nzm.exotv2;

import static com.google.android.exoplayer2.C.TRACK_TYPE_VIDEO;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;

import androidx.leanback.media.PlaybackGlueHost;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.media.SurfaceHolderGlueHost;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;


/**
 * This implementation extends the {@link PlayerAdapter} with a {@link SimpleExoPlayer}.
 */
public class ExoPlayerAdapter extends PlayerAdapter implements Player.Listener {

    Context mContext;
    final ExoPlayer mPlayer;
    boolean hasSubsTrack;
    SubtitleView subtitleView;
    SurfaceHolderGlueHost mSurfaceHolderGlueHost;
    final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            getCallback().onCurrentPositionChanged(ExoPlayerAdapter.this);
            getCallback().onBufferedPositionChanged(ExoPlayerAdapter.this);
            mHandler.postDelayed(this, getUpdatePeriod());
        }
    };
    final Handler mHandler = new Handler();
    boolean mInitialized = false;
    Uri mMediaSourceUri = null;
    Uri mMediaSubsUri = null;
    boolean mHasDisplay;
    boolean mBufferingStart = true;
    @C.StreamType int mAudioStreamType;

    /**
     * Constructor.
     */
    public ExoPlayerAdapter(Context context) {
        mContext = context;
        mPlayer = new ExoPlayer.Builder(mContext,
                new DefaultRenderersFactory(mContext),
                new DefaultMediaSourceFactory(mContext)
        ).build();
        mPlayer.addListener(this);
    }

    @Override
    public void onAttachedToHost(PlaybackGlueHost host) {
        if (host instanceof SurfaceHolderGlueHost) {
            mSurfaceHolderGlueHost = ((SurfaceHolderGlueHost) host);
            mSurfaceHolderGlueHost.setSurfaceHolderCallback(new VideoPlayerSurfaceHolderCallback());
        }
        getCallback().onBufferingStateChanged(ExoPlayerAdapter.this, true);
    }

    /**
     * Will reset the {@link ExoPlayer} and the glue such that a new file can be played. You are
     * not required to call this method before playing the first file. However you have to call it
     * before playing a second one.
     */
    public void reset() {
        changeToUninitialized();
        mPlayer.stop();
    }

    void changeToUninitialized() {
        if (mInitialized) {
            mInitialized = false;
            notifyBufferingStartEnd();
            if (mHasDisplay) {
                getCallback().onPreparedStateChanged(ExoPlayerAdapter.this);
            }
        }
    }

    /**
     * Notify the state of buffering. For example, an app may enable/disable a loading figure
     * according to the state of buffering.
     */
    void notifyBufferingStartEnd() {
        getCallback().onBufferingStateChanged(ExoPlayerAdapter.this,
                mBufferingStart || !mInitialized);
    }

    /**
     * Release internal {@link SimpleExoPlayer}. Should not use the object after call release().
     */
    public void release() {
        changeToUninitialized();
        mHasDisplay = false;
        mPlayer.release();
    }

    @Override
    public void onDetachedFromHost() {
        if (mSurfaceHolderGlueHost != null) {
            mSurfaceHolderGlueHost.setSurfaceHolderCallback(null);
            mSurfaceHolderGlueHost = null;
        }
        reset();
        release();
    }

    /**
     * @see SimpleExoPlayer#setVideoSurfaceHolder(SurfaceHolder)
     */
    void setDisplay(SurfaceHolder surfaceHolder) {
        boolean hadDisplay = mHasDisplay;
        mHasDisplay = surfaceHolder != null;
        if (hadDisplay == mHasDisplay) {
            return;
        }

        mPlayer.setVideoSurfaceHolder(surfaceHolder);
        if (mHasDisplay) {
            if (mInitialized) {
                getCallback().onPreparedStateChanged(ExoPlayerAdapter.this);
            }
        } else {
            if (mInitialized) {
                getCallback().onPreparedStateChanged(ExoPlayerAdapter.this);
            }
        }
    }

    @Override
    public void setProgressUpdatingEnabled(final boolean enabled) {
        mHandler.removeCallbacks(mRunnable);
        if (!enabled) {
            return;
        }
        mHandler.postDelayed(mRunnable, getUpdatePeriod());
    }

    int getUpdatePeriod() {
        return 16;
    }

    @Override
    public boolean isPlaying() {
        boolean exoPlayerIsPlaying = mPlayer.getPlaybackState() == ExoPlayer.STATE_READY
                && mPlayer.getPlayWhenReady();
        return mInitialized && exoPlayerIsPlaying;
    }

    @Override
    public long getDuration() {
        return mInitialized ? mPlayer.getDuration() : -1;
    }

    @Override
    public long getCurrentPosition() {
        return mInitialized ? mPlayer.getCurrentPosition() : -1;
    }

    @Override
    public void play() {
        if (!mInitialized || isPlaying()) {
            return;
        }

        mPlayer.setPlayWhenReady(true);
        getCallback().onPlayStateChanged(ExoPlayerAdapter.this);
        getCallback().onCurrentPositionChanged(ExoPlayerAdapter.this);
    }

    @Override
    public void pause() {
        if (isPlaying()) {
            mPlayer.setPlayWhenReady(false);
            getCallback().onPlayStateChanged(ExoPlayerAdapter.this);
        }
    }

    @Override
    public void seekTo(long newPosition) {
        if (!mInitialized) {
            return;
        }
        mPlayer.seekTo(newPosition);
    }

    @Override
    public void fastForward() {
        if (!mInitialized) {
            return;
        }
        mPlayer.seekTo(getCurrentPosition() + 10_000);
    }

    @Override
    public void rewind() {
        if (!mInitialized) {
            return;
        }
        mPlayer.seekTo(getCurrentPosition() - 10_000);
    }

    @Override
    public long getBufferedPosition() {
        return mPlayer.getBufferedPosition();
    }

    public Context getContext() {
        return mContext;
    }

    /**
     * Sets the media source of the player with a given URI.
     *
     * @return Returns <code>true</code> if uri represents a new media; <code>false</code>
     * otherwise.
     * @see ExoPlayer#prepare(MediaSource)
     */
    public boolean setDataSource(Uri uri, Uri subsUri) {
        if (mMediaSourceUri != null ? mMediaSourceUri.equals(uri) : uri == null) {
            return false;
        }
        mMediaSourceUri = uri;
        mMediaSubsUri = subsUri;
        prepareMediaForPlaying();
        return true;
    }

    public int getAudioStreamType() {
        return mAudioStreamType;
    }

    public void setAudioStreamType(@C.StreamType int audioStreamType) {
        mAudioStreamType = audioStreamType;
    }

    /**
     * Set {@link MediaSource} for {@link ExoPlayer}. An app may override this method in order
     * to use different {@link MediaSource}.
     *
     * @param uri The url of media source
     * @return MediaSource for the player
     */
    public MediaSource onCreateMediaSource(Uri uri, Uri subsUri) {
        String userAgent = Util.getUserAgent(mContext, "ExoPlayerAdapter");
        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder().setUri(uri);
        if (subsUri != null) {
            MediaItem.SubtitleConfiguration subtitle = new MediaItem.SubtitleConfiguration.Builder(subsUri)
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage("en")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_AUTOSELECT)
                    .build();
            mediaItemBuilder.setSubtitleConfigurations(ImmutableList.of(subtitle));
        }
        return new DefaultMediaSourceFactory(mContext).createMediaSource(mediaItemBuilder.build());
    }

    private void prepareMediaForPlaying() {
        reset();
        if (mMediaSourceUri != null) {
            MediaSource mediaSource = onCreateMediaSource(mMediaSourceUri, mMediaSubsUri);
            mPlayer.setMediaSource(mediaSource);
            mPlayer.prepare();
        } else {
            return;
        }
        notifyBufferingStartEnd();
        getCallback().onPlayStateChanged(ExoPlayerAdapter.this);
    }

    /**
     * @return True if ExoPlayer is ready and got a SurfaceHolder if
     * {@link PlaybackGlueHost} provides SurfaceHolder.
     */
    @Override
    public boolean isPrepared() {
        return mInitialized && (mSurfaceHolderGlueHost == null || mHasDisplay);
    }

    public void showTrackDialog() {
        Dialog track_selector = new TrackSelectionDialogBuilder(mContext, "Track Selector", mPlayer, TRACK_TYPE_VIDEO)
                .setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight_Dialog)
                .build();
        track_selector.show();
    }

    public void toggleSubs() {
        if (subtitleView != null) {
            subtitleView.setVisibility(subtitleView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        }
    }

    public void setSubtitleView(SubtitleView subtitleView) {
        this.subtitleView = subtitleView;
    }

    /**
     * Implements {@link SurfaceHolder.Callback} that can then be set on the
     * {@link PlaybackGlueHost}.
     */
    class VideoPlayerSurfaceHolderCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            setDisplay(surfaceHolder);
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            setDisplay(null);
        }
    }

    @Override
    public void onCues(CueGroup cueGroup) {
        Player.Listener.super.onCues(cueGroup);
        if (subtitleView != null) {
            subtitleView.setCues(cueGroup.cues);
        }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        Player.Listener.super.onPlayerError(error);
        Toast.makeText(mContext, "Playback Error!!"+error.getMessage(), Toast.LENGTH_LONG).show();
        mBufferingStart = false;
        notifyBufferingStartEnd();
    }

    @Override
    public void onTracksChanged(Tracks tracks) {
        Player.Listener.super.onTracksChanged(tracks);
        hasSubsTrack = false;
        for (Tracks.Group tracksGroup : tracks.getGroups()) {
            if (tracksGroup.getType() == C.TRACK_TYPE_TEXT &&
                    tracksGroup.isSelected()) {
                hasSubsTrack = true;
                break;
            }
        }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        Player.Listener.super.onPlaybackStateChanged(playbackState);
        mBufferingStart = false;
        getCallback().onPlayStateChanged(ExoPlayerAdapter.this);
        if (playbackState == ExoPlayer.STATE_READY && !mInitialized) {
            mInitialized = true;
            if (mSurfaceHolderGlueHost == null || mHasDisplay) {
                getCallback().onPreparedStateChanged(ExoPlayerAdapter.this);
            }
        } else if (playbackState == ExoPlayer.STATE_BUFFERING) {
            mBufferingStart = true;
        } else if (playbackState == ExoPlayer.STATE_ENDED) {
            getCallback().onPlayCompleted(ExoPlayerAdapter.this);
        }
        notifyBufferingStartEnd();
    }

}
