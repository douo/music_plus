package info.dourok.android.demo.lyrics;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;

public class LyricsDemoActivity extends Activity {
    /** Called when the activity is first created. */
	LyricsView lyricsView;
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	DisplayMetrics metrics = new DisplayMetrics();
    	 getWindowManager().getDefaultDisplay().getMetrics(metrics);
    	System.out.println(metrics);
    	
        super.onCreate(savedInstanceState);
        lyricsView= new LyricsView(this);
        
        try {
			String s = Lyrics.readFile("/sdcard/a.txt",Charset.forName("gbk"));
			lyricsView .setLyrics(Lyrics.renderLyrics(s));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        setContentView(lyricsView);
        
    }
    
    
    static void setBackground(View v, Bitmap bm) {

        if (bm == null) {
            v.setBackgroundResource(0);
            return;
        }

        int vwidth = v.getWidth();
        int vheight = v.getHeight();
        int bwidth = bm.getWidth();
        int bheight = bm.getHeight();
        float scalex = (float) vwidth / bwidth;
        float scaley = (float) vheight / bheight;
        float scale = Math.max(scalex, scaley) * 1.3f;   
        
        //1.3f ?=   b'/b= (a*sin_n+b*cos_n)/b=a/b*sin_n+ cos_n ; n = PI/18 , a>=b
        // 当a > 2b , 1.3f便太小了 , 当专辑的长边大于短边两倍以上时应该用下面式子 ，效率差别不大吧
        //float scale = Math.max(scalex, scaley) * 
        //             (Math.max(bheight, bwidth) / Math.min(bheight, bwidth)*0.174f+0.985f);

        Bitmap.Config config = Bitmap.Config.ARGB_8888;
        Bitmap bg = Bitmap.createBitmap(vwidth, vheight, config);
        Canvas c = new Canvas(bg);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        ColorMatrix greymatrix = new ColorMatrix();
        greymatrix.setSaturation(0);
        ColorMatrix darkmatrix = new ColorMatrix();
        darkmatrix.setScale(.3f, .3f, .3f, 1.0f);
        greymatrix.postConcat(darkmatrix);
        ColorFilter filter = new ColorMatrixColorFilter(greymatrix);
        paint.setColorFilter(filter);
        Matrix matrix = new Matrix();
        matrix.setTranslate(-bwidth/2, -bheight/2); // move bitmap center to origin
        matrix.postRotate(10);    //   rotate
        matrix.postScale(scale, scale);
        matrix.postTranslate(vwidth/2, vheight/2);  // Move bitmap center to view center
        c.drawBitmap(bm, matrix, paint);
        v.setBackgroundDrawable(new BitmapDrawable(bg));
        
    }
    
 // *****TEST*********//
 	private static final int REFRESH_LYRICS_POSITION = 1;

 	private Handler mHandler = new Handler() {
 		public void handleMessage(android.os.Message msg) {
 			switch (msg.what) {
 			case REFRESH_LYRICS_POSITION:
 				lyricsView.updateCurLyricsItemIndex(getTime());
 				// System.out.println(mCurLyricsItemIndex);
 				lyricsView.invalidate();
 				mHandler.sendEmptyMessageDelayed(REFRESH_LYRICS_POSITION, 20L);
 			}

 		};
 	};

 	private long stime;

 	private int getTime() {
 		return (int) (System.currentTimeMillis() - stime) * 10;
 	}

 	
 		@Override
 		public boolean onKeyUp(int keyCode, KeyEvent event) {
 			// TODO Auto-generated method stub
 			System.out.println("onkeyup");
 			return super.onKeyUp(keyCode, event);
 		}
 	@Override
 	public boolean onKeyDown(int keyCode, KeyEvent event) {
 		super.onKeyDown(keyCode, event);
 		System.out.println(keyCode);
 		if (stime == 0) {
 			stime = System.currentTimeMillis();
 			mHandler.sendEmptyMessage(REFRESH_LYRICS_POSITION);
 		} else {
 			mHandler.removeMessages(REFRESH_LYRICS_POSITION);
 			stime = 0;
 		}
 		return true;
 	}
    
}