package io.github.deepbluecitizenservice.citizenservice;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

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

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener,
        PhotoFragment.OnPhotoListener, SettingsFragment.OnSettingsFragmentInteraction {

    private final String TAG = "Main Activity:";
    private GoogleApiClient mGAP;
    private AHBottomNavigation bottomNavigation;
    private Fragment lastFragment = null;

    public Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleLogCheck();
        setContentView(R.layout.activity_main);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        createBottomBar();
    }

    //Login check and handler
    private void handleLogCheck(){
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

    private void createBottomBar(){
        bottomNavigation = (AHBottomNavigation) findViewById(R.id.bottom_navigation);

        // Create items
        AHBottomNavigationItem item1 = new AHBottomNavigationItem(R.string.bottom_bar_tab1, R.drawable.ic_home, R.color.colorPrimary);
        AHBottomNavigationItem item2 = new AHBottomNavigationItem(R.string.bottom_bar_tab2, R.drawable.ic_world, R.color.colorPrimary);
        AHBottomNavigationItem item3 = new AHBottomNavigationItem(R.string.bottom_bar_tab3, R.drawable.ic_camera, R.color.colorPrimary);
        AHBottomNavigationItem item4 = new AHBottomNavigationItem(R.string.bottom_bar_tab4, R.drawable.ic_settings, R.color.colorPrimary);

        // Add items
        bottomNavigation.addItem(item1);
        bottomNavigation.addItem(item2);
        bottomNavigation.addItem(item3);
        bottomNavigation.addItem(item4);

        // Set background color
        bottomNavigation.setDefaultBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));

        // Disable the translation inside the CoordinatorLayout
        bottomNavigation.setBehaviorTranslationEnabled(false);

        // Change colors of icons, when active and inactive
        bottomNavigation.setAccentColor(ContextCompat.getColor(this, R.color.colorAccent));
        bottomNavigation.setInactiveColor(ContextCompat.getColor(this, R.color.inactiveBottomBar));

        // Force to tint the drawable (useful for font with icon for example)
        bottomNavigation.setForceTint(true);

        // Manage titles
        bottomNavigation.setTitleState(AHBottomNavigation.TitleState.ALWAYS_SHOW);

        // Set current item programmatically
        bottomNavigation.setCurrentItem(0);

        // Set listeners
        bottomNavigation.setOnTabSelectedListener(new AHBottomNavigation.OnTabSelectedListener() {
            @Override
            public boolean onTabSelected(int position, boolean wasSelected) {
                Log.d(TAG, "Tab changed to "+ position +" was selected: "+wasSelected);
                FragmentManager fm = getSupportFragmentManager();
                FragmentTransaction fragmentTransaction = fm.beginTransaction();

                switch(position){
                    case 0:
                    case 1:
                        if(lastFragment!=null) {
							toolbar.removeAllViews();
                            toolbar.setTitle(R.string.app_name);
                            fragmentTransaction
                                    .remove(lastFragment)
                                    .commit();
                            lastFragment = null;
                        }
                        break;

                    case 2:
                        if(!wasSelected) {
                            View toolbarView = getLayoutInflater().inflate(R.layout.add_toolbar, null);
                            toolbar.addView(toolbarView);
                            PhotoFragment photoFragment = new PhotoFragment();
                            if(lastFragment!=null){
                                fragmentTransaction
                                        .replace(R.id.fragment_container, photoFragment)
                                        .commit();
                            }
                            else{
                                fragmentTransaction
                                        .add(R.id.fragment_container, photoFragment)
                                        .commit();
                            }

                            lastFragment = photoFragment;
                        }

                        break;

                    case 3:

						toolbar.removeAllViews();
						toolbar.setTitle(R.string.app_name);
                        if(!wasSelected){
                            SettingsFragment settingsFragment = new SettingsFragment();
                            if(lastFragment!=null){
                                fragmentTransaction
                                        .replace(R.id.fragment_container, settingsFragment)
                                        .commit();
                            }
                            else{
                                fragmentTransaction
                                        .add(R.id.fragment_container, settingsFragment)
                                        .commit();
                            }

                            lastFragment = settingsFragment;
                        }

                        break;

                    default:
                        return true;
                }

                return true;
            }
        });

        bottomNavigation.setOnNavigationPositionListener(new AHBottomNavigation.OnNavigationPositionListener() {
            @Override public void onPositionChange(int y) {
                // Manage the new y position
            }
        });

        //For implementing back stack UI changes when all fragments are added
//        getSupportFragmentManager().addOnBackStackChangedListener(
//                new FragmentManager.OnBackStackChangedListener() {
//                    @Override
//                    public void onBackStackChanged() {
//
//                    }
//                }
//        );
    }


    @Override
    public void changeView(int toWhere) {
        bottomNavigation.setCurrentItem(toWhere);
    }

    public Toolbar getToolbar(){
        return toolbar;
    }
}
