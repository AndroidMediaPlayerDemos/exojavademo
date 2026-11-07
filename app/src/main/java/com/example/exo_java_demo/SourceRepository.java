package com.example.exo_java_demo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 播放源仓库：预置示例源 + 最近播放历史（SharedPreferences 持久化）。
 * 多码率预置源同时供 FR-3 轨道选择使用。
 */
public class SourceRepository {

    /** 预置示例源。 */
    public static class Preset {
        public final int labelRes;
        public final String url;

        Preset(int labelRes, String url) {
            this.labelRes = labelRes;
            this.url = url;
        }
    }

    // 原 TearsOfSteel 源所在 gtv-videos-bucket 已整桶 403（2026-11 实测），
    // 默认源换为 W3C 托管的 Sintel 预告片；三个预置源均验证过可达。
    public static final List<Preset> PRESETS = Collections.unmodifiableList(Arrays.asList(
            new Preset(R.string.preset_mp4, "https://media.w3.org/2010/05/sintel/trailer.mp4"),
            new Preset(R.string.preset_hls,
                    "https://devstreaming-cdn.apple.com/videos/streaming/examples/"
                            + "img_bipbop_adv_example_fmp4/master.m3u8"),
            new Preset(R.string.preset_dash,
                    "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd")));

    private static final String PREFS_NAME = "playback_sources";
    private static final String KEY_HISTORY = "history";
    private static final int HISTORY_LIMIT = 10;

    private final SharedPreferences prefs;

    public SourceRepository(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 默认起播源：第一个预置源。 */
    public String defaultUrl() {
        return PRESETS.get(0).url;
    }

    /** 最近播放历史，新在前，已去重，上限 10 条。 */
    public List<String> getHistory() {
        List<String> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                result.add(array.getString(i));
            }
        } catch (JSONException e) {
            // 历史数据损坏时按空历史处理，不向调用方抛出
        }
        return result;
    }

    public void addToHistory(String url) {
        List<String> history = new ArrayList<>(getHistory());
        history.remove(url); // 已存在则上浮到最前
        history.add(0, url);
        while (history.size() > HISTORY_LIMIT) {
            history.remove(history.size() - 1);
        }
        JSONArray array = new JSONArray();
        for (String item : history) {
            array.put(item);
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply();
    }

    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }
}
