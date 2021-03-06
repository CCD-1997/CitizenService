package io.github.deepbluecitizenservice.citizenservice;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.bumptech.glide.Glide;
import com.firebase.ui.storage.images.FirebaseImageLoader;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import io.github.deepbluecitizenservice.citizenservice.database.CustomDatabase;
import io.github.deepbluecitizenservice.citizenservice.fragments.PhotoFragment;
import io.github.deepbluecitizenservice.citizenservice.fragments.SettingsFragment;
import io.github.deepbluecitizenservice.citizenservice.service.GPSService;
import io.github.deepbluecitizenservice.citizenservice.views.ModifiedProgressFAB;

public class SolutionDialogActivity extends AppCompatActivity {
    private String imageKey, mSolutionImagePath="";

    private ImageView mSolutionImageView;
    private LinearLayout mButtons, mImageLayout;
    private View baseView;

    private ModifiedProgressFAB fabCircle;
    private FloatingActionButton mFab;
    private  boolean isClicked;

    private final static int CAMERA_CALL = 100, GALLERY_CALL=200;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.solution_dialog_title));
        setContentView(R.layout.dialog_solution);

        Bundle params = getIntent().getExtras();

        ImageView problemImage, cameraButton, galleryButton;

        mFab = (FloatingActionButton) findViewById(R.id.fab_problem_dialog);
        mFab.setVisibility(View.GONE);

        baseView = findViewById(R.id.solution_dialog_base);

        mFab.setBackgroundTintList(ContextCompat.getColorStateList(this, getThemeColorID()));

        mButtons     = (LinearLayout) findViewById(R.id.solution_dialog_button_container);
        mImageLayout = (LinearLayout) findViewById(R.id.solution_image_container);

        problemImage       = (ImageView) findViewById(R.id.dialog_problem_image_view);
        mSolutionImageView = (ImageView) findViewById(R.id.dialog_solution_image_view);
        cameraButton       = (ImageView) findViewById(R.id.solution_dialog_camera);
        galleryButton      = (ImageView) findViewById(R.id.solution_dialog_gallery);

        String imageUrl = (String) params.get(SLANotification.URL_KEY);
        imageKey        = (String) params.get(SLANotification.PROBLEM_KEY);

        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent startCamera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(startCamera, CAMERA_CALL);
            }
        });

        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent startGallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(startGallery, GALLERY_CALL);
            }
        });

        fabCircle = (ModifiedProgressFAB) findViewById(R.id.solution_dialog_progress_fab);

        mFab.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                fabCircle.show();
                if(mSolutionImagePath.length()>0 && !isClicked) {
                    new AsyncTask<Void, Void, Boolean>(){

                        boolean analysisSuccess = false;

                        @Override
                        protected void onPreExecute() {
                            analysisSuccess = analyseImage(mSolutionImagePath);
                        }

                        @Override
                        protected Boolean doInBackground(Void... params) {
                            if(analysisSuccess){
                                handleSolutionUpload(imageKey);
                                return true;
                            }
                            else {
                                return false;
                            }
                        }
                        @Override
                        protected void onPostExecute(Boolean result){
                            if(result){
                                isClicked = true;
                                mFab.setOnClickListener(null);
                            }
                            else {
                                fabCircle.hide();
                            }
                        }
                    }.execute();
                }
            }
        });

        StorageReference ref = FirebaseStorage.getInstance().getReference(imageUrl);
        Glide
                .with(this)
                .using(new FirebaseImageLoader())
                .load(ref)
                .centerCrop()
                .crossFade()
                .into(problemImage);
    }

    //HACK
    private int getThemeColorID(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        switch (preferences.getInt(SettingsFragment.SP_THEME, 2)){
            case SettingsFragment.INDIGO_PINK:
                return R.color.pink_a200;
            case SettingsFragment.MIDNIGHT_BLUE_YELLOW:
                return R.color.might_night_blue_700;
            default:
            case SettingsFragment.WET_ASPHALT_TURQUOISE:
                return R.color.turquoise_500;
            case SettingsFragment.GREY_EMERALD:
                return R.color.emerald_500;
            case SettingsFragment.TEAL_ORANGE:
                return R.color.deep_orange_500;
            case SettingsFragment.BROWN_BLUE:
                return R.color.blue_500;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode== CAMERA_CALL){
            if(resultCode == Activity.RESULT_OK){
                handleCameraUpload(data);
                mButtons.setVisibility(View.GONE);
                mImageLayout.setVisibility(View.VISIBLE);
                mFab.setVisibility(View.VISIBLE);
            }
        }

        //Get image from Gallery
        if(requestCode== GALLERY_CALL){
            if(resultCode== Activity.RESULT_OK) {
                handleGalleryUpload(data);
                mButtons.setVisibility(View.GONE);
                mImageLayout.setVisibility(View.VISIBLE);
                mFab.setVisibility(View.VISIBLE);
            }
        }
    }

    public boolean analyseImage(String imagePath){
        GPSService gpsService = new GPSService(this, baseView);
        if(!gpsService.isGPSEnabled() && !gpsService.isGPSPermissionGranted()){
            return false;
        }
        // TODO: Get location and verify if it's correct
        // TODO: Upload image to tensorflow server to get probabilities and then verify
        //For now return true (i.e. image is verified)
        return true;
    }

    private Bitmap handleCameraUpload(Intent data) {
        Bitmap bitmap = (Bitmap) data.getExtras().get(PhotoFragment.INTENT_BITMAP_DATA);
        mSolutionImageView.setImageBitmap(bitmap);
        try {
            File outputDir = getCacheDir();
            File outFile = new File(outputDir, PhotoFragment.TEMP_CAMERA_FILE);
            FileOutputStream fos = new FileOutputStream(outFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            mSolutionImagePath = outFile.getPath();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private Bitmap handleGalleryUpload(Intent data){
        mSolutionImagePath = getFilePathFromGallery(data);
        //Change image using setImageBitmap
        Bitmap bitmap = BitmapFactory.decodeFile(mSolutionImagePath);
        mSolutionImageView.setImageBitmap(bitmap);

        return bitmap;
    }

    private String getFilePathFromGallery(Intent data){
        Uri selectedImage = data.getData();
        String picturePath = "";

        //Get filepath
        String[] filePathColumn = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);

        if(cursor!=null) {
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            picturePath = cursor.getString(columnIndex);
            cursor.close();
        }

        return picturePath;
    }

    private void handleSolutionUpload(final String problemId){

        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        final CustomDatabase db = new CustomDatabase(FirebaseDatabase.getInstance().getReference());

        //Time stamps are unique
        final Long tsLong = System.currentTimeMillis()/1000;
        final String ts = tsLong.toString();
        final String userName = user==null? "guest": user.getEmail();

        //Create a unique file reference in FireBase
        final StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference()
                .child(userName+"/solvedProblems/problem-"+ts+".jpg");

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                db.updateProblemToSolved(user.getUid(),
                        problemId, userName +"/solvedProblems/problem-"+ts+".jpg", tsLong);

                return null;
            }
        }.execute();

        //Compress images before uploading if they are greater than 100kb
        Uri file;
        try {
            long fileSize = new File(mSolutionImagePath).length();
            int qualityPercentage = (int) (10240000 / fileSize);
            if(qualityPercentage > 100) qualityPercentage = 100;

            File tmpFile = File.createTempFile("solution_", ".jpeg", getCacheDir());
            FileOutputStream fos = new FileOutputStream(tmpFile);
            BitmapFactory.decodeFile(mSolutionImagePath)
                    .compress(Bitmap.CompressFormat.JPEG, qualityPercentage, fos);

            file = Uri.fromFile(tmpFile);

        } catch (Exception e){
            file = Uri.fromFile(new File(mSolutionImagePath));
        }

        UploadTask uploadTask = storageRef.putFile(file);

        uploadTask.addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                fabCircle.beginFinalAnimation();
                fabCircle.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });
            }
        });
    }
}