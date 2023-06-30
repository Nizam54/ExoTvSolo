package cs.nzm.exotv2;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.AsyncTask;
import android.util.Log;
import android.util.SparseArray;

import androidx.leanback.widget.PlaybackSeekDataProvider;

import java.io.IOException;
import java.util.HashMap;

public class PlaybackSeekMetadataDataProvider extends PlaybackSeekDataProvider {
    private final Context mContext;
    private final String mVideoUrl;
    private long[] mSeekPositions= new long[0];
    private final SparseArray<LoadBitmapAsyncTask> mTasks = new SparseArray<>();
    private final long interval;

    public PlaybackSeekMetadataDataProvider(Context context,
                                            String videoUrl,
                                            long interval) {
        mContext = context;
        mVideoUrl = videoUrl;
        this.interval = interval;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(mVideoUrl, new HashMap<>());

            long duration = Long.parseLong(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

            int size = (int) (duration / interval) + 1;
            mSeekPositions = new long[size];
            for (int i = 0; i < size; i++) {
                mSeekPositions[i] = i * interval;
            }
        } catch (Exception e) {
            //ignore seek thumbnails not supported
        }
    }

    @Override
    public long[] getSeekPositions() {
        return mSeekPositions;
    }

    @Override
    public void reset() {
        for (int i = 0; i < mTasks.size(); i++) {
            LoadBitmapAsyncTask task = mTasks.get(i);
            if (task != null)
                task.cancel(true);
        }
        mTasks.clear();
    }

    @Override
    public void getThumbnail(int index, ResultCallback callback) {
        LoadBitmapAsyncTask task = mTasks.get(index);
        if (task == null) {
            long position = getSeekPositions()[index];
            task = new LoadBitmapAsyncTask(index, mVideoUrl, position, callback);
            mTasks.put(index, task);
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void setDuration(long duration) {
        reset();
        int size = (int) (duration / interval) + 1;
        mSeekPositions = new long[size];
        for (int i = 0; i < size; i++) {
            mSeekPositions[i] = i * interval;
        }
    }

    class LoadBitmapAsyncTask extends AsyncTask<Void, Void, Bitmap> {

        final int mIndex;
        private final String mVideoUrl;
        private final long position;
        final ResultCallback mResultCallback;

        public LoadBitmapAsyncTask(int index, String videoUrl, long position, ResultCallback resultCallback) {
            mIndex = index;
            mVideoUrl = videoUrl;
            this.position = position;
            mResultCallback = resultCallback;
        }

        @Override
        protected Bitmap doInBackground(Void... voids) {
            Bitmap thumbnail = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(mVideoUrl, new HashMap<>());
            Log.d("SeekProvider", "position: " + position);

            thumbnail = retriever.getFrameAtTime(position * 1000);
            
                retriever.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return thumbnail;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            mResultCallback.onThumbnailLoaded(bitmap, mIndex);
            mTasks.remove(mIndex);
        }
    }
}
