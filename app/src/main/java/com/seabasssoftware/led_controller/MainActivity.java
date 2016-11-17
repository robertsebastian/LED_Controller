package com.seabasssoftware.led_controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements
        ServiceConnection, LedControlService.LedControlListener {
    private static final String TAG = "MainActivity";

    //private BluetoothSocket mSocket = null;
    //private CommandSendThread mCmdThread = null;

    //private LedState mLedState = new LedState();
    private LedControlService mService;

    @BindView(R.id.viewpager) ViewPager mViewPager;
    @BindView(R.id.tabs) TabLayout mTabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Bind to LocalService
        Intent intent = new Intent(this, LedControlService.class);
        bindService(intent, this, Context.BIND_AUTO_CREATE);

        // Set up layout
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ButterKnife.bind(this);

        // Add tabs
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new GlobalEditFragment(), "Global");
        adapter.addFragment(LayerEditFragment.create(0), "Layer 1");
        adapter.addFragment(LayerEditFragment.create(1), "Layer 2");
        adapter.addFragment(LayerEditFragment.create(2), "Layer 3");
        adapter.addFragment(LayerEditFragment.create(3), "Layer 4");

        mViewPager.setAdapter(adapter);
        mTabLayout.setupWithViewPager(mViewPager);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mService = ((LedControlService.LocalBinder)service).getService();
        mService.addLedControllerListener(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mService = null;
    }

    @Override
    public void onConnectionStateChange(LedControlService.ConnectionState newState) {
        Snackbar.make(mTabLayout, newState.toString(), Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onLedStateChange(LedState state) {
        // Handled by fragments
    }

    public static class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        } else if(id == R.id.action_connect) {
            /*
            if(mCmdThread == null || !mCmdThread.isAlive()) {
                new BtConnectorTask(findViewById(R.id.toolbar)).execute();
            }
            */
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    /*
    private void sendCommand(String cmd) {
        if(mCmdThread != null && mCmdThread.isAlive()) {
            mCmdThread.sendCommand(cmd);
        } else {
            new BtConnectorTask(findViewById(R.id.toolbar)).execute();
        }
    }

    private class CommandSendThread extends Thread {
        private final LinkedBlockingQueue<String> mInQueue = new LinkedBlockingQueue<String>();
        private final OutputStream mOutputStream;

        CommandSendThread(OutputStream stream) {
            mOutputStream = stream;
        }

        @Override
        public void run() {
            try {
                while(true) {
                    try {
                        mOutputStream.write(mInQueue.take().getBytes());
                    } catch(InterruptedException e) {}
                }
            } catch(IOException e) {
                Log.w(TAG, "Error sending command: " + e.toString());
            }
        }

        public void sendCommand(String cmd) {
            mInQueue.offer(cmd);
        }
    }

    private void handleCommandResponse(String response) {
        Log.d(TAG, "RESPONSE: " + response);
        mLedState.updateFromString(response);
    }

    private class CommandResponseThread extends Thread {
        private final BufferedReader mReader;

        CommandResponseThread(InputStream stream) {
            mReader = new BufferedReader(new InputStreamReader(stream));
        }

        @Override
        public void run() {
            try {
                for(String s = mReader.readLine(); s != null; s = mReader.readLine()) {
                    final String line = s;

                    // Handle responses in main thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleCommandResponse(line);
                        }
                    });
                }
            } catch(IOException e) {
                Log.w(TAG, "Error reading command: " + e.toString());
            }
        }
    }

    private class BtConnectorTask extends AsyncTask<Void, Void, BluetoothSocket> {
        private final BluetoothAdapter mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        private final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        private final View mParentView;

        BtConnectorTask(View parentView) {
            mParentView = parentView;
        }

        @Override
        protected void onPreExecute() {
            // Request bluetooth enable
            if (!mBtAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 0);
            }
        }

        @Override
        protected BluetoothSocket doInBackground(Void ...args) {
            Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

            // Look for HC-06 device and connect, otherwise return null
            for (BluetoothDevice dev : pairedDevices) {
                if (dev.getName().equals("HC-06")) {
                    try {
                        BluetoothSocket sock = dev.createRfcommSocketToServiceRecord(SPP_UUID);
                        sock.connect();
                        return sock;
                    } catch (IOException e) {
                        Log.w(TAG, "Failed to connect bluetooth: " + e.toString());
                        return null;
                    }
                }
            }
            Log.w(TAG, "Failed to find device");
            return null;
        }

        @Override
        protected void onPostExecute(BluetoothSocket s) {
            // Set up handler threads
            try {
                if(s != null) {
                    InputStream in = s.getInputStream();
                    OutputStream out = s.getOutputStream();

                    mSocket = s;
                    new CommandResponseThread(in).start();
                    mCmdThread = new CommandSendThread(out);
                    mCmdThread.start();

                    mCmdThread.sendCommand("l");
                    mCmdThread.sendCommand("c");
                }
            } catch(IOException e) {
                Log.w(TAG, "Error getting BT socket streams: " + s.toString());
            }

            // Notify user
            if(mCmdThread != null) {
                Snackbar.make(mParentView, "Connected", Snackbar.LENGTH_LONG).show();
            } else {
                Snackbar.make(mParentView, "Connection failed!", Snackbar.LENGTH_LONG).show();
            }
        }
    }
    */
}
