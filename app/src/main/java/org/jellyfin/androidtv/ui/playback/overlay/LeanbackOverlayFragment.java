package org.jellyfin.androidtv.ui.playback.overlay;

import static org.koin.java.KoinJavaComponent.inject;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.PlaybackSupportFragment;

import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.preference.UserSettingPreferences;
import org.jellyfin.androidtv.ui.playback.CustomPlaybackOverlayFragment;
import org.jellyfin.androidtv.ui.playback.PlaybackController;
import org.jellyfin.androidtv.ui.playback.PlaybackControllerContainer;
import org.jellyfin.sdk.api.client.ApiClient;

import coil3.ImageLoader;
import kotlin.Lazy;
import timber.log.Timber;

public class LeanbackOverlayFragment extends PlaybackSupportFragment {
    private CustomPlaybackTransportControlGlue playerGlue;
    private VideoPlayerAdapter playerAdapter;
    private boolean shouldShowOverlay = true;
    private boolean popupShowing = false;
    private Lazy<PlaybackControllerContainer> playbackControllerContainer = inject(PlaybackControllerContainer.class);
    private final Lazy<UserSettingPreferences> userSettingPreferences = inject(UserSettingPreferences.class);
    private Lazy<ImageLoader> imageLoader = inject(ImageLoader.class);
    private Lazy<ApiClient> api = inject(ApiClient.class);
    private Lazy<UserPreferences> userPreferences = inject(UserPreferences.class);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setBackgroundType(BG_LIGHT);

        PlaybackController playbackController = playbackControllerContainer.getValue().getPlaybackController();
        if (playbackController == null) {
            Timber.w("PlaybackController is null, skipping initialization.");
            return;
        }

        playerAdapter = new VideoPlayerAdapter(playbackController, this);
        playerGlue = new CustomPlaybackTransportControlGlue(getContext(), playerAdapter, playbackController);
        playerGlue.setHost(new CustomPlaybackFragmentGlueHost(this));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        super.hideControlsOverlay(false);
    }

    public void initFromView(CustomPlaybackOverlayFragment customPlaybackOverlayFragment) {
        playerGlue.setInitialPlaybackDrawable();
        playerAdapter.setMasterOverlayFragment(customPlaybackOverlayFragment);
    }

    @Override
    public void showControlsOverlay(boolean runAnimation) {
        if (shouldShowOverlay && !popupShowing) {
            // This is called for every key press while the controls are up too, to keep them from fading
            boolean wasVisible = isControlsOverlayVisible();

            super.showControlsOverlay(runAnimation);
            playerAdapter.getMasterOverlayFragment().show();

            // Leanback puts the focus on the first button; the play button is the one to start from, but only when
            // the controls come up, or the focus would jump back to it every time another button is selected
            View root = getView();
            if (!wasVisible && root != null && playerGlue != null) root.postDelayed(() -> playerGlue.focusPlayButton(), 150);
        }
    }

    @Override
    public void hideControlsOverlay(boolean runAnimation) {
        super.hideControlsOverlay(runAnimation);
        playerAdapter.getMasterOverlayFragment().hide();
    }

    public void updateCurrentPosition() {
        playerAdapter.updateCurrentPosition();
        updatePlayState();
    }

    public void updatePlayState() {
        playerAdapter.updatePlayState();
        playerGlue.updatePlayState();
    }

    /**
     * While the panel at the bottom is up, Leanback must not bring the controls back on a left or right press: that
     * is what took the focus out of the panel.
     */
    public void setPopupShowing(boolean popupShowing) {
        this.popupShowing = popupShowing;
        setShowOrHideControlsOverlayOnUserInteraction(!popupShowing);
    }

    public void setShouldShowOverlay(boolean shouldShowOverlay) {
        this.shouldShowOverlay = shouldShowOverlay;
    }

    public void hideOverlay() {
        hideControlsOverlay(true);
    }

    public void setFading(boolean fadingEnabled) {
        playerAdapter.getMasterOverlayFragment().setFadingEnabled(fadingEnabled);
    }

    public void mediaInfoChanged() {
        org.jellyfin.sdk.model.api.BaseItemDto currentlyPlayingItem = playbackControllerContainer.getValue().getPlaybackController().getCurrentlyPlayingItem();
        if (currentlyPlayingItem == null) return;

        playerGlue.invalidatePlaybackControls();
        playerGlue.setSeekEnabled(playerAdapter.canSeek());

        long skipForwardLength = userSettingPreferences.getValue().get(UserSettingPreferences.Companion.getSkipForwardLength()).longValue();
        playerGlue.setSeekProvider(playerAdapter.canSeek() ? new CustomSeekProvider(playerAdapter, imageLoader.getValue(), api.getValue(), requireContext(), skipForwardLength) : null);
        recordingStateChanged();
        playerAdapter.updateDuration();
    }

    public void recordingStateChanged() {
        playerGlue.recordingStateChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (playerAdapter != null) {
            playerAdapter.getMasterOverlayFragment().onPause();
        }
    }

    public CustomPlaybackTransportControlGlue getPlayerGlue() {
        return playerGlue;
    }

    public void onFullyInitialized() {
        updatePlayState();
        playerGlue.addMediaActions();
    }
}
