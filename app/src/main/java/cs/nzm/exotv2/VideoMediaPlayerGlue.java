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
 *
 */

package cs.nzm.exotv2;

import android.app.Activity;

import androidx.leanback.media.PlaybackTransportControlGlue;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.PlaybackControlsRow;

/**
 * PlayerGlue for video playback
 *
 * @param <T>
 */
public class VideoMediaPlayerGlue<T extends PlayerAdapter> extends PlaybackTransportControlGlue<T> {

    private final PlaybackControlsRow.FastForwardAction forwardAction;
    private final PlaybackControlsRow.RewindAction rewindAction;
    private final PlaybackControlsRow.HighQualityAction mQualityAction;
    private final PlaybackControlsRow.ClosedCaptioningAction subtitleAction;
    private final ExoPlayerAdapter adapter;

    public VideoMediaPlayerGlue(Activity context, T impl) {
        super(context, impl);
        adapter = (ExoPlayerAdapter) impl;
        forwardAction = new PlaybackControlsRow.FastForwardAction(context);
        rewindAction = new PlaybackControlsRow.RewindAction(context);
        mQualityAction = new PlaybackControlsRow.HighQualityAction(context);
        subtitleAction = new PlaybackControlsRow.ClosedCaptioningAction(context);
        subtitleAction.setIndex(PlaybackControlsRow.ClosedCaptioningAction.INDEX_ON);
    }

    @Override
    protected void onCreateSecondaryActions(ArrayObjectAdapter adapter) {
        adapter.add(subtitleAction);
        adapter.add(mQualityAction);
    }

    @Override
    protected void onCreatePrimaryActions(ArrayObjectAdapter adapter) {
        adapter.add(rewindAction);
        super.onCreatePrimaryActions(adapter);
        adapter.add(forwardAction);
    }

    @Override
    public void onActionClicked(Action action) {
        if (shouldDispatchAction(action)) {
            dispatchAction(action);
            return;
        }
        super.onActionClicked(action);
    }

    private boolean shouldDispatchAction(Action action) {
        return action == mQualityAction
                || action == rewindAction
                || action == forwardAction
                || action == subtitleAction;
    }

    private void dispatchAction(Action action) {
        if (mQualityAction.equals(action)) {
            adapter.showTrackDialog();
        } else if (forwardAction.equals(action)) {
            adapter.fastForward();
        } else if (rewindAction.equals(action)) {
            adapter.rewind();
        } else {
            if (subtitleAction.equals(action)) {
                adapter.toggleSubs();
            }
            PlaybackControlsRow.MultiAction multiAction = (PlaybackControlsRow.MultiAction) action;
            multiAction.nextIndex();
            notifyActionChanged(multiAction);
        }
    }

    private void notifyActionChanged(PlaybackControlsRow.MultiAction action) {
        int index = -1;
        if (getPrimaryActionsAdapter() != null) {
            index = getPrimaryActionsAdapter().indexOf(action);
        }
        if (index >= 0) {
            getPrimaryActionsAdapter().notifyArrayItemRangeChanged(index, 1);
        } else {
            if (getSecondaryActionsAdapter() != null) {
                index = getSecondaryActionsAdapter().indexOf(action);
                if (index >= 0) {
                    getSecondaryActionsAdapter().notifyArrayItemRangeChanged(index, 1);
                }
            }
        }
    }

    private ArrayObjectAdapter getPrimaryActionsAdapter() {
        if (getControlsRow() == null) {
            return null;
        }
        return (ArrayObjectAdapter) getControlsRow().getPrimaryActionsAdapter();
    }

    private ArrayObjectAdapter getSecondaryActionsAdapter() {
        if (getControlsRow() == null) {
            return null;
        }
        return (ArrayObjectAdapter) getControlsRow().getSecondaryActionsAdapter();
    }

    @Override
    protected void onPlayCompleted() {
        super.onPlayCompleted();
    }

    @Override
    protected void onPreparedStateChanged() {
        super.onPreparedStateChanged();
        if (isPrepared()) {
            ((PlaybackSeekMetadataDataProvider)getSeekProvider()).setDuration(getDuration());
        }
    }
}