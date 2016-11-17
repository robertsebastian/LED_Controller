package com.seabasssoftware.led_controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;

public class GlobalEditFragment extends Fragment implements
        SeekBar.OnSeekBarChangeListener, LedControlService.LedControlListener, ServiceConnection {
    private final ArrayList<SeekBar> mBars = new ArrayList<SeekBar>();
    private LedState mLedState = null;
    private LedControlService mService;

    @BindView(R.id.globalBrightnessSeekBar) SeekBar mGlobalSeek;
    @BindView(R.id.topBrightnessSeekBar) SeekBar mTopSeek;
    @BindView(R.id.backBrightnessSeekBar) SeekBar mBackSeek;
    @BindView(R.id.chainBrightnessSeekBar) SeekBar mChainSeek;
    @BindView(R.id.bottomBrightnessSeekBar) SeekBar mBottomSeek;

    public GlobalEditFragment() {
        // Required empty constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Bind to LED control service
        Intent intent = new Intent(context, LedControlService.class);
        context.bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        if(mService != null) mService.removeLedControllerListener(this);
        mService = null;
        mLedState = null;

        super.onDetach();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.global_edit_fragment, container, false);
        ButterKnife.bind(this, v);

        mBars.add(mTopSeek);
        mBars.add(mBackSeek);
        mBars.add(mChainSeek);
        mBars.add(mBottomSeek);

        mGlobalSeek.setOnSeekBarChangeListener(this);
        for(SeekBar sb : mBars) {
            sb.setOnSeekBarChangeListener(this);
        }

        loadState();

        return v;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if(fromUser) {
            writeState(false);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        writeState(true);
    }

    public void writeState(boolean isFinal) {
        if(mLedState == null && mService == null) return;

        mLedState.globalBrightness = mGlobalSeek.getProgress();
        mLedState.sectionBrightness.set(0, mTopSeek.getProgress());
        mLedState.sectionBrightness.set(1, mBackSeek.getProgress());
        mLedState.sectionBrightness.set(2, mChainSeek.getProgress());
        mLedState.sectionBrightness.set(3, mBottomSeek.getProgress());

        if(isFinal) {
            mService.sendCommand(mLedState.getGlobalConfigCommand());
        } else {
            mService.sendCommandIfReady(mLedState.getGlobalConfigCommand());
        }
    }

    public void loadState() {
        if(mLedState == null) return;

        mGlobalSeek.setProgress(mLedState.globalBrightness);
        mTopSeek.setProgress(mLedState.sectionBrightness.get(0));
        mBackSeek.setProgress(mLedState.sectionBrightness.get(1));
        mChainSeek.setProgress(mLedState.sectionBrightness.get(2));
        mBottomSeek.setProgress(mLedState.sectionBrightness.get(3));
    }

    @Override
    public void onConnectionStateChange(LedControlService.ConnectionState newState) {
        // Don't care
    }

    @Override
    public void onLedStateChange(LedState state) {
        loadState();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mService = ((LedControlService.LocalBinder)service).getService();
        mLedState = mService.getLedState();
        mService.addLedControllerListener(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mService = null;
        mLedState = null;
    }
}