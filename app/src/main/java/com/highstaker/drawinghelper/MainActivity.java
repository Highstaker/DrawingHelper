package com.highstaker.drawinghelper;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {

	public void ckdebug(String message)
	{
		Log.d("ck_debug",message);	
	}

	public void ckwarning(String message)
	{
		Log.w("ck_debug",message);	
	}

	//Camera object
	private static Camera camera=null;

	private TextureView mTextureView;

	private ImageView mImageView;

	    //a flag showing if a flashlight is on at this time
	private boolean flashlightOn=false;

    // a seekbar object, for a binary display threshold
	private SeekBar seekbar_treshold;

	//the treshold value for binary view
	private int TRESHOLD_VALUE = 128;

    private ToggleButton flashlightToggleButton;

    //returns the scale of the matrix as array {X,Y}
	private float[] getMatrixScale(Matrix matrix)
	{
		float[] f = new float[9];
		matrix.getValues(f);

		float scaleX = f[Matrix.MSCALE_X];
		float scaleY = f[Matrix.MSCALE_Y];
		float[] result = {scaleX, scaleY};
		return result;
	}

    //returns the translate of the matrix as array {X,Y}
	private float[] getMatrixTranslate(Matrix matrix)
	{
		float[] f = new float[9];
		matrix.getValues(f);

		float translateX = f[Matrix.MTRANS_X];
		float translateY = f[Matrix.MTRANS_Y];
		float[] result = {translateX, translateY};
		return result;
	}

	private Matrix transformMatrix()
	{
		Matrix matrix = new Matrix();
		matrix.setScale((viewIsMirroredX ? -1 : 1), (viewIsMirroredY ? -1 : 1));
        //move it back to in view otherwise it'll be off to the left. 
		matrix.postTranslate((viewIsMirroredX ? mTextureView.getWidth() : 1), (viewIsMirroredY ? mTextureView.getHeight() : 1));
		return matrix;
	}

    //switch for regular and mirrored view on horizontal axis
	private boolean viewIsMirroredX=false;   
	public void setIsMirroredX(View view)
	{
		viewIsMirroredX = !viewIsMirroredX;
		mTextureView.setTransform(transformMatrix());
	}

    //switch for regular and mirrored view on vertical axis
	private boolean viewIsMirroredY=false;
	public void setIsMirroredY(View view)
	{
		viewIsMirroredY = !viewIsMirroredY;
		mTextureView.setTransform(transformMatrix());
	}

	private boolean viewIsGrayscale=false;
	public void setIsGrayscale(View view)
	{
		viewIsGrayscale = !viewIsGrayscale;
		if(viewIsGrayscale)
		{
			Camera.Parameters params = camera.getParameters();
			params.setColorEffect("mono");
			camera.setParameters(params);
		}
		else
		{
			Camera.Parameters params = camera.getParameters();
			params.setColorEffect("none");
			camera.setParameters(params);
		}
	}   

	boolean viewIsBinary=false;
	public void setIsBinary(View view)
	{
		viewIsBinary = !viewIsBinary;
		if(viewIsBinary)
		{
			mImageView.setVisibility(View.VISIBLE);
		}
		else
		{
			mImageView.setVisibility(View.INVISIBLE);
		}
	}

	double[] databaseRGBtoBinary_reds = new double[256];
	double[] databaseRGBtoBinary_greens = new double[256];
	double[] databaseRGBtoBinary_blues = new double[256];
	private void generateDatabaseRGBtoBinary()
	{
		for(int i=0;i<256;i++)
		{
			databaseRGBtoBinary_reds[i] = 0.2126*i;
		}

		for(int i=0;i<256;i++)
		{
			databaseRGBtoBinary_greens[i] = 0.7152*i;
		}
				
		for(int i=0;i<256;i++)
		{
			databaseRGBtoBinary_blues[i] = 0.0722*i;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		ckdebug("onCreate() invoked");
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) 
		{
			//Restore saved values after the app has been restarted after destruction
		}

		if(!checkCameraHardware(getBaseContext()))
		{
			Toast.makeText(getBaseContext(),"Device has no camera, exiting.",Toast.LENGTH_LONG);
			finish();
		}
		
		generateDatabaseRGBtoBinary();		

		//Remove title bar
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
	    //Remove notification bar
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        //sets the content view
        setContentView(R.layout.activity_main);

        flashlightToggleButton = (ToggleButton)findViewById(R.id.button_flashlight);

        if(  !getBaseContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) )
        {
            //no flashligh - remove the button. not with INVISIBLE, cuz it will still occupy space. use GONE instead.
            flashlightToggleButton.setVisibility(View.GONE);
        }

		mTextureView = (TextureView)findViewById(R.id.preview);
		mTextureView.setSurfaceTextureListener(this);
		mImageView = (ImageView)findViewById(R.id.preview2);
		mImageView.setVisibility(View.INVISIBLE);
		seekbar_treshold = (SeekBar) findViewById(R.id.seekBar_treshold);
		seekbar_treshold.setOnSeekBarChangeListener(seekBar_treshold_ChangeListener);

	 	//get display size
		DisplayMetrics displaymetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
		int height = displaymetrics.heightPixels;
	 	//int width = displaymetrics.widthPixels;

		double aspect = 1.33;

		mTextureView.getLayoutParams().width = (int)(height * aspect);
		mTextureView.getLayoutParams().height = height;	 
		mImageView.getLayoutParams().width = (int)(height * aspect);
		mImageView.getLayoutParams().height = height;

	    //keep the screen on (needed?)
		mTextureView.setKeepScreenOn(true);

	}
	
	private SeekBar.OnSeekBarChangeListener seekBar_treshold_ChangeListener =
	new SeekBar.OnSeekBarChangeListener()
	{
		public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
			TRESHOLD_VALUE = progress;
		}

		public void onStartTrackingTouch(SeekBar seekBar) {

		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {

		}
	};

	
	@Override
	public void onStart(){
		ckdebug("onStart() invoked");
		super.onStart();
		
	}
	
	@Override
	public void onResume()
	{
		ckdebug("onResume() invoked");
		super.onResume();
    }//onResume()


    @Override
    public void onPause(){
    	ckdebug("onPause() invoked");

    	if(camera != null) 
    	{
			//stopPreviewAndRelease(camera);
    	}

    	super.onPause();	
    }//onPause()

    @Override
    public void onStop(){
    	ckdebug("onStop() invoked");
        super.onStop();

        this.finish();

    }//onStop()

    private void stopPreviewAndRelease(Camera camera)
    {
    	camera.stopPreview();
    	camera.lock();
    	camera.release();
    	ckdebug("Preview stopped. Camera released.");
    }//stopPreviewAndRelease

    @Override
    public void onRestart(){
    	ckdebug("onRestart() invoked");
    	super.onRestart();
    }//onRestart()

    @Override
    public void onDestroy(){
    	ckdebug("onDestroy() invoked");
    	super.onDestroy();
    }//onDestroy()

	// Check if this device has a camera
    private boolean checkCameraHardware(Context context) 
    {
    	if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
	        // this device has a camera
    		ckdebug("checkCameraHardware returns true");
    		return true;
    	} else {
	        // no camera on this device
    		ckwarning("checkCameraHardware returns false");
    		return false;
    	}
    }//checkCameraHardware()


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width,int height)
    {
    	ckdebug("Surface texture available");

    	camera = Camera.open(0);

    	Camera.Parameters params = camera.getParameters();
    	List<String> focusModes = params.getSupportedFocusModes();
    	if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
    	{
            // set the focus mode
    		params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            // set Camera parameters
    		camera.setParameters(params);
    	}

    	try {
    		camera.setPreviewTexture(surface);
    	} catch (IOException t) {
    		ckdebug("failed to set preview texture");
    	}

        //mTextureView.setTransform(transformMatrix());

    	camera.startPreview();

    }//onSurfaceTextureAvailable()

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture arg0) {
    	ckdebug("Surface texture destroyed");
    	if(camera != null)
    	{
    		stopPreviewAndRelease(camera);
    	}
    	return false;
    }//onSurfaceTextureDestroyed()

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture arg0, int arg1,
    	int arg2) {

    }//onSurfaceTextureSizeChanged

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture arg0) 
    {
    	if(viewIsBinary)
    	{
    		Bitmap bitmap = mTextureView.getBitmap();		
    		int bitmap_width = bitmap.getWidth();
    		int bitmap_height = bitmap.getHeight();
    		int[] pixels = new int[bitmap_width*bitmap_height];
    		bitmap.getPixels(pixels, 0, bitmap_width, 0, 0, bitmap_width,bitmap_height);

    		for(int i=0; i<pixels.length; i++)
    		{			
    			int red = Color.red(pixels[i]);
    			int green = Color.green(pixels[i]);
    			int blue = Color.blue(pixels[i]);
    			int gray = (int)Math.round(databaseRGBtoBinary_reds[red]
    				+ databaseRGBtoBinary_greens[green] 
    				+ databaseRGBtoBinary_blues[blue]);

    			int a = (gray >= TRESHOLD_VALUE)? 255 : 0;	
    			pixels[i] = Color.rgb(a, a, a) ;


    		}

    		bitmap.setPixels(pixels, 0, bitmap_width, 0, 0, bitmap_width, bitmap_height);

	Bitmap bitmap2 = Bitmap.createBitmap(bitmap,0,0,bitmap_width,bitmap_height,transformMatrix(),false);
	mImageView.setImageBitmap(bitmap2);
}

}//onSurfaceTextureUpdated()

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        ckdebug("onSaveInstanceState() called");

        super.onSaveInstanceState(outState);
    }//onSaveInstanceState

    public void toggleFlashlight(View view) {

	if(  getBaseContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) )
	{
		ckdebug("flashlight detected");

		if(!flashlightOn)
		{
			//turn the light on
			flashlightOn=true;
			Camera.Parameters p = camera.getParameters();
			p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
			camera.setParameters(p);
		}
		else
		{
			//turn off the light
			flashlightOn=false;
			Camera.Parameters p = camera.getParameters();
			p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
			camera.setParameters(p);
		}

	}
	else
	{
		ckdebug("There is no flashlight in this device");
        flashlightToggleButton.setChecked(false);
		Toast.makeText(getBaseContext(),"Flashlight not found on this device",Toast.LENGTH_SHORT).show();
	}

}//toggleFlashlight
}//MainActivity