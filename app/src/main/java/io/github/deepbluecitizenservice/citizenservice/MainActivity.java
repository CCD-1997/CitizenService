package io.github.deepbluecitizenservice.citizenservice;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;

import com.aurelhubert.ahbottomnavigation.AHBottomNavigation;
import com.aurelhubert.ahbottomnavigation.AHBottomNavigationItem;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;

import io.github.deepbluecitizenservice.citizenservice.fragments.AllViewFragment;
import io.github.deepbluecitizenservice.citizenservice.fragments.HomeFragment;
import io.github.deepbluecitizenservice.citizenservice.fragments.PhotoFragment;
import io.github.deepbluecitizenservice.citizenservice.fragments.SettingsFragment;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener,
        PhotoFragment.OnPhotoListener, SettingsFragment.OnSettingsFragmentInteraction {

    private final static String TAG = "Main Activity:";
    private GoogleApiClient mGAP;
    private AHBottomNavigation bottomNavigation;

    public final static String HOME_TAG="HOME", ALL_TAG="ALL", PHOTOS_TAG="PHOTOS", SETTINGS_TAG="SETTINGS";
    private Fragment homeFragment, allviewFragment, settingsFragment, photosFragment;
    private boolean backPressed = false;
    private String lastFragment;
    private FragmentManager fragmentManager;
    private FragmentTransaction fragmentTransaction;
    private ArrayList<String> BackStack;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setThemeFromPreferences();
        setContentView(R.layout.activity_main);

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage("Logging in");


        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                progressDialog.show();
            }

            @Override
            protected Void doInBackground(Void... params) {
                handleLogCheck();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                progressDialog.dismiss();

                Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
                setSupportActionBar(toolbar);

                FragmentManager fm = getSupportFragmentManager();

                if(savedInstanceState==null){
                    if(homeFragment==null ) {
                        if(fm.findFragmentByTag(HOME_TAG)==null)
                            homeFragment = new HomeFragment();
                        else
                            homeFragment = fm.findFragmentByTag(HOME_TAG);
                    }
                    if(photosFragment==null) {
                        if(fm.findFragmentByTag(HOME_TAG)==null)
                            photosFragment = new PhotoFragment();
                        else
                            photosFragment = fm.findFragmentByTag(PHOTOS_TAG);
                    }
                    if(allviewFragment==null) {
                        if(fm.findFragmentByTag(ALL_TAG)==null)
                            allviewFragment = new AllViewFragment();
                        else
                            allviewFragment = fm.findFragmentByTag(ALL_TAG);
                    }
                    if(settingsFragment==null) {
                        if(fm.findFragmentByTag(SETTINGS_TAG)==null)
                            settingsFragment = new SettingsFragment();
                        else
                            settingsFragment = fm.findFragmentByTag(SETTINGS_TAG);
                    }

                    if(!homeFragment.isAdded()) {
                        getSupportFragmentManager()
                                .beginTransaction()
                                .add(R.id.fragment_container, homeFragment, HOME_TAG)
                                .commit();
                        lastFragment = HOME_TAG;
                    }

                    BackStack = new ArrayList<>();
                }

                fragmentManager = getSupportFragmentManager();

                if(bottomNavigation==null)
                    createBottomBar(savedInstanceState==null);
            }
        }.execute();
    }

    private void setThemeFromPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        switch (preferences.getInt(SettingsFragment.SP_THEME, 0)){
            default:
            case SettingsFragment.INDIGO_PINK:
                setTheme(R.style.AppTheme_IndigoPink);
                break;
            case SettingsFragment.MIDNIGHT_BLUE_YELLOW:
                setTheme(R.style.AppTheme_MidNightBlueYellow);
                break;
            case SettingsFragment.WET_ASPHALT_TURQUOISE:
                setTheme(R.style.AppTheme_WetAsphaltTurquoise);
                break;
            case SettingsFragment.GREY_EMERALD:
                setTheme(R.style.AppTheme_GreyEmerald);
                break;
            case SettingsFragment.TEAL_ORANGE:
                setTheme(R.style.AppTheme_TealOrange);
                break;
            case SettingsFragment.BROWN_BLUE:
                setTheme(R.style.AppTheme_BlueBrown);
        }
    }

    //Login check and handler
    private void handleLogCheck(){
        Log.d(TAG, "Logging in.....");
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.web_client_id))
                .requestEmail()
                .build();

        mGAP = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();


        SharedPreferences prefs = getSharedPreferences(getString(R.string.user_preferences_id), Context.MODE_PRIVATE);

        //Check if logged in through shared preferences
        boolean isLoggedIn = prefs.getBoolean(getString(R.string.logged_in_state), false);

        //Check if user is logged in to firebase
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if(user!=null){
            Log.d(TAG, "Current user is: "+ user.getDisplayName());
        }

        Log.d(TAG, "Logged in state in shared preferences" + (isLoggedIn? "True": "False"));
        Log.d(TAG, prefs.getBoolean(getString(R.string.logged_in_state), false)? "True" : "False");

        //While we redirect to the login activity if not connected to firebase OR google, it might be
        //wise to handle these values separately. This is because shared preferences are available offline
        //So the non login screens should be visible if user was logged in before the connection was lost
        if(!isLoggedIn || user==null){
            //If not logged in, start the login activity
            Intent startLoginActivity = new Intent(this, LoginActivity.class);
            startActivity(startLoginActivity);
        }
    }

    //Handle clicking logout button
    //Might replace this to another screen later
    //This is here for testing logging out and logging in
    //Need to move this to settings fragment

    @Override
    public void onLogoutClick(){
        if(mGAP.isConnected()) {
            Auth.GoogleSignInApi.signOut(mGAP).setResultCallback(
                    new ResultCallback<Status>() {
                        @Override
                        public void onResult(@NonNull Status status) {
                            Log.d(TAG, "Logged out");
                            SharedPreferences prefs = getSharedPreferences(getString(R.string.user_preferences_id), MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();

                            //Set logged in state to false
                            editor.putBoolean(getString(R.string.logged_in_state), false);
                            editor.apply();

                            //Log out of firebase
                            if(FirebaseAuth.getInstance().getCurrentUser()!=null)
                                FirebaseAuth.getInstance().signOut();

                            Intent login = new Intent(getBaseContext(), LoginActivity.class);
                            startActivity(login);
                        }
                    });
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    private void createBottomBar(boolean isNotSaved){
        bottomNavigation = (AHBottomNavigation) findViewById(R.id.bottom_navigation);

        TypedValue primaryColor = new TypedValue();
        TypedValue accentColor = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimary, primaryColor, true);
        getTheme().resolveAttribute(R.attr.colorAccent, accentColor, true);

        // Create items
        AHBottomNavigationItem item1 = new AHBottomNavigationItem(R.string.bottom_bar_tab1, R.drawable.ic_home, primaryColor.resourceId);
        AHBottomNavigationItem item2 = new AHBottomNavigationItem(R.string.bottom_bar_tab2, R.drawable.ic_world, primaryColor.resourceId);
        AHBottomNavigationItem item3 = new AHBottomNavigationItem(R.string.bottom_bar_tab3, R.drawable.ic_camera, primaryColor.resourceId);
        AHBottomNavigationItem item4 = new AHBottomNavigationItem(R.string.bottom_bar_tab4, R.drawable.ic_settings, primaryColor.resourceId);

        // Add items
        bottomNavigation.addItem(item1);
        bottomNavigation.addItem(item2);
        bottomNavigation.addItem(item3);
        bottomNavigation.addItem(item4);

        // Set background color
        bottomNavigation.setDefaultBackgroundColor(primaryColor.data);

        // Disable the translation inside the CoordinatorLayout
        bottomNavigation.setBehaviorTranslationEnabled(true);

        // Change colors of icons, when active and inactive
        bottomNavigation.setAccentColor(accentColor.data);
        bottomNavigation.setInactiveColor(ContextCompat.getColor(this, R.color.white));

        // Force to tint the drawable (useful for font with icon for example)
        bottomNavigation.setForceTint(true);

        // Manage titles
        bottomNavigation.setTitleState(AHBottomNavigation.TitleState.ALWAYS_SHOW);

        // Set current item programmatically
        if(isNotSaved)
            bottomNavigation.setCurrentItem(0);

        // Set listeners
        bottomNavigation.setOnTabSelectedListener(new AHBottomNavigation.OnTabSelectedListener() {
            @Override
            public boolean onTabSelected(int position, boolean wasSelected) {
                fragmentTransaction = fragmentManager.beginTransaction();

                Fragment genericFragment = null;
                String fragmentTAG = "";

                switch(position){
                    case 0:
                        if(!wasSelected) {
                            genericFragment = homeFragment==null? new HomeFragment(): homeFragment;
                            fragmentTAG = HOME_TAG;
                        }
                        break;

                    case 1:
                        if(!wasSelected) {
                            genericFragment = allviewFragment==null? new AllViewFragment(): allviewFragment;
                            fragmentTAG = ALL_TAG;
                        }
                        break;

                    case 2:
                        if(!wasSelected) {
                            genericFragment = photosFragment==null? new PhotoFragment(): photosFragment;
                            fragmentTAG = PHOTOS_TAG;
                        }

                        break;

                    case 3:
                        if(!wasSelected){
                            genericFragment = settingsFragment==null? new SettingsFragment(): settingsFragment;
                            fragmentTAG = SETTINGS_TAG;
                        }
                        break;

                    default:
                        return true;
                }

                if(!wasSelected  && !backPressed){
                    Fragment testFragment = fragmentManager.findFragmentByTag(lastFragment);

                        if(!genericFragment.isAdded()){
                            fragmentTransaction
                                    .add(R.id.fragment_container, genericFragment, fragmentTAG);
                        }

                        try {
                            Log.d(TAG, "Removing fragment: "+ lastFragment+" Answer: "+ (testFragment == null ? "Yes":"no"));
                            fragmentTransaction.hide(testFragment);
                        }
                        catch(Exception e){
                            Log.d(TAG, "Exception Occurred "+ lastFragment+ " "+  e.getMessage());
                        }

                            fragmentTransaction
                                    .show(genericFragment);

                        if(BackStack==null){
                            BackStack = new ArrayList<>();
                        }

                        BackStack.add(0, fragmentTAG);
                        if(BackStack.size()==5)
                            BackStack.remove(4);

                        //Log.d(TAG, "Backstack size: "+BackStack.size());

                        lastFragment = fragmentTAG;
                }

                fragmentTransaction.commit();
                backPressed = false;
                return true;
            }
        });
    }

    @Override
    public void changeView(int toWhere) {
        bottomNavigation.setCurrentItem(toWhere);
    }

    //Handle the back stack navigation
    @Override
    public void onBackPressed() {
        backPressed = true;
        FragmentTransaction fm = getSupportFragmentManager().beginTransaction();
        try {
            if (BackStack.size() > 1) {

                String name = BackStack.get(1);
                Log.d(TAG, "Back stack woo " + BackStack.size() + " " + name);

                bottomNavigation.restoreBottomNavigation();

                switch (name) {
                    case HOME_TAG:
                        bottomNavigation.setCurrentItem(0);
                        if (homeFragment.isHidden()) {
                            fm.show(homeFragment);
                        }
                        break;
                    case ALL_TAG:
                        bottomNavigation.setCurrentItem(1);
                        if (allviewFragment.isHidden()) {
                            fm.show(allviewFragment);
                        }

                        break;

                    case PHOTOS_TAG:
                        bottomNavigation.setCurrentItem(2);
                        if (photosFragment.isHidden())
                            fm.show(photosFragment);
                        break;
                    case SETTINGS_TAG:
                        bottomNavigation.setCurrentItem(3);
                        if (settingsFragment.isHidden())
                            fm.show(settingsFragment);
                        break;
                }

                lastFragment = name;

                name = BackStack.get(0);

                Fragment generic = name.equals(HOME_TAG) ? homeFragment : (name.equals(PHOTOS_TAG) ? photosFragment : (name.equals(ALL_TAG) ? allviewFragment : settingsFragment));
                fm.hide(generic);
                fm.commit();
                BackStack.remove(0);
            } else {
                if (BackStack.size() == 1) {
                    BackStack.remove(0);
                }
                super.onBackPressed();
            }
        }

        catch(Exception e){
            super.onBackPressed();
        }
    }
}
