package com.seabasssoftware.led_controller;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LedControlService extends Service {
    private static final String TAG = "LedControlService";

    private final IBinder mBinder = new LocalBinder();
    private BluetoothSocket mSocket;
    private ConnectionState mConnectionState;
    private CommandSendThread mSendThread;
    private CommandResponseThread mRespThread;
    private ArrayList<LedControlListener> mListeners = new ArrayList<LedControlListener>();
    private LedState mLedState = new LedState();
    private Handler mHandler;

    // State of bluetooth connection to LED controller board
    public enum ConnectionState {DISCONNECTED, CONNECTING, CONNECTED}

    // Since this runs in the same process as client, just let the client get a direct reference and
    // call methods directly.
    public class LocalBinder extends Binder {
        LedControlService getService() {
            return LedControlService.this;
        }
    }

    // State updates
    public interface LedControlListener {
        void onConnectionStateChange(ConnectionState newState);
        void onLedStateChange(LedState state);
    }

    public LedState getLedState() {
        return mLedState;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind for LedControlService");
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mHandler = new Handler();
        startConnecting(0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close socket", e);
            }
            mSocket = null;
        }
    }

    public void addLedControllerListener(LedControlListener l) {
        mListeners.add(l);
    }

    public void removeLedControllerListener(LedControlListener l) {
        mListeners.remove(l);
    }

    private void fireConnectionStateChangeEvent() {
        Log.d(TAG, "Connection status: " + mConnectionState.toString());
        for(LedControlListener l: mListeners) l.onConnectionStateChange(mConnectionState);
    }

    private void fireLedStateChangeEvent() {
        for(LedControlListener l: mListeners) l.onLedStateChange(mLedState);
    }

    public boolean isConnected() {
        return mRespThread != null && mRespThread.isAlive();
    }

    public void startConnecting(long delayMs) {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(mConnectionState != ConnectionState.CONNECTING) {
                    // Stop send thread if running
                    if (mSendThread != null && mSendThread.isAlive()) {
                        mSendThread.sendCommand("<END>");
                    }

                    // Close socket if open -- this will kill receive thread if it is running
                    if (mSocket != null) {
                        try {
                            mSocket.close();
                        } catch (IOException e) {
                            // Nothing to do
                        }
                    }

                    // Kick off connection task
                    new ConnectorTask().execute();
                }
            }
        }, delayMs);
    }

    public void sendCommand(String cmd) {
        // Try to connect if we aren't already doing it
        if(isConnected() && mSendThread != null && mSendThread.isAlive()) {
            mSendThread.sendCommand(cmd);
        } else {
            startConnecting(0);
        }
    }

    public void sendCommandIfReady(String cmd) {
        // Try to connect if we aren't already doing it
        if(mSendThread != null && mSendThread.isAlive()) {
            mSendThread.sendCommandIfReady(cmd);
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
                        String s = mInQueue.poll(1, TimeUnit.SECONDS);

                        if(s != null) {
                            // Exit when we get a special string
                            if(s.equals("<END>")) break;

                            // Otherwise write to stream
                            mOutputStream.write(s.getBytes());
                            Log.d(TAG, "Send command: " + s);

                            sleep(100, 0); // Give controller board 100ms to handle message
                        }
                    } catch(InterruptedException e) {
                        // Don't care
                    }
                }
            } catch(IOException e) {
                Log.w(TAG, "Error sending command: " + e.toString());
            }
        }

        public void sendCommand(String cmd) {
            mInQueue.offer(cmd);
        }

        public void sendCommandIfReady(String cmd) {
            if(mInQueue.isEmpty()) {
                mInQueue.offer(cmd);
            }
        }
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
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "RESPONSE: " + line);
                            mLedState.updateFromString(line);
                            fireLedStateChangeEvent();
                        }
                    });
                }
            } catch(IOException e) {
                Log.w(TAG, "Error reading command: " + e.toString());
            }

            // Reconnect if we get an IO error
            startConnecting(0);
        }
    }

    private class ConnectorTask extends AsyncTask<Void, Void, BluetoothSocket> {
        private final BluetoothAdapter mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        private final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

        @Override
        protected void onPreExecute() {
            mConnectionState = ConnectionState.CONNECTING;
            fireConnectionStateChangeEvent();
        }

        @Override
        protected BluetoothSocket doInBackground(Void ...args) {
            Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();


            // Look for HC-06 device and connect, otherwise return null
            for (BluetoothDevice dev : pairedDevices) {
                if (dev.getName().equals("HC-06")) {
                    BluetoothSocket sock;

                    // Create socket
                    try {
                        sock = dev.createRfcommSocketToServiceRecord(SPP_UUID);
                    } catch(IOException e) {
                        Log.w(TAG, "Error creating bluetooth socket: " + e.toString());
                        return null;
                    }

                    // Try to connect
                    try {
                        // First try normal method
                        sock.connect();
                    } catch(IOException e) {
                        // If that fails try again using this workaround with a non-public API method
                        try {
                            Log.w(TAG, "Using fallback socket connection");
                            sock = (BluetoothSocket) dev.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(dev, 1);
                            sock.connect();
                        } catch(Exception e2) {
                            Log.w(TAG, "Failed to connect bluetooth: " + e.toString());
                            return null;
                        }
                    }

                    return sock;
                }
            }

            Log.w(TAG, "Failed to find device");
            return null;
        }

        @Override
        protected void onPostExecute(BluetoothSocket s) {
            try {
                if(s == null) {
                    mConnectionState = ConnectionState.DISCONNECTED;
                } else {
                    // Set up connection objects
                    InputStream in = s.getInputStream();
                    OutputStream out = s.getOutputStream();
                    mSocket = s;

                    // Kick off communcation threads
                    mRespThread = new CommandResponseThread(in);
                    mRespThread.start();

                    mSendThread = new CommandSendThread(out);
                    mSendThread.start();

                    // Request layer options and current state of everything from LED board
                    mSendThread.sendCommand("l");
                    mSendThread.sendCommand("c");

                    mConnectionState = ConnectionState.CONNECTED;
                }
            } catch(IOException e) {
                mConnectionState = ConnectionState.DISCONNECTED;
                Log.w(TAG, "Error getting BT socket streams: " + s.toString());
            }

            // If connection attempt fails, retry in three seconds
            if(mConnectionState == ConnectionState.DISCONNECTED) {
                Log.d(TAG, "Retrying connection in 3 seconds");
                startConnecting(3000);
            }

            // Let everyone know the new state if any
            fireConnectionStateChangeEvent();
        }
    }
}
