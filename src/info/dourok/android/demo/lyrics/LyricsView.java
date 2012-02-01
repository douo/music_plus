package info.dourok.android.demo.lyrics;

import info.dourok.android.demo.lyrics.Lyrics.LyricsItem;
import info.dourok.android.demo.lyrics.Lyrics.LyricsItemNode;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class LyricsView extends View {
	public interface onPositionChangeListener {

		void onPositionChanged(LyricsView lyricsView, int progress,
				boolean fromUser);

		void onStartTouch(LyricsView lyricsView);

		void onStopTouch(LyricsView lyricsView);
	}

	public LyricsView(Context context) {
		super(context);
		initPaint();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		System.out.println("onMeasure");
		float width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft()
				- getPaddingRight();

		if (width != 0 && mLyrics != null) {
			layoutLyrics();
		}
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		/*if (changed) {// FIXME 找到真正宽高确定的地方 XXX
			LyricsDemoActivity.setBackground(this, BitmapFactory
					.decodeResource(getResources(), R.drawable.ic_launcher));
			mLinesBefore = (int) (getHeight() * mCurLinePosition / getLineHeight());
			mLinesAfter = (int) (getHeight() * (1 - mCurLinePosition)
					/ getLineHeight() + 1);
		}*/
	}

	@Override
	protected void onDraw(Canvas canvas) {
		canvas.save();

		canvas.translate(getPaddingLeft(), 0);

		float startHeight = -getCurOffset();
		// 预处理 得到边框内的index上下限
		int s = (mCurLyricsItemIndex < 0 ? 0 : mCurLyricsItemIndex) - 1;
		int lineCounts = 0;
		if (mCurLyricsItemIndex >= 0) {
			while (s >= 0 && lineCounts < mLinesBefore) {
				lineCounts += mLyricsItems.get(s--).mNodeLenght;
			}
		}
		if (s < 0) {
			startHeight += getLineHeight() * (mLinesBefore - lineCounts);
		}
		s++;
		int e = mCurLyricsItemIndex + 1;
		lineCounts = 0;
		while (e < mLyricsItems.size() && lineCounts < mLinesAfter) {
			lineCounts += mLyricsItems.get(e++).mNodeLenght;
		}
		// Log.v("LyricsView.java",
		// "s:" + s + " e:" + e + " size:" + mLyricsItems.size());
		for (int i = s; i < e; i++) {
			Paint p = mNormalPaint;
			if (i == mCurLyricsItemIndex) {
				p = mCurPaint;
			}

			startHeight = drawLyricsItem(mLyricsItems.get(i), p, canvas,
					startHeight);

		}
		canvas.drawLine(0, getContentHeight() * mCurLinePosition,
				getContentWidth(), getContentHeight() * mCurLinePosition,
				mCurPaint);
		canvas.restore();
	}

	private Paint mNormalPaint;
	private Paint mCurPaint;

	private float mCurLinePosition = 0.3f;
	/*
	 * 一旦画笔和宽高和行高确定这些值便不能改变
	 */
	private int mLinesBefore;
	private int mLinesAfter;

	private Lyrics mLyrics;
	private ArrayList<LyricsItem> mLyricsItems;
	private int mCurLyricsItemIndex;
	private int mCurTime;
	// private float mLyricsWidth;
	private float fontHeight;
	private int mLineHeight;
	private int mRealItemLength; // 总的行数
	private int mCurItemLength; // 当前项目所在的行数

	private float mSpacingMult = 1.3f;
	private float mSpacingAdd = 0;

	/**
	 * 唯一更改画笔的地方
	 */
	private void initPaint() {

		mNormalPaint = new Paint();
		mNormalPaint.setTextSize(18f);
		mNormalPaint.setColor(0xffffffff);
		mNormalPaint.setAntiAlias(true);
		mCurPaint = new Paint();
		mCurPaint.setTextSize(18f);
		mCurPaint.setColor(0xfff00000);
		mCurPaint.setAntiAlias(true);
		fontHeight = mNormalPaint.getFontMetricsInt(null);
		fontHeight = mNormalPaint.getFontMetricsInt(null);
		mLineHeight = (int) (fontHeight * mSpacingMult + mSpacingAdd);
		setPadding(20, 50, 20, 10);
	}

	public void setLyrics(Lyrics lyrics) {
		if (this.mLyrics != lyrics) {
			this.mLyrics = lyrics;
			this.mLyricsItems = mLyrics.getLyricsItems();
			layoutLyrics();
			mCurTime = 0;
			mCurLyricsItemIndex = 0;
			mCurItemLength = 0;
		}
	}

	public Lyrics getLyrics() {
		return mLyrics;
	}

	public float getContentWidth() {
		return getWidth() - getPaddingLeft() - getPaddingRight();
	}

	public float getContentHeight() {
		return getHeight() /*- getPaddingTop() - getPaddingBottom() padding on heigh alway ignores*/;
	}

	private void layoutLyrics() {
		layoutLyrics(getContentWidth());
	}

	private void layoutLyrics(float width) {

		if (width <= 0) {
			// if call this method before onMeasure just do nothing
			return;
		}

		mRealItemLength = 0;
		for (LyricsItem item : mLyricsItems) {
			mRealItemLength += splitLyricsItem(item, mNormalPaint, width);
		}

	}

	private static int splitLyricsItem(LyricsItem item, Paint paint, float width) {
		LyricsItemNode node = null;
		LyricsItemNode head = null;
		int i = 0;
		int c = 0; // counter
		String text = item.mText;

		if (text.equals("")) { // 如果是空串则空出一行
			item.setHead(new LyricsItemNode(0, 0f), 1);
			return 1;
		}

		while (i < text.length()) {
			float f[] = new float[1];
			int e = paint.breakText(text, i, text.length(), true, width, f);
			i += e;
			c++;
			if (node == null) {
				node = new LyricsItemNode(i, f[0]);
				head = node;
			} else {
				node.next = new LyricsItemNode(i, f[0]);
				node = node.next;
			}

		}
		item.setHead(head, c);
		return c;
	}

	private float drawLyricsItem(LyricsItem item, Paint paint, Canvas canvas,
			float startHeight) {
		LyricsItemNode node = item.getHeadNode();
		float height = getLineHeight();
		int i = 0;
		String text = item.mText;
		while (node != null) {
			float x = (getContentWidth() - node.width) / 2;
			// System.out.println(text.length()+" :"+ i+" "+ node.pos);
			// System.out.println(text.substring(i, node.pos));

			canvas.drawText(text, i, node.pos, x, startHeight, paint);
			i = node.pos;
			startHeight += height;
			node = node.next;
		}
		return startHeight;
	}

	/**
	 * not thread-safety
	 * 
	 * @param time
	 */
	private void updateCurLyricsItemIndex(int time) {
		this.mCurTime = time;
		if (mLyrics.isHasTimestamp()) {
			if (time > mLyricsItems.get(mCurLyricsItemIndex).mTime) {
				for (int i = mCurLyricsItemIndex + 1; i < mLyricsItems.size(); i++) {

					if (time < mLyricsItems.get(i).mTime) {
						mCurLyricsItemIndex = i - 1;
						return;
					}
					mCurItemLength += mLyricsItems.get(i).mNodeLenght;
				}
				// time > Lyrics.maxtime 显示最后一项
				mCurLyricsItemIndex = mLyricsItems.size() - 1;
			} else {
				// 传入时间比当前时间还要小,一般只可能发生在动态改变歌词offset的时候
				for (int i = mCurLyricsItemIndex - 1; i >= 0; i--) {
					if (time > mLyricsItems.get(i).mTime) {
						mCurLyricsItemIndex = i;
						return;
					}
					mCurItemLength -= mLyricsItems.get(i).mNodeLenght;
				}

				mCurLyricsItemIndex = 0;
			}
		} else {
			// 没有时间戳时,歌词平滑滚动
			int ut = getTotalTime() / mLyrics.getLyricsItems().size();
			mCurLyricsItemIndex = time / ut;
		}

	}

	private int getCurTimeDiffer() {
		if (mLyrics.isHasTimestamp()) {
			int time1 = mLyricsItems.get(mCurLyricsItemIndex).mTime;
			int time2;
			if (mCurLyricsItemIndex + 1 == mLyricsItems.size()) {
				time2 = getTotalTime() > time1 ? getTotalTime() : time1/*不会发生*/;
			} else {
				time2 = mLyricsItems.get(mCurLyricsItemIndex + 1).mTime;
			}
			return time2 - time1;
		}
		return 0;
	}

	private float getCurOffset() {
		if (mCurLyricsItemIndex < 0
				|| mCurLyricsItemIndex >= mLyricsItems.size()
				|| mCurTime > getTotalTime()) {
			return getLineHeight()
					* mLyricsItems.get(mCurLyricsItemIndex).mNodeLenght;
		}
		if (mLyrics.isHasTimestamp()) {
			int time1 = mLyricsItems.get(mCurLyricsItemIndex).mTime;
			int diff = getCurTimeDiffer();
			diff= diff==0?1:diff;  // 避免0除
			return 1.0f*getLineHeight()
					* mLyricsItems.get(mCurLyricsItemIndex).mNodeLenght
					* (mCurTime - time1) / (diff);
		} else {
			int ut = getTotalTime() / mLyrics.getLyricsItems().size();
			return getLineHeight()
					* mLyricsItems.get(mCurLyricsItemIndex).mNodeLenght
					* (mCurTime % ut) / ut;
		}
	}

	private int getLineHeight() {
		return mLineHeight;
	}

	
	// *****TEST*********//
	private static final int REFRESH_LYRICS_POSITION = 1;

	private Handler mHandler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case REFRESH_LYRICS_POSITION:
				updateCurLyricsItemIndex(getTime());
				// System.out.println(mCurLyricsItemIndex);
				invalidate();
				mHandler.sendEmptyMessageDelayed(REFRESH_LYRICS_POSITION, 20L);
			}

		};
	};

	private long stime;

	private int getTime() {
		return (int) (System.currentTimeMillis() - stime) * 10;
	}

	private int getTotalTime() {
		return 5 * 60 * 1000;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// TODO Auto-generated method stub
		if (event.getAction() == MotionEvent.ACTION_UP) {
			if (stime == 0) {
				stime = System.currentTimeMillis();
				mHandler.sendEmptyMessage(REFRESH_LYRICS_POSITION);
			} else {
				mHandler.removeMessages(REFRESH_LYRICS_POSITION);
				stime = 0;
			}
		}
		return true;
	}
}