package com.android.music;

import info.dourok.android.lyrics.Lyric;
import info.dourok.android.lyrics.LyricsException;
import info.dourok.android.lyrics.LyricsProvider.RawLyrics;
import info.dourok.android.lyrics.LyricsProviderManager;
import info.dourok.android.lyrics.LyricsRefreshHandler;
import info.dourok.android.lyrics.LyricsResultListActivity;
import info.dourok.android.lyrics.LyricsStorageManager;
import info.dourok.android.lyrics.LyricsView;
import info.dourok.android.lyrics.SongWrapper;
import info.dourok.android.lyrics.UriSongWrapper;
import info.dourok.musicp.R;

import java.io.IOException;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.android.music.MusicUtils.ServiceToken;

public class LyricsShowActivity extends Activity implements MusicUtils.Defs {
	private IMediaPlaybackService mService = null;
	private ServiceToken mToken;

	private LyricsView mLyricsView;
	private TextView mAlbumTextView;
	private TextView mArtistTextView;
	private TextView mTrackTextView;
	private ViewSwitcher mContainer;
	private Worker mAlbumArtWorker;
	private Worker mSongWorker;
	private AlbumArtHandler mAlbumArtHandler;
	private SongHandler mSongHandler;
	private LyricsRefreshHandler mLyricsRefreshHandler;
	private long mDuration;
	private long mSongId;
	private final static String TAG = "LyricsShow";
	private static final int ALBUM_ART_DECODED = 4;
	public static final int GET_ALBUM_ART = 0;
	public static final int ON_FIND_LYRICS = 10;
	public static final int ON_LYRICS_FOUND = 11;
	public static final int LYRICS_REFRESH = 14;
	public static final int ON_LYRICS_NOT_FOUND = 12;
	public static final int ON_UPDATE_SONG = 2;
	public static final int ON_SEARCH_LYRIC = 15;

	private Lyric mLyric; // FIXME

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		// requestWindowFeature(Window.FEATURE_PROGRESS);
		// mUriSongWrapper = (UriSongWrapperImpl)
		mLyric = (Lyric) getLastNonConfigurationInstance();

		mAlbumArtWorker = new Worker("album art worker");
		mAlbumArtHandler = new AlbumArtHandler(mAlbumArtWorker.getLooper());
		mSongWorker = new Worker("lyrics finder");
		mSongHandler = new SongHandler(mSongWorker.getLooper());

		setContentView(R.layout.lyrics_default_panel);
		mContainer = (ViewSwitcher) findViewById(R.id.lyrics_container);
		mAlbumTextView = (TextView) findViewById(R.id.lyrics_album);
		mArtistTextView = (TextView) findViewById(R.id.lyrics_artist);
		mTrackTextView = (TextView) findViewById(R.id.lyrics_track);
		mLyricsView = (LyricsView) findViewById(R.id.lyrics_view);

		mLyricsRefreshHandler = mLyricsView.buildLyricsRefreshHandler();

		mSongId = -1;
	}

	@Override
	public void onStart() {
		super.onStart();
		mToken = MusicUtils.bindToService(this, osc);
		if (mToken == null) {
			// something went wrong
			Log.d("LyricsShow", "something went wrong");
		}
		IntentFilter f = new IntentFilter();
		f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
		f.addAction(MediaPlaybackService.META_CHANGED);
		registerReceiver(mStatusListener, new IntentFilter(f));
		updateTrackInfo();

	}

	@Override
	protected void onResume() {

		super.onResume();
		updateTrackInfo();
	}

	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		unregisterReceiver(mStatusListener);

		MusicUtils.unbindFromService(mToken);
		mService = null;
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		mSongWorker.quit();
		mAlbumArtWorker.quit();
		super.onDestroy();
	}

	private void busy(boolean busy) {
		setProgressBarIndeterminateVisibility(busy);
		// setProgressBarVisibility(busy);
	}

	// 监听歌曲信息变化
	private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			System.out.println(action + " receive");
			if (action.equals(MediaPlaybackService.META_CHANGED)) {
				updateTrackInfo();
			} else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
				// Log.v(TAG, TAG + "recieve PLAYSTATE_CHANGED");
			}
		}
	};

	//
	// @Override
	public Object onRetainNonConfigurationInstance() {

		// return mUriSongWrapper.getLyrics(); //泄漏前一个 activity 的 引用
		if (mUriSongWrapper != null)
			return mUriSongWrapper.getLyrics();
		return null;
	}

	private void updateTrackInfo() {
		if (mService == null) {
			return;
		}

		try {

			String path = mService.getPath();
			if (path == null) {
				finish();
				return;
			}
			long songid = mService.getAudioId();

			String artistName = mService.getArtistName();
			if (MediaStore.UNKNOWN_STRING.equals(artistName)) {
				artistName = getString(R.string.unknown_artist_name);
			}
			String albumName = mService.getAlbumName();
			long albumid = mService.getAlbumId();
			if (MediaStore.UNKNOWN_STRING.equals(albumName)) {
				albumName = getString(R.string.unknown_album_name);
				albumid = -1;
			}

			String trackName = mService.getTrackName();

			mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
			mAlbumArtHandler.obtainMessage(GET_ALBUM_ART,
					new AlbumSongIdWrapper(albumid, songid)).sendToTarget();

			mAlbumTextView.setText(albumName);
			mArtistTextView.setText(artistName);
			mTrackTextView.setText(trackName);
			setTitle(artistName + " - " + trackName);

			Uri uri;
			uri = Uri.parse(mService.getPath());
			if (mUriSongWrapper == null
					|| !mUriSongWrapper.getUri().equals(uri)) {

			}
			mDuration = mService.duration();

			if (songid != -1 /*&& songid != mSongId*/) {
				busy(true);
				showInfoPanel();
				mSongId = songid;
				mSongHandler.removeMessages(ON_UPDATE_SONG);
				mSongHandler.obtainMessage(ON_UPDATE_SONG, uri).sendToTarget();
			}
			// }
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private ServiceConnection osc = new ServiceConnection() {
		// entry
		public void onServiceConnected(ComponentName classname, IBinder obj) {
			mService = IMediaPlaybackService.Stub.asInterface(obj);

			try {
				// Assume something is playing when the service says it is,
				// but also if the audio ID is valid but the service is paused.

				if (mService.getAudioId() >= 0 || mService.isPlaying()
						|| mService.getPath() != null) {

					// 绑定服务时更新 歌词面板
					updateTrackInfo();
					return;
				}
			} catch (RemoteException ex) {
			}
			// Service is dead or not playing anything. If we got here as part
			// of a "play this file" Intent, exit. Otherwise go to the Music
			// app start screen.
			if (getIntent().getData() == null) {
				Intent intent = new Intent(Intent.ACTION_MAIN);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.setClass(LyricsShowActivity.this,
						MusicBrowserActivity.class);
				startActivity(intent);
			}
			finish();
		}

		public void onServiceDisconnected(ComponentName classname) {
			mService = null;
		}
	};

	private void showInfoPanel() {
		if (mContainer.getCurrentView() == mLyricsView) {
			mContainer.showNext();
		}
	}

	private void showLyricsPanel() {
		if (mContainer.getCurrentView() != mLyricsView) {
			mContainer.showNext();
		}
	}

	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {

			case LYRICS_REFRESH:
				// Log.d("LyricsShow", "LYRICS_REFRESH");
				long time = System.currentTimeMillis();
				mLyricsView.refresh();
				time = System.currentTimeMillis() - time;
				try {
					if (mService != null && mService.isPlaying()) {
						time = mLyricsRefreshHandler.getRefreshTime(time);
						mHandler.sendEmptyMessageDelayed(LYRICS_REFRESH, time);
					}
				} catch (LyricsException e) {
					Log.w(TAG, e);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				break;
			case ON_LYRICS_FOUND:
				Log.d("LyricsShow", "LYRICS_FOUND");
				mLyricsView.setSong(mUriSongWrapper);
				// mLyricsView.updateLyrics();
				busy(false);
				showLyricsPanel();
				mHandler.sendEmptyMessage(LYRICS_REFRESH);
				break;
			case ON_LYRICS_NOT_FOUND:
				Log.d("LyricsShow", "LYRICS_NOT_FOUND");
				busy(false);
				showInfoPanel();
				break;
			case ALBUM_ART_DECODED:
				MusicUtils.setBackground(mContainer, (Bitmap) msg.obj);
				break;
			default:
				break;
			}
		}
	};

	UriSongWrapperImpl mUriSongWrapper;

	private final class UriSongWrapperImpl extends UriSongWrapper {

		public UriSongWrapperImpl(Uri uri, Context context) {
			super(uri, context);

		}

		@Override
		public int getTotalTime() {
			return (int) mDuration;
		}

		@Override
		public int getCurentTime() {

			try {
				if (mService != null)
					return (int) mService.position();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return -1;
		}

		@Override
		public boolean isPlaying() {
			try {
				if (mService != null)
					return mService.isPlaying();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return false;
		}

		public boolean haveLyrics() {
			return mLyrics != null;
		}
	}

	public class AlbumArtHandler extends Handler {
		private long mAlbumId = -1;

		public AlbumArtHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			long albumid = ((AlbumSongIdWrapper) msg.obj).albumid;
			long songid = ((AlbumSongIdWrapper) msg.obj).songid;
			if (msg.what == GET_ALBUM_ART
					&& (mAlbumId != albumid || albumid < 0)) {
				// while decoding the new image, show the default album art
				Message numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, null);
				mHandler.removeMessages(ALBUM_ART_DECODED);
				mHandler.sendMessageDelayed(numsg, 300);
				Bitmap bm = MusicUtils.getArtwork(LyricsShowActivity.this,
						songid, albumid);
				if (bm == null) {
					bm = MusicUtils.getArtwork(LyricsShowActivity.this, songid,
							-1);
					albumid = -1;
				}
				if (bm != null) {

					numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, bm);
					mHandler.removeMessages(ALBUM_ART_DECODED);
					mHandler.sendMessage(numsg);
				}
				mAlbumId = albumid;
			}
		}
	}

	public class SongHandler extends Handler {

		public SongHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {

			switch (msg.what) {
			case ON_FIND_LYRICS:
				Log.d("LyricsShow", "FIND_LYRICS");
				SongWrapper songWrapper = (SongWrapper) msg.obj;
				String src = LyricsStorageManager.searchLyrics(songWrapper);
				if (src == null) {
					mHandler.removeMessages(ON_LYRICS_FOUND);
					mHandler.sendEmptyMessage(ON_LYRICS_NOT_FOUND);
				} else {
					Message numsg = mHandler.obtainMessage(ON_LYRICS_FOUND,
							null);
					mHandler.removeMessages(ON_LYRICS_FOUND);
					songWrapper.setLyrics(Lyric.renderLyrics(src));
					mHandler.sendMessage(numsg);
				}
				break;
			case ON_SEARCH_LYRIC:
				SongWrapper wrapper = (SongWrapper) msg.obj;
				RawLyrics lyric = LyricsProviderManager.getInstance().best(wrapper);
				if(lyric !=null){
					Message numsg = mHandler.obtainMessage(ON_LYRICS_FOUND,
							null);
					mHandler.removeMessages(ON_LYRICS_FOUND);
					wrapper.setLyrics(Lyric.renderLyrics(lyric.mRaw));
					mHandler.sendMessage(numsg);
					try {
						LyricsStorageManager.storage.write(wrapper, lyric.mRaw);
						Toast.makeText(LyricsShowActivity.this, "Saved", 3000).show();
					} catch (IOException e) {
						Toast.makeText(LyricsShowActivity.this, "save failure", 3000).show();
						e.printStackTrace();
					}
				}
				break;
			case ON_UPDATE_SONG:
				// if this msg receive meed
				// song must be update
				try {
					Log.d("LyricsShow", "UPDATE_SONG");
					Uri uri = (Uri) msg.obj;
					if (mUriSongWrapper == null) {
						mUriSongWrapper = new UriSongWrapperImpl(
								Uri.parse(mService.getPath()),
								LyricsShowActivity.this);

					} else {
						mUriSongWrapper.update(uri, LyricsShowActivity.this);
					}
					if (mLyric == null) {
						Log.d(TAG, mUriSongWrapper.getLyrics() + " lyrics");
						mSongHandler.removeMessages(ON_FIND_LYRICS);
						mSongHandler.obtainMessage(ON_FIND_LYRICS,
								mUriSongWrapper).sendToTarget();
					} else {
						mUriSongWrapper.setLyrics(mLyric); // 如果上次activity销毁前保存了lyrics
															// 则恢复它 FIXME 不能保证
															// lyrics的正确性
						mLyric = null;
						Message numsg = mHandler.obtainMessage(ON_LYRICS_FOUND,
								null);
						mHandler.removeMessages(ON_LYRICS_FOUND);
						mHandler.sendMessage(numsg);
					}

				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			}
		}
	}

	private static class AlbumSongIdWrapper {
		public long albumid;
		public long songid;

		AlbumSongIdWrapper(long aid, long sid) {
			albumid = aid;
			songid = sid;
		}
	}

	private static class Worker implements Runnable {
		private final Object mLock = new Object();
		private Looper mLooper;

		/**
		 * Creates a worker thread with the given name. The thread then runs a
		 * {@link android.os.Looper}.
		 * 
		 * @param name
		 *            A name for the new thread
		 */
		Worker(String name) {
			Thread t = new Thread(null, this, name);
			t.setPriority(Thread.MIN_PRIORITY);
			t.start();
			synchronized (mLock) {
				while (mLooper == null) {
					try {
						mLock.wait();
					} catch (InterruptedException ex) {
					}
				}
			}
		}

		public Looper getLooper() {
			return mLooper;
		}

		public void run() {
			synchronized (mLock) {
				Looper.prepare();
				mLooper = Looper.myLooper();
				mLock.notifyAll();
			}
			Looper.loop();
		}

		public void quit() {
			mLooper.quit();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case AUTO_SEARCH_LYRIC:
			busy(true);
			Message msg =  mSongHandler.obtainMessage(ON_SEARCH_LYRIC, mUriSongWrapper);
			mSongHandler.removeMessages(ON_SEARCH_LYRIC);
			msg.sendToTarget();
			break;
		case SEARCH_LYRIC:
			Intent i = new Intent(this, LyricsResultListActivity.class);
			i.putExtra(LyricsResultListActivity.KEY_SONGWRAPPER,
					mUriSongWrapper);
			startActivity(i);
			return true;
		default:

		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, AUTO_SEARCH_LYRIC, 0, "Auto Search");
		menu.add(0, SEARCH_LYRIC, 0, "Search...");

		return true;
	}
}
