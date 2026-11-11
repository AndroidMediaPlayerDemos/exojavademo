package com.example.exo_java_demo;

import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

/**
 * T1.1 验收回归·可断言子集（Espresso，CI 模拟器运行）：FR-1 旋转不中断、FR-2 倍速
 * 勾选态、FR-3 轨道覆盖、恢复自适应与倍速共存、FR-4 AIDL 结果显示、FR-5 历史记录与清空、
 * FR-8 坏源错误面板与恢复、FR-9 MediaPlayer 后台/前台、FR-10 WebView 加载与回退。
 * 真机限定项（手感/体感、真机弱网、FR-7 真实租户登录、杀进程历史留存）不在此列，
 * 见 TASKS.md T1.1 留档。播放状态断言不轮询界面动画，经 {@link PlayerView#getPlayer()}
 * 与 Activity 字段在主线程采样。
 */
@OptIn(markerClass = UnstableApi.class) // PlayerView / MediaPlayer 观测属 Media3 opt-in API
@RunWith(AndroidJUnit4.class)
public class AcceptanceRegressionTest {

    /** 网络源在 CI 模拟器上起播偏慢，统一放宽（与 T3.3 主策略放宽配套）。 */
    private static final long NETWORK_STARTUP_BUDGET_MS = 90_000;

    private ActivityScenario<MainActivity> scenario;

    @Before
    public void setUp() {
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(
                240, java.util.concurrent.TimeUnit.SECONDS);
        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @After
    public void tearDown() {
        scenario.close();
    }

    // ------------------------------------------------- FR-4 AIDL

    /** FR-4：自托管服务绑定后主界面显示 add(5, 6) = 11（绑定异步，轮询结果文本）。 */
    @Test
    public void aidl_selfHostedCallShowsResult() throws InterruptedException {
        assertTrue("15s 内未显示 AIDL 调用结果",
                await(15_000, a -> ((TextView) a.findViewById(R.id.aidl_status))
                        .getText().toString().contains("add(5, 6) = 11")));
    }

    // ------------------------------------------------- FR-1 旋转

    /** FR-1：横竖屏旋转（configChanges 承接）播放不中断、不从头重播。 */
    @Test
    public void rotation_playbackContinuesFromSamePosition() throws InterruptedException {
        assertTrue("默认源 60s 内未起播", await(60_000, a -> playerOf(a).isPlaying()));
        assertTrue("起播后 30s 内进度未推进到 3s",
                await(30_000, a -> playerOf(a).getCurrentPosition() >= 3_000));
        long[] posBefore = new long[1];
        scenario.onActivity(a -> posBefore[0] = playerOf(a).getCurrentPosition());

        scenario.onActivity(a ->
                a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        Thread.sleep(2_000); // 等配置变化过完一帧

        AtomicBoolean playing = new AtomicBoolean(false);
        long[] posAfter = new long[1];
        scenario.onActivity(a -> {
            Player player = playerOf(a);
            posAfter[0] = player.getCurrentPosition();
            playing.set(player.isPlaying());
        });
        assertTrue("旋转后播放中断", playing.get());
        assertTrue("旋转后进度回退（从头重播）：" + posAfter[0] + " < " + posBefore[0],
                posAfter[0] > posBefore[0]);
        scenario.onActivity(a ->
                a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
    }

    // ------------------------------------------------- FR-2 倍速勾选态

    /** FR-2：对勾标记与生效速度一致——初始 1.0x 勾选；选 2.0x 生效后重开对话框勾选随迁。 */
    @Test
    public void speedDialog_checkmarkMatchesAppliedSpeed() throws InterruptedException {
        onView(withId(R.id.button)).perform(click());
        assertRowChecked("1.0x", true);
        onView(withText("2.0x")).inRoot(isDialog()).perform(click());
        assertTrue("选 2.0x 后 10s 内未生效",
                await(10_000, a -> playerOf(a).getPlaybackParameters().speed == 2.0f));

        onView(withId(R.id.button)).perform(click());
        assertRowChecked("2.0x", true);
        assertRowChecked("1.0x", false);
        onView(withText("0.5x")).inRoot(isDialog()).perform(click()); // 顺手回归他档
        assertTrue("选 0.5x 后 10s 内未生效",
                await(10_000, a -> playerOf(a).getPlaybackParameters().speed == 0.5f));
    }

    /**
     * 断言倍速/轨道列表中指定文字行的勾选标记可见性：从 TextView 爬到条目根
     * （speed_item），再取其 ivCheckMark——不依赖条目在屏内 90% 可见的滚动断言。
     */
    private static void assertRowChecked(String label, boolean checked) {
        onView(Matchers.allOf(withText(label), isDisplayed())).inRoot(isDialog())
                .check((View view, NoMatchingViewException noView) -> {
                    if (noView != null) {
                        throw noView;
                    }
                    View mark = ((View) view.getParent()).findViewById(R.id.ivCheckMark);
                    if ((mark.getVisibility() == View.VISIBLE) != checked) {
                        throw new AssertionError(
                                label + " 勾选态应为 " + checked + "，实际 visibility="
                                        + mark.getVisibility());
                    }
                });
    }

    // ------------------------------------------------- FR-3 轨道选择

    /**
     * FR-3：多码率源可列出档位（含自动）；手动指定 180p 生成覆盖并出画；
     * 选回「自动」清覆盖且高度回升（恢复自适应）。
     */
    @Test
    public void trackDialog_overrideAppliesAndAutoRestores() throws InterruptedException {
        loadUrlViaDialog(SourceRepository.PRESETS.get(2).url); // DASH 多码率预置
        assertTrue("DASH 源 90s 内未 READY 且无轨道信息",
                await(NETWORK_STARTUP_BUDGET_MS, a -> {
                    Player player = playerOf(a);
                    return player.getPlaybackState() == Player.STATE_READY
                            && player.getCurrentTracks().getGroups().size() > 1;
                }));

        onView(withId(R.id.btn_tracks)).perform(click());
        onView(withText(R.string.tracks_video_auto)).inRoot(isDialog())
                .check(matches(isDisplayed())); // 列表含「画质：自动」
        // 两档 180p（254k/507k）都匹配前缀会致 actionOnItem 歧义，用全称唯一定位
        onView(withId(R.id.recyclerView)).inRoot(isDialog()).perform(
                RecyclerViewActions.actionOnItem(
                        ViewMatchers.hasDescendant(withText("180p · 254 kbps")),
                        click()));

        assertTrue("指定 180p 后 15s 内未生成轨道覆盖",
                await(15_000, a ->
                        playerOf(a).getTrackSelectionParameters().overrides.size() == 1));
        assertTrue("指定 180p 后 60s 内未切换到 180p 画面",
                await(60_000, a -> playerOf(a).getVideoSize().height == 180));

        onView(withId(R.id.btn_tracks)).perform(click());
        onView(withText(R.string.tracks_video_auto)).inRoot(isDialog()).perform(click());
        assertTrue("选「自动」后覆盖未清除",
                await(15_000, a ->
                        playerOf(a).getTrackSelectionParameters().overrides.isEmpty()));
        assertTrue("恢复自适应后 90s 内未回升出 180p（CI 小屏自适应组也含 270/360p）",
                await(90_000, a -> playerOf(a).getVideoSize().height > 180));
    }

    // ------------------------------------------------- FR-3 × FR-2 共存

    /**
     * FR-3：轨道选择与倍速共存互不影响——2.0x 生效后指定 180p，两者同时保持；
     * 再换 0.5x，轨道覆盖仍在、画面不离开 180p。
     */
    @Test
    public void speedAndTrackOverride_coexist() throws InterruptedException {
        loadUrlViaDialog(SourceRepository.PRESETS.get(2).url); // DASH 多码率预置
        assertTrue("DASH 源 90s 内未 READY 且无轨道信息",
                await(NETWORK_STARTUP_BUDGET_MS, a -> {
                    Player player = playerOf(a);
                    return player.getPlaybackState() == Player.STATE_READY
                            && player.getCurrentTracks().getGroups().size() > 1;
                }));

        onView(withId(R.id.button)).perform(click()); // 先倍速 2.0x
        onView(withText("2.0x")).inRoot(isDialog()).perform(click());
        assertTrue("选 2.0x 后 10s 内未生效",
                await(10_000, a -> playerOf(a).getPlaybackParameters().speed == 2.0f));

        onView(withId(R.id.btn_tracks)).perform(click()); // 再指定 180p
        onView(withId(R.id.recyclerView)).inRoot(isDialog()).perform(
                RecyclerViewActions.actionOnItem(
                        ViewMatchers.hasDescendant(withText("180p · 254 kbps")),
                        click()));
        assertTrue("指定 180p 后 60s 内未切换到 180p 画面",
                await(60_000, a -> playerOf(a).getVideoSize().height == 180));
        AtomicBoolean atSpeed = new AtomicBoolean();
        scenario.onActivity(a ->
                atSpeed.set(playerOf(a).getPlaybackParameters().speed == 2.0f));
        assertTrue("轨道指定后倍速被重置", atSpeed.get());

        onView(withId(R.id.button)).perform(click()); // 换 0.5x
        onView(withText("0.5x")).inRoot(isDialog()).perform(click());
        assertTrue("选 0.5x 后 10s 内未生效",
                await(10_000, a -> playerOf(a).getPlaybackParameters().speed == 0.5f));
        assertTrue("换倍速后轨道覆盖丢失或画面离开 180p",
                await(60_000, a ->
                        playerOf(a).getTrackSelectionParameters().overrides.size() == 1
                                && playerOf(a).getVideoSize().height == 180));
    }

    // ------------------------------------------------- FR-5 历史

    /** FR-5：URL 加载后进历史（新在前）；一键清空后历史区消失。全程同一对话框会话。 */
    @Test
    public void sourceDialog_historyRecordsAndClears() throws InterruptedException {
        // 与预置源字符串不同（加查询参数，同一 faststart 文件秒 READY）、又能唯一
        // 定位历史行：预置行的 url 视图同样会命中 withText(url)。备选 bunny trailer
        // 实测 moov 在 11MB 文件尾，渐进式要整段下完才 READY，不可用作起播源。
        String url = SourceRepository.PRESETS.get(0).url + "?hist=1";
        onView(withId(R.id.btn_source)).perform(click());
        onView(withId(R.id.source_input)).inRoot(isDialog()).perform(closeSoftKeyboard());
        onView(withId(R.id.btn_clear_history)).inRoot(isDialog()).perform(click()); // 防串场
        onView(withText(R.string.source_section_history)).inRoot(isDialog())
                .check(doesNotExist());

        onView(withId(R.id.source_input)).inRoot(isDialog())
                .perform(replaceText(url), closeSoftKeyboard());
        onView(withId(R.id.btn_load_source)).inRoot(isDialog()).perform(click());
        assertTrue("输入源 90s 内未 READY", await(NETWORK_STARTUP_BUDGET_MS,
                a -> playerOf(a).getPlaybackState() == Player.STATE_READY));

        onView(withId(R.id.btn_source)).perform(click());
        onView(withText(R.string.source_section_history)).inRoot(isDialog())
                .check(matches(isDisplayed()));
        // 历史行 title 与 url 两视图文本相同，限定 tv_source_url 避免歧义
        onView(Matchers.allOf(withId(R.id.tv_source_url), withText(url)))
                .inRoot(isDialog()).check(matches(isDisplayed()));
        onView(withId(R.id.btn_clear_history)).inRoot(isDialog()).perform(click());
        onView(withText(R.string.source_section_history)).inRoot(isDialog())
                .check(doesNotExist());
        onView(Matchers.allOf(withId(R.id.tv_source_url), withText(url)))
                .inRoot(isDialog()).check(doesNotExist());
    }

    // ------------------------------------------------- FR-8 / FR-5 坏源

    /**
     * FR-8/FR-5：不可达源出错误面板，只含错误码（FR-7 红线：无 URL/异常详情）；
     * 换回有效源面板收起并恢复 READY。
     */
    @Test
    public void badSource_errorPanelShowsCodeOnlyAndRecovers() throws InterruptedException {
        loadUrlViaDialog("https://127.0.0.1:1/unreachable.mp4"); // 端口拒绝，秒级失败
        assertTrue("坏源 20s 内未弹出错误面板",
                await(20_000, a -> a.findViewById(R.id.error_panel).getVisibility()
                        == View.VISIBLE));
        StringBuilder shown = new StringBuilder();
        scenario.onActivity(a -> shown.append(((TextView) a.findViewById(R.id.error_text))
                .getText().toString()));
        assertTrue("错误文案缺错误码：" + shown,
                shown.toString().startsWith("播放失败：")
                        && shown.toString().contains("ERROR_CODE"));
        assertTrue("错误文案泄漏了 URL/地址信息：" + shown,
                !shown.toString().contains("http") && !shown.toString().contains("127.0.0.1"));

        onView(withId(R.id.btn_source)).perform(click());
        onView(withId(R.id.source_input)).inRoot(isDialog()).perform(closeSoftKeyboard());
        onView(withText(R.string.preset_hls)).inRoot(isDialog()).perform(click());
        assertTrue("换有效源后 90s 内未 READY",
                await(NETWORK_STARTUP_BUDGET_MS, a ->
                        playerOf(a).getPlaybackState() == Player.STATE_READY));
        assertTrue("恢复起播后错误面板未收起",
                await(10_000, a -> a.findViewById(R.id.error_panel).getVisibility()
                        == View.GONE));
    }

    // ------------------------------------------------- FR-9 MediaPlayer

    /** FR-9：起播出画；切后台（onPause）暂停，回前台（onResume）恢复。 */
    @Test
    public void mediaPlayerActivity_pausesInBackgroundAndResumes() throws InterruptedException {
        try (ActivityScenario<MediaPlayerActivity> mpScenario =
                ActivityScenario.launch(MediaPlayerActivity.class)) {
            assertTrue("MediaPlayer 60s 内未起播（prepareAsync→start）",
                    awaitOn(mpScenario, 60_000, a ->
                            a.currentPlayer() != null && a.currentPlayer().isPlaying()));

            mpScenario.moveToState(Lifecycle.State.CREATED); // 至 onPause
            assertTrue("后台 10s 内未暂停",
                    awaitOn(mpScenario, 10_000, a ->
                            a.currentPlayer() == null || !a.currentPlayer().isPlaying()));

            mpScenario.moveToState(Lifecycle.State.RESUMED);
            assertTrue("回前台 15s 内未恢复播放",
                    awaitOn(mpScenario, 15_000, a ->
                            a.currentPlayer() != null && a.currentPlayer().isPlaying()));
        }
    }

    // ------------------------------------------------- FR-10 WebView

    /**
     * FR-10：HTTPS 页可加载并给出 Cookie 检测结果（未登录 → 未检测到）；
     * 返回键先回退网页历史，历史耗尽才退出页面。
     */
    @Test
    public void webViewActivity_loadsPageAndBackNavigatesHistory() throws InterruptedException {
        try (ActivityScenario<WebViewActivity> wvScenario =
                ActivityScenario.launch(WebViewActivity.class)) {
            loadPageInWebView(wvScenario, "https://example.com/");
            loadPageInWebView(wvScenario, "https://example.org/"); // 第二页，制造历史

            Espresso.pressBackUnconditionally(); // 网页历史回退，不退页面
            Thread.sleep(1_500);
            assertTrue("首个返回键不应退出页面（应回退网页历史）",
                    wvScenario.getState().isAtLeast(Lifecycle.State.RESUMED));

            Espresso.pressBackUnconditionally(); // 历史耗尽，退出
            long deadline = SystemClock.elapsedRealtime() + 10_000;
            while (wvScenario.getState() != Lifecycle.State.DESTROYED
                    && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(500);
            }
            assertTrue("第二个返回键后页面未退出", wvScenario.getState()
                    == Lifecycle.State.DESTROYED);
        }
    }

    private void loadPageInWebView(ActivityScenario<WebViewActivity> wvScenario, String url)
            throws InterruptedException {
        String missing = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getString(R.string.cookie_missing);
        wvScenario.onActivity(a -> {
            ((TextView) a.findViewById(R.id.etUrl)).setText(url);
            a.findViewById(R.id.btnLoad).performClick();
        });
        assertTrue(url + " 30s 内未完成加载并给出 Cookie 检测结果",
                awaitOn(wvScenario, 30_000, a -> missing.equals(
                        ((TextView) a.findViewById(R.id.tvCookieStatus)).getText().toString())));
    }

    // ------------------------------------------------- 公共

    /** 经源对话框输入 URL 并加载（对话框随加载 dismiss，与手工流程一致）。 */
    private void loadUrlViaDialog(String url) {
        onView(withId(R.id.btn_source)).perform(click());
        onView(withId(R.id.source_input)).inRoot(isDialog())
                .perform(replaceText(url), closeSoftKeyboard());
        onView(withId(R.id.btn_load_source)).inRoot(isDialog()).perform(click());
    }

    /** 主界面条件轮询（主线程采样，500ms 间隔）。 */
    private boolean await(long timeoutMs, Predicate<MainActivity> condition)
            throws InterruptedException {
        return awaitOn(scenario, timeoutMs, condition);
    }

    /** 任意 Activity 场景的条件轮询：每次采样经 onActivity 在主线程执行。 */
    private static <T extends android.app.Activity> boolean awaitOn(
            ActivityScenario<T> scenario, long timeoutMs, Predicate<T> condition)
            throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        AtomicBoolean result = new AtomicBoolean(false);
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity(a -> result.set(condition.test(a)));
            if (result.get()) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    private static Player playerOf(MainActivity activity) {
        PlayerView playerView = activity.findViewById(R.id.player_view);
        Player player = playerView.getPlayer();
        assertTrue("Player 未初始化", player != null);
        return player;
    }
}
