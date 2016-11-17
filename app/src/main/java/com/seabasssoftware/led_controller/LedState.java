package com.seabasssoftware.led_controller;

import android.text.TextUtils;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LedState implements Serializable {
    private static final String TAG = "LedState";

    // State data
    private ArrayList<LayerSettings> mLayers = new ArrayList<LayerSettings>();
    public ArrayList<PatternInfo> patterns = new ArrayList<PatternInfo>();
    public int globalBrightness = 0;
    public List<Integer> sectionBrightness = Arrays.asList(0, 0, 0, 0);

    // Information about an argument that controls a pattern
    public class PatternArgInfo implements Serializable {
        public String name;
        public int start;
        public int end;

        // Initialize from command message token list
        PatternArgInfo(List<MessageArg> tok) {
            name = tok.remove(0).value;
            start = tok.remove(0).intValue;
            end = tok.remove(0).intValue;
        }
    }

    // Information about a pattern and its arguments
    public class PatternInfo implements Serializable {
        public String name = "<UNKNOWN>";
        public ArrayList<PatternArgInfo> args = new ArrayList<PatternArgInfo>();

        // Empty
        PatternInfo() {}

        // Initialize from command message token list
        PatternInfo(List<MessageArg> tok) {
            name = tok.remove(0).value;

            while(tok.size() >= 3) {
                args.add(new PatternArgInfo(tok));
            }
        }
    }

    // Container for current layer settings state
    public class LayerSettings implements Serializable {
        public int layerNum = 0;
        public int patternNum = 0;
        public List<Integer> args = Arrays.asList(0, 0, 0);
        public int animSpeed = 0;
        public int animStep = 0;

        public String getConfigCommand() {
            // Convert logarithmic slider to linear scale
            int realAnimSpeed = (int)Math.pow(10.0, (double)(1000 - animSpeed) / 1000.0 * 3.0) - 1;

            return "p" + layerNum + "," + patternNum + "," + TextUtils.join(",", args) + "\n" +
                    "a" + layerNum + "," + realAnimSpeed + "\n" +
                    "t" + layerNum + "," + animStep + "\n";
        }
    }

    // Store arguments from message
    public class MessageArg {
        String value;
        int intValue;

        MessageArg(String s) {
            value = s;
            try {
                intValue = Integer.parseInt(value);
            } catch(NumberFormatException e) {
                intValue = 0;
            }
        }
    }

    // Get layer from list, adding it if it doesn't already exist
    public LayerSettings getLayer(int index) {
        while(mLayers.size() <= index) {
            mLayers.add(new LayerSettings());
        }

        mLayers.get(index).layerNum = index;
        return mLayers.get(index);
    }

    // Return global settings as command string
    public String getGlobalConfigCommand() {
        return "b" + globalBrightness + "\n" +
               "s" + TextUtils.join(",", sectionBrightness) + "\n";
    }

    private static final Pattern mResponsePattern = Pattern.compile("^(\\p{Alpha})(.*?)");
    public void updateFromString(String line) {
        Matcher respMatcher = mResponsePattern.matcher(line);

        // Nothing to do if command doesn't match
        if(!respMatcher.matches()) {
            Log.w(TAG, "Invalid config line: " + line);
            return;
        }

        String code = respMatcher.group(1);
        Log.d(TAG, "Got code: " + code);

        ArrayList<MessageArg> args = new ArrayList<MessageArg>();
        for(String s : respMatcher.group(2).split(",")) {
            Log.d(TAG, "Got arg: " + s);
            args.add(new MessageArg(s));
        }

        if(code.equals("l")) {
            int idx = args.remove(0).intValue;

            // Add blanks as necessary
            while(patterns.size() <= idx) {
                patterns.add(new PatternInfo());
            }
            patterns.set(idx, new PatternInfo(args));

        } else if(code.equals("b")) {
            globalBrightness = args.get(0).intValue;

        } else if(code.equals("s")) {
            sectionBrightness = new ArrayList<Integer>();
            for(MessageArg a : args) {
                sectionBrightness.add(a.intValue);
            }

        } else {
            LayerSettings layer = getLayer(args.remove(0).intValue);

            if(code.equals("p")) {
                layer.patternNum = args.remove(0).intValue;
                layer.args = new ArrayList<Integer>();
                for(MessageArg a : args) {
                    layer.args.add(a.intValue);
                }
            } else if(code.equals("a")) {
                // Convert slider to logarithmic scale
                layer.animSpeed = 1000 - (int)(Math.log10(args.get(0).intValue + 1) / 3.0 * 1000.0);
            } else if(code.equals("t")) {
                layer.animStep = args.get(0).intValue;
            } else {
                // Nothing to do if not valid layer config
                Log.w(TAG, "Invalid layer config: " + line);
            }
        }
    }
}