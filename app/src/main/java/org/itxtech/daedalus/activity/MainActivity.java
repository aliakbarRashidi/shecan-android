package org.itxtech.daedalus.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.fragment.AboutFragment;
import org.itxtech.daedalus.fragment.DNSTestFragment;
import org.itxtech.daedalus.fragment.HomeFragment;
import org.itxtech.daedalus.fragment.LogFragment;
import org.itxtech.daedalus.fragment.SettingsFragment;
import org.itxtech.daedalus.fragment.ToolbarFragment;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.server.DNSServerHelper;
import org.itxtech.daedalus.util.server.LocaleHelper;

import java.util.Locale;

/**
 * Daedalus Project
 *
 * @author iTX Technologies
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "DMainActivity";

    public static final String LAUNCH_ACTION = "org.itxtech.daedalus.activity.MainActivity.LAUNCH_ACTION";
    public static final int LAUNCH_ACTION_NONE = 0;
    public static final int LAUNCH_ACTION_ACTIVATE = 1;
    public static final int LAUNCH_ACTION_DEACTIVATE = 2;
    public static final int LAUNCH_ACTION_SERVICE_DONE = 3;

    public static final String LAUNCH_FRAGMENT = "org.itxtech.daedalus.activity.MainActivity.LAUNCH_FRAGMENT";
    public static final int FRAGMENT_NONE = -1;
    public static final int FRAGMENT_HOME = 0;
    public static final int FRAGMENT_DNS_TEST = 1;
    public static final int FRAGMENT_SETTINGS = 2;
    public static final int FRAGMENT_ABOUT = 3;
//    public static final int FRAGMENT_RULES = 4;
//    public static final int FRAGMENT_DNS_SERVERS = 5;
    public static final int FRAGMENT_LOG = 6;

    public static final String LAUNCH_NEED_RECREATE = "org.itxtech.daedalus.activity.MainActivity.LAUNCH_NEED_RECREATE";

    private static MainActivity instance = null;

    private ToolbarFragment currentFragment;

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Daedalus.getInstance().updateLocale();
        if (Daedalus.isDarkTheme()) {
            setTheme(R.style.AppTheme_Dark_NoActionBar);
        }
        super.onCreate(savedInstanceState);

        instance = this;

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar); //causes toolbar issues

        DrawerLayout drawer = findViewById(R.id.main_drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        updateUserInterface(getIntent());
    }

    private void switchFragment(Class fragmentClass) {
        if (currentFragment == null || fragmentClass != currentFragment.getClass()) {
            try {
                ToolbarFragment fragment = (ToolbarFragment) fragmentClass.newInstance();
                FragmentManager fm = getFragmentManager();
                fm.beginTransaction().replace(R.id.id_content, fragment).commit();
                currentFragment = fragment;
            } catch (Exception e) {
                Logger.logException(e);
            }
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.main_drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (!(currentFragment instanceof HomeFragment)) {
            switchFragment(HomeFragment.class);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
        currentFragment = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        updateUserInterface(intent);
    }

    public void activateService() {
        Intent intent = VpnService.prepare(Daedalus.getInstance());
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, Activity.RESULT_OK, null);
        }

        long activateCounter = Daedalus.configurations.getActivateCounter();
        if (activateCounter == -1) {
            return;
        }
        activateCounter++;
        Daedalus.configurations.setActivateCounter(activateCounter);
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        if (result == Activity.RESULT_OK) {
            DaedalusVpnService.primaryServer = DNSServerHelper.getDNSById(DNSServerHelper.getPrimary());
            DaedalusVpnService.secondaryServer = DNSServerHelper.getDNSById(DNSServerHelper.getSecondary());
            Daedalus.getInstance().startService(Daedalus.getServiceIntent(getApplicationContext()).setAction(DaedalusVpnService.ACTION_ACTIVATE));
            updateMainButton(R.string.button_text_deactivate);
            Daedalus.updateShortcut(getApplicationContext());
        }
    }

    private void updateMainButton(int id) {
        if (currentFragment instanceof HomeFragment) {
            Button button = currentFragment.getView().findViewById(R.id.button_activate);
            button.setText(id);
        }
    }

    private void updateUserInterface(Intent intent) {
        int launchAction = intent.getIntExtra(LAUNCH_ACTION, LAUNCH_ACTION_NONE);
        Log.d(TAG, "Updating user interface with Launch Action " + String.valueOf(launchAction));
        if (launchAction == LAUNCH_ACTION_ACTIVATE) {
            this.activateService();
        } else if (launchAction == LAUNCH_ACTION_DEACTIVATE) {
            Daedalus.deactivateService(getApplicationContext());
        } else if (launchAction == LAUNCH_ACTION_SERVICE_DONE) {
            Daedalus.updateShortcut(getApplicationContext());
            if (DaedalusVpnService.isActivated()) {
                updateMainButton(R.string.button_text_deactivate);
            } else {
                updateMainButton(R.string.button_text_activate);
            }
        }

        int fragment = intent.getIntExtra(LAUNCH_FRAGMENT, FRAGMENT_NONE);

        if (intent.getBooleanExtra(LAUNCH_NEED_RECREATE, false)) {
            if (fragment != FRAGMENT_NONE)
                getIntent().putExtra(MainActivity.LAUNCH_FRAGMENT, fragment);
            recreate();
            return;
        }

        switch (fragment) {
            case FRAGMENT_ABOUT:
                switchFragment(AboutFragment.class);
                break;
            case FRAGMENT_DNS_TEST:
                switchFragment(DNSTestFragment.class);
                break;
            case FRAGMENT_HOME:
                switchFragment(HomeFragment.class);
                break;
            case FRAGMENT_SETTINGS:
                switchFragment(SettingsFragment.class);
                break;
            case FRAGMENT_LOG:
                switchFragment(LogFragment.class);
                break;
        }
        if (currentFragment == null) {
            switchFragment(HomeFragment.class);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        switch (id) {
            case R.id.nav_about:
                switchFragment(AboutFragment.class);
                break;
            case R.id.nav_dns_test:
                switchFragment(DNSTestFragment.class);
                break;
                case R.id.nav_domain_test:
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shecan.ir")));
                break;
            case R.id.nav_home:
                switchFragment(HomeFragment.class);
                break;
            case R.id.nav_settings:
                switchFragment(SettingsFragment.class);
                break;
            case R.id.nav_log:
                switchFragment(LogFragment.class);
                break;
        }

        DrawerLayout drawer = findViewById(R.id.main_drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        InputMethodManager imm = (InputMethodManager) Daedalus.getInstance().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findViewById(R.id.id_content).getWindowToken(), 0);
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Daedalus.getInstance().updateLocale();
    }


    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base));
    }
}
