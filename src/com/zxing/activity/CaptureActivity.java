package com.zxing.activity;

import java.io.IOException;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.example.zxing.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.zxing.camera.CameraManager;
import com.zxing.decoding.CaptureActivityHandler;
import com.zxing.decoding.InactivityTimer;
import com.zxing.view.ViewfinderView;
/**
 * 
 * 扫描
 */
public class CaptureActivity extends Activity implements Callback ,android.os.Handler.Callback{
	private CaptureActivityHandler handler;
	private ViewfinderView viewfinderView;
	private boolean hasSurface;
	private Vector<BarcodeFormat> decodeFormats;
	private String characterSet;
	private InactivityTimer inactivityTimer;
	private MediaPlayer mediaPlayer;
	private boolean playBeep;
	private static final float BEEP_VOLUME = 0.10f;
	private boolean vibrate;
	Handler mHandler = new Handler(this);
	private String strResult;
	private Map<String, String> cMap = null;
	private Map<String, String> joinMap = null;
	private long oldShowTime = 0;
	public static int cameraHeight;
	public static int sWidth;
	public static int sHeight;
	private String strurl;
//	private Button cancelScanButton;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.camera);
		Rect frame = new Rect();
	   	getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
	    int statusBarHeight = frame.top;
	    WindowManager manager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
	    Display display = manager.getDefaultDisplay();
	    sWidth = display.getWidth();
	    sHeight = display.getHeight();
	    cameraHeight = display.getHeight()-statusBarHeight;
		//ViewUtil.addTopView(getApplicationContext(), this, R.string.scan_card);
		CameraManager.init(getApplication());
		viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
//		cancelScanButton = (Button) this.findViewById(R.id.btn_cancel_scan);
		hasSurface = false;
		inactivityTimer = new InactivityTimer(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			initCamera(surfaceHolder);
		} else {
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
		decodeFormats = null;
		characterSet = null;

		playBeep = true;
		AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
			playBeep = false;
		}
		initBeepSound();
		vibrate = true;
		
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		CameraManager.get().closeDriver();
	}
	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}
	
	/**
	 * Handler scan result
	 * @param result
	 * @param barcode
	 */
	public void handleDecode(Result result, Bitmap barcode) {
		inactivityTimer.onActivity();
		strResult= result.getText();
		Log.e("result", ""+strResult);
		if (strResult==null || strResult.equals("") ) {
			oldShowTime = System.currentTimeMillis();
			Toast.makeText(CaptureActivity.this, "无效二维码", Toast.LENGTH_SHORT).show();
			if (handler == null) {
				handler = new CaptureActivityHandler(this, decodeFormats,
						characterSet);
			}
			handler.sendEmptyMessage(R.id.restart_preview);
		}else {
//			System.out.println("Result:"+resultString);
//			Intent resultIntent = new Intent();
//			Bundle bundle = new Bundle();
//			bundle.putString("result", resultString);
//			resultIntent.putExtras(bundle);
//			this.setResult(RESULT_OK, resultIntent);
			playBeepSoundAndVibrate();
			mHandler.sendEmptyMessage(1);
		}
//		CaptureActivity.this.finish();
	}
	public static boolean isNumeric(String str){ 
	     Pattern pattern = Pattern.compile("[0-9]*"); 
	     return pattern.matcher(str).matches();    
	} 
	private void initCamera(SurfaceHolder surfaceHolder) {
		try {
			CameraManager.get().openDriver(surfaceHolder);
		} catch (IOException ioe) {
			return;
		} catch (RuntimeException e) {
			return;
		}
		if (handler == null) {
			handler = new CaptureActivityHandler(this, decodeFormats,
					characterSet);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;

	}

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();

	}

	private void initBeepSound() {
		if (playBeep && mediaPlayer == null) {
			// The volume on STREAM_SYSTEM is not adjustable, and users found it
			// too loud,
			// so we now play on the music stream.
			setVolumeControlStream(AudioManager.STREAM_MUSIC);
			mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mediaPlayer.setOnCompletionListener(beepListener);

//			AssetFileDescriptor file = getResources().openRawResourceFd(
//					R.raw.beep);
//			try {
//				mediaPlayer.setDataSource(file.getFileDescriptor(),
//						file.getStartOffset(), file.getLength());
//				file.close();
//				mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
//				mediaPlayer.prepare();
//			} catch (IOException e) {
//				mediaPlayer = null;
//			}
		}
	}

	private static final long VIBRATE_DURATION = 200L;
	private Dialog alertDialog;
	private void playBeepSoundAndVibrate() {
		if (playBeep && mediaPlayer != null) {
			mediaPlayer.start();
		}
		if (vibrate) {
			Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			vibrator.vibrate(VIBRATE_DURATION);
		}
	}

	/**
	 * When the beep has finished playing, rewind to queue up another one.
	 */
	private final OnCompletionListener beepListener = new OnCompletionListener() {
		public void onCompletion(MediaPlayer mediaPlayer) {
			mediaPlayer.seekTo(0);
		}
	};
	private ProgressDialog dialog;
	String circleNames = "";
	void openUrl(){
		alertDialog = new AlertDialog.Builder(this). 
	            setTitle("提示").setCancelable(false).
	            setMessage("是否打开\""+(strResult.length()>50?(strResult.substring(0,50)+"...\""):(strResult+"\""))). 
	            setPositiveButton("确定",new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog2, int which) {
						// TODO Auto-generated method stub
						try{
							Uri uri = Uri.parse(strResult);  
						    startActivity(new Intent(Intent.ACTION_VIEW,uri));  
						    finish();
						}catch(Exception e){
							Toast.makeText(CaptureActivity.this, "网页打开失败",Toast.LENGTH_SHORT).show();
							startActivity(new Intent(CaptureActivity.this, CaptureActivity.class));
							finish();
						}
						
					}
				}).setNegativeButton("取消",new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// TODO Auto-generated method stub
						if (handler == null) {
							handler = new CaptureActivityHandler(CaptureActivity.this, decodeFormats,
									characterSet);
						}
						handler.sendEmptyMessage(R.id.restart_preview);
					}
				})
	            .create();
				alertDialog.show();
	}
	@Override
	public boolean handleMessage(Message msg) {
		openUrl();
		return false;
	}
	void alertResult(){
		alertDialog = new AlertDialog.Builder(this). 
	            setTitle("提示"). 
	            setMessage(strResult).setCancelable(false).
	            setPositiveButton("确定",new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog2, int which) {
						// TODO Auto-generated method stub
						if(joinMap==null || joinMap.get("result")==null || !joinMap.get("result").equals("0")){
							if (handler == null) {
								handler = new CaptureActivityHandler(CaptureActivity.this, decodeFormats,
										characterSet);
							}
							handler.sendEmptyMessage(R.id.restart_preview);
						}else{
							finish();
						}
					}
				})
	            .create();
				alertDialog.show();
	}
	void joinCircle(){
	}
	void parse(){
		if(strResult!=null && !strResult.equals("") && strResult.indexOf("?")!=-1){
			if(strResult.indexOf("?") < strResult.length()-1){
				strurl = strResult.substring(strResult.indexOf("?")+1, strResult.length());
			}else{
				mHandler.sendEmptyMessage(2);
				return;
			}
		}else{
			mHandler.sendEmptyMessage(2);
			return;
		}
		if(strurl!=null && !strurl.equals("")){
			mHandler.sendEmptyMessage(3);
		}else{
			mHandler.sendEmptyMessage(2);
		}
		
	}
}