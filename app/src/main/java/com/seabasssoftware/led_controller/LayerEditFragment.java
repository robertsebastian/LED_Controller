package com.seabasssoftware.led_controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.apache.commons.lang3.text.WordUtils;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;

public class LayerEditFragment extends Fragment implements
        SeekBar.OnSeekBarChangeListener, AdapterView.OnItemSelectedListener, ServiceConnection,
        LedControlService.LedControlListener {
    private static String TAG = "LayerEditFragment";

    private int mLayerNum;
    private LedState mLedState;
    private ArrayAdapter<String> mPatternArray;
    private LedControlService mService;

    private ArrayList<ParameterRowSliderWrapper> mArgs = new ArrayList<ParameterRowSliderWrapper>();
    private ParameterRowSliderWrapper mAnimSpeed;
    private ParameterRowSliderWrapper mAnimStep;

    @BindView(R.id.patternSpinner)
    Spinner mPatternSpinner;

    public static LayerEditFragment create(int layerNum) {
        LayerEditFragment f = new LayerEditFragment();

        Bundle args = new Bundle();
        args.putInt("layerNum", layerNum);

        f.setArguments(args);
        return f;
    }

    public LayerEditFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLayerNum = getArguments().getInt("layerNum", 1);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.layer_edit_fragment, container, false);
        ButterKnife.bind(this, v);

        mArgs.clear();
        mArgs.add(new ParameterRowSliderWrapper((ViewGroup)v.findViewById(R.id.arg1), this));
        mArgs.add(new ParameterRowSliderWrapper((ViewGroup)v.findViewById(R.id.arg2), this));
        mArgs.add(new ParameterRowSliderWrapper((ViewGroup)v.findViewById(R.id.arg3), this));

        mAnimSpeed = new ParameterRowSliderWrapper((ViewGroup)v.findViewById(R.id.animSpeed), this);
        mAnimStep = new ParameterRowSliderWrapper((ViewGroup)v.findViewById(R.id.animStep), this);

        mPatternArray = new ArrayAdapter<String>(getContext(), android.R.layout.simple_spinner_dropdown_item);
        mPatternSpinner.setOnItemSelectedListener(this);
        mPatternSpinner.setAdapter(mPatternArray);

        // Bind to LED control service now that view is created
        Intent intent = new Intent(getContext(), LedControlService.class);
        getContext().bindService(intent, this, Context.BIND_AUTO_CREATE);

        return v;
    }

    public class ParameterRowSliderWrapper implements SeekBar.OnSeekBarChangeListener {
        private ViewGroup mParent;
        private SeekBar.OnSeekBarChangeListener mListener;
        @BindView(R.id.argSeekBar)
        SeekBar mArgSeek;
        @BindView(R.id.argName)
        TextView mArgNameText;
        @BindView(R.id.argValue)
        TextView mArgValueText;

        int mMinVal;

        public ParameterRowSliderWrapper(ViewGroup parent, SeekBar.OnSeekBarChangeListener listener) {
            mParent = parent;
            ButterKnife.bind(this, mParent);

            mListener = listener;
            mArgSeek.setOnSeekBarChangeListener(this);

            mArgValueText.setText(Integer.toString(mArgSeek.getProgress()));
            mParent.setVisibility(View.GONE);
        }

        public void configure(String name, int start, int end, int value) {
            value = Math.max(start, Math.min(end, value)); // Ensure good range
            mMinVal = start;

            mArgSeek.setMax(end - start);
            mArgSeek.setMin(0);
            mArgSeek.setProgress(value - start);
            Log.d(TAG, "MIN: " + mArgSeek.getMin() + " MAX: " + mArgSeek.getMax());
            mArgNameText.setText(WordUtils.capitalize(name));
            mParent.setVisibility(View.VISIBLE);
        }

        public int getValue() {
            return mArgSeek.getProgress() + mMinVal;
        }

        public void disable() {
            mParent.setVisibility(View.GONE);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            Log.d(TAG, "PROGRESS: " + progress + " of " + seekBar.getMax());
            mArgValueText.setText(Integer.toString(progress + mMinVal));
            mListener.onProgressChanged(seekBar, progress + mMinVal, fromUser);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            Log.d(TAG, "PROGRESS START: " + seekBar.getProgress() + " of " + seekBar.getMax());
            mArgValueText.setText(Integer.toString(seekBar.getProgress() + mMinVal));
            mListener.onStartTrackingTouch(seekBar);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            Log.d(TAG, "PROGRESS STOP: " + seekBar.getProgress() + " of " + seekBar.getMax());
            mArgValueText.setText(Integer.toString(seekBar.getProgress() + mMinVal));
            mListener.onStopTrackingTouch(seekBar);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mService != null) {
            mService.removeLedControllerListener(this);
            getContext().unbindService(this);
            mService = null;
        }
        mLedState = null;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
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
        if (mLedState == null) return;

        LedState.LayerSettings layer = mLedState.getLayer(mLayerNum);
        for (int i = 0; i < mArgs.size(); i++) {
            layer.args.set(i, mArgs.get(i).getValue());
        }
        layer.animSpeed = mAnimSpeed.getValue();
        layer.animStep = mAnimStep.getValue();

        if (isFinal) {
            mService.sendCommand(layer.getConfigCommand());
        } else {
            mService.sendCommandIfReady(layer.getConfigCommand());
        }

        Log.w(TAG, "WRITING LAYER STATE " + mLayerNum);
    }

    public void loadState() {
        if (mLedState == null) return;

        LedState.LayerSettings layer = mLedState.getLayer(mLayerNum);

        mPatternArray.clear();
        for (LedState.PatternInfo p : mLedState.patterns) {
            mPatternArray.add(p.name);
        }
        mPatternSpinner.setSelection(layer.patternNum);

        for (int i = 0; i < mArgs.size() && layer.patternNum < mLedState.patterns.size(); i++) {
            LedState.PatternInfo pat = mLedState.patterns.get(layer.patternNum);
            if (i < pat.args.size() && i < mArgs.size() && i < layer.args.size()) {
                LedState.PatternArgInfo arg = pat.args.get(i);
                mArgs.get(i).configure(arg.name, arg.start, arg.end, layer.args.get(i));
            } else if (i < mArgs.size()) {
                mArgs.get(i).disable();
            }
        }

        mAnimSpeed.configure(getString(R.string.anim_speed_text), 0, 1000, layer.animSpeed);
        mAnimStep.configure(getString(R.string.anim_step_text), 0, 30, layer.animStep);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mLedState.getLayer(mLayerNum).setPattern(position);
        loadState();
        writeState(true);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        mLedState.getLayer(mLayerNum).setPattern(0);
        writeState(true);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mService = ((LedControlService.LocalBinder)service).getService();
        mLedState = mService.getLedState();
        mService.addLedControllerListener(this);

        loadState();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mService = null;
        mLedState = null;
    }

    @Override
    public void onConnectionStateChange(LedControlService.ConnectionState newState) {
        // Don't care
    }

    @Override
    public void onLedStateChange(LedState state) {
        loadState();
    }
}
