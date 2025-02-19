/*
 * AsteroidOSSync
 * Copyright (c) 2023 AsteroidOS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.asteroidos.sync;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import org.asteroidos.sync.asteroid.IAsteroidDevice;
import org.asteroidos.sync.fragments.AppListFragment;
import org.asteroidos.sync.fragments.DeviceDetailFragment;
import org.asteroidos.sync.fragments.DeviceListFragment;
import org.asteroidos.sync.fragments.WeatherSettingsFragment;
import org.asteroidos.sync.services.SynchronizationService;
import org.asteroidos.sync.utils.AppInfo;
import org.asteroidos.sync.utils.AppInfoHelper;
import org.asteroidos.sync.utils.AsteroidUUIDS;

import java.util.ArrayList;
import java.util.List;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

import static android.os.ParcelUuid.fromString;

public class MainActivity extends AppCompatActivity implements DeviceListFragment.OnDefaultDeviceSelectedListener,
        DeviceListFragment.OnScanRequestedListener, DeviceDetailFragment.OnDefaultDeviceUnselectedListener,
        DeviceDetailFragment.OnConnectRequestedListener, DeviceDetailFragment.OnAppSettingsClickedListener,
        DeviceDetailFragment.OnWeatherSettingsClickedListener, DeviceDetailFragment.OnUpdateListener {

    public static final String PREFS_NAME = "MainPreferences";
    public static final String PREFS_DEFAULT_MAC_ADDR = "defaultMacAddress";
    public static final String PREFS_DEFAULT_LOC_NAME = "defaultLocalName";
    private static final String TAG = "MainActivity";
    public static ArrayList<AppInfo> appInfoList;
    final Messenger mDeviceDetailMessenger = new Messenger(new MainActivity.SynchronizationHandler(this));
    public final ParcelUuid asteroidUUID = fromString(AsteroidUUIDS.SERVICE_UUID.toString());
    Messenger mSyncServiceMessenger;
    ActivityResultLauncher<Intent> mLocationEnableActivityLauncher;
    LocationManager mLocationManager;
    /* Synchronization service events handling */
    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            mSyncServiceMessenger = new Messenger(service);
            onUpdateRequested();
        }

        public void onServiceDisconnected(ComponentName className) {
            mSyncServiceMessenger = null;
        }
    };
    Intent mSyncServiceIntent;
    IAsteroidDevice.ConnectionState mStatus = IAsteroidDevice.ConnectionState.STATUS_DISCONNECTED;
    ScanSettings mSettings;
    List<ScanFilter> mFilters;
    private DeviceListFragment mListFragment;
    public final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, @NonNull ScanResult result) {
            super.onScanResult(callbackType, result);

            if (mListFragment == null) return;
            mListFragment.deviceDiscovered(result.getDevice());
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(getParent(), new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 225);
                }
                return;
            }
            Log.d(TAG, "SCAN RESULT:" + result.getDevice() + " Name:" + result.getDevice().getName());
            ParcelUuid[] arr = result.getDevice().getUuids();
        }
    };
    private DeviceDetailFragment mDetailFragment;
    private Fragment mPreviousFragment;
    private BluetoothLeScannerCompat mScanner;
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mScanner = BluetoothLeScannerCompat.getScanner();

        mPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String defaultDevMacAddr = mPrefs.getString(PREFS_DEFAULT_MAC_ADDR, "");

        Thread appInfoRetrieval = new Thread(() -> appInfoList = AppInfoHelper.getPackageInfo(MainActivity.this));
        appInfoRetrieval.start();

        mSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        mFilters = new ArrayList<>();
        mFilters.add(new ScanFilter.Builder().setServiceUuid(asteroidUUID).build());

        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mLocationEnableActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> btEnableAndScan()
        );
        btEnable();

        /* Start and/or attach to the Synchronization Service */
        mSyncServiceIntent = new Intent(this, SynchronizationService.class);
        startService(mSyncServiceIntent);

        if (mListFragment != null) mListFragment.scanningStarted();
        else if (mDetailFragment != null) mDetailFragment.scanningStarted();

        if (savedInstanceState == null) {
            Fragment f;
            if (defaultDevMacAddr.isEmpty()) {
                f = mListFragment = new DeviceListFragment();
                onScanRequested();
            } else {
                setTitle(mPrefs.getString(PREFS_DEFAULT_LOC_NAME, ""));
                f = mDetailFragment = new DeviceDetailFragment();
            }

            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.flContainer, f);
            ft.commit();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mStatus != IAsteroidDevice.ConnectionState.STATUS_CONNECTED)
            stopService(mSyncServiceIntent);
    }

    /* Fragments switching */
    @Override
    public void onDefaultDeviceSelected(BluetoothDevice mDevice) {
        mScanner.stopScan(scanCallback);
        mListFragment.scanningStopped();
        mDetailFragment = new DeviceDetailFragment();

        if (mListFragment != null) mListFragment.scanningStopped();
        else mDetailFragment.scanningStopped();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.flContainer, mDetailFragment)
                .commit();

        try {
            Message msg = Message.obtain(null, SynchronizationService.MSG_SET_DEVICE);
            msg.obj = mDevice;
            msg.replyTo = mDeviceDetailMessenger;
            mSyncServiceMessenger.send(msg);
        } catch (RemoteException ignored) {
        }

        onConnectRequested();

        mListFragment = null;
    }

    @Override
    public void onDefaultDeviceUnselected() {
        onScanRequested();
        mListFragment = new DeviceListFragment();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.flContainer, mListFragment)
                .commit();

        try {
            Message msg = Message.obtain(null, SynchronizationService.MSG_UNSET_DEVICE);
            msg.obj = "";
            msg.replyTo = mDeviceDetailMessenger;
            mSyncServiceMessenger.send(msg);
        } catch (RemoteException ignored) {
        }

        mDetailFragment = null;
        setTitle(R.string.app_name);
    }

    @Override
    public void onUpdateRequested() {
        try {
            Message msg = Message.obtain(null, SynchronizationService.MSG_UPDATE);
            msg.replyTo = mDeviceDetailMessenger;
            if (mSyncServiceMessenger != null)
                mSyncServiceMessenger.send(msg);
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public void onConnectRequested() {
        if (mScanner != null)
            mScanner.stopScan(scanCallback);
        try {
            Message msg = Message.obtain(null, SynchronizationService.MSG_CONNECT);
            msg.replyTo = mDeviceDetailMessenger;
            mSyncServiceMessenger.send(msg);
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public void onDisconnectRequested() {
        try {
            Message msg = Message.obtain(null, SynchronizationService.MSG_DISCONNECT);
            msg.replyTo = mDeviceDetailMessenger;
            mSyncServiceMessenger.send(msg);
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home)
            onBackPressed();

        return (super.onOptionsItemSelected(menuItem));
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
            setTitle(mPrefs.getString(PREFS_DEFAULT_LOC_NAME, ""));
            ActionBar ab = getSupportActionBar();
            if (ab != null)
                ab.setDisplayHomeAsUpEnabled(false);
        } else
            finish();
        try {
            mDetailFragment = (DeviceDetailFragment) mPreviousFragment;
        } catch (ClassCastException ignored1) {
            try {
                mListFragment = (DeviceListFragment) mPreviousFragment;
            } catch (ClassCastException ignored2) {
            }
        }
    }

    @Override
    public void onAppSettingsClicked() {
        Fragment f = new AppListFragment();

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        if (mDetailFragment != null) {
            mPreviousFragment = mDetailFragment;
            mDetailFragment = null;
        }
        if (mListFragment != null) {
            mPreviousFragment = mListFragment;
            mListFragment = null;
        }
        ft.replace(R.id.flContainer, f);
        ft.addToBackStack(null);
        ft.commit();

        setTitle(getString(R.string.notifications_settings));
        ActionBar ab = getSupportActionBar();
        if (ab != null)
            ab.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onWeatherSettingsClicked() {
        Fragment f = new WeatherSettingsFragment();

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        if (mDetailFragment != null) {
            mPreviousFragment = mDetailFragment;
            mDetailFragment = null;
        }
        if (mListFragment != null) {
            mPreviousFragment = mListFragment;
            mListFragment = null;
        }
        ft.replace(R.id.flContainer, f);
        ft.addToBackStack(null);
        ft.commit();

        setTitle(getString(R.string.weather_settings));
        ActionBar ab = getSupportActionBar();
        if (ab != null)
            ab.setDisplayHomeAsUpEnabled(true);
    }

    private void handleSetLocalName(String name) {
        if (mDetailFragment != null)
            mDetailFragment.setLocalName(name);
    }

    private void handleSetStatus(IAsteroidDevice.ConnectionState status) {
        if (mDetailFragment != null) {
            mDetailFragment.setStatus(status);
            mStatus = status;
        }
        if (status == IAsteroidDevice.ConnectionState.STATUS_CONNECTED) {
            try {
                Message msg = Message.obtain(null, SynchronizationService.MSG_REQUEST_BATTERY_LIFE);
                msg.replyTo = mDeviceDetailMessenger;
                mSyncServiceMessenger.send(msg);
            } catch (RemoteException ignored) {
            }
        }
    }

    private void handleSetBatteryPercentage(int percentage) {
        if (mDetailFragment != null)
            mDetailFragment.setBatteryPercentage(percentage);
    }

    @Override
    public void onScanRequested() {
        btEnableAndScan();
        if (mListFragment != null) mListFragment.scanningStarted();
        else if (mDetailFragment != null) mDetailFragment.scanningStarted();
    }

    private void btEnable() {
        BluetoothAdapter mBtAdapter;
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBtAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(getParent(), new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 225);
                }
                return;
            }
            mBtAdapter.enable();
        }
    }

    private void btEnableAndScan() {
        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.location_disabled_title);
            builder.setMessage(R.string.location_disabled_message);
            builder.setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                mLocationEnableActivityLauncher.launch(intent);

            });
            builder.setNegativeButton(android.R.string.no, (dialog, which) -> this.finishAffinity());
            builder.show();
        } else {
            btEnable();

            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                try {
                    mScanner.stopScan(scanCallback);
                    startScanning();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            }, 5000);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindService(mSyncServiceIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mConnection);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getDelegate().onConfigurationChanged(newConfig);
        int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (currentNightMode) {
            case Configuration.UI_MODE_NIGHT_NO:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case Configuration.UI_MODE_NIGHT_YES:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }

        finish();
        overridePendingTransition(0, 0);
        startActivity(getIntent());
    }

    private void startScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, 
                Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        ScanSettings settings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(false)
            .build();

        scanner.startScan(mFilters, settings, scanCallback);
    }

    static private class SynchronizationHandler extends Handler {
        private final MainActivity mActivity;

        SynchronizationHandler(MainActivity activity) {
            super(Looper.getMainLooper());
            mActivity = activity;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SynchronizationService.MSG_SET_LOCAL_NAME:
                    mActivity.handleSetLocalName((String) msg.obj);
                    break;
                case SynchronizationService.MSG_SET_STATUS:
                    mActivity.handleSetStatus((IAsteroidDevice.ConnectionState) msg.obj);
                    break;
                case SynchronizationService.MSG_SET_BATTERY_PERCENTAGE:
                    mActivity.handleSetBatteryPercentage(msg.arg1);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
