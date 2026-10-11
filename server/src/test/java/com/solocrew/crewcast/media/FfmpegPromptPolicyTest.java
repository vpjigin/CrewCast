package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.FfmpegDownloadConsent;
import org.junit.jupiter.api.Test;

import static com.solocrew.crewcast.media.FfmpegPromptPolicy.Trigger;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegPromptPolicyTest {

    @Test
    void neverWhenFfmpegPresent() {
        assertFalse(FfmpegPromptPolicy.shouldShowPrompt(
                Trigger.START_SHARING, true, false, FfmpegDownloadConsent.UNSET, false, false));
        assertFalse(FfmpegPromptPolicy.shouldShowPrompt(
                Trigger.ADDED_NON_NATIVE_VIDEO, true, false, FfmpegDownloadConsent.UNSET, false, true));
    }

    @Test
    void neverWhileDownloadRunning() {
        assertFalse(FfmpegPromptPolicy.shouldShowPrompt(
                Trigger.START_SHARING, false, true, FfmpegDownloadConsent.UNSET, false, false));
    }

    @Test
    void startSharingWhenMissingFfmpegOncePerRun() {
        assertTrue(FfmpegPromptPolicy.shouldShowPrompt(
                Trigger.START_SHARING, false, false, FfmpegDownloadConsent.UNSET, false, false));
        assertFalse(FfmpegPromptPolicy.shouldShowPrompt(
                Trigger.START_SHARING, false, false, FfmpegDownloadConsent.UNSET, true, false));
    }

    @Test
    void startSharingSkippedAfterUserDeclined() {
        assertFalse(FfmpegPromptPolicy.shouldShowPrompt(
                Trigger.START_SHARING, false, false, FfmpegDownloadConsent.DECLINED, false, false));
    }

    @Test
    void addedMkvShowsPrompt() {
        assertTrue(FfmpegPromptPolicy.shouldShowPrompt(
                Trigger.ADDED_NON_NATIVE_VIDEO, false, false, FfmpegDownloadConsent.UNSET, false, true));
    }

    @Test
    void addedMp4OnlyDoesNotShowForAddTrigger() {
        assertFalse(FfmpegPromptPolicy.shouldShowPrompt(
                Trigger.ADDED_NON_NATIVE_VIDEO, false, false, FfmpegDownloadConsent.UNSET, false, false));
    }

    @Test
    void addTriggerSkippedAfterDecline() {
        assertFalse(FfmpegPromptPolicy.shouldShowPrompt(
                Trigger.ADDED_NON_NATIVE_VIDEO, false, false, FfmpegDownloadConsent.DECLINED, false, true));
    }

    @Test
    void toggleOnAlwaysPromptsWhenFfmpegMissing() {
        assertTrue(FfmpegPromptPolicy.shouldShowPrompt(
                Trigger.USER_ENABLED_FIX_AUDIO_TOGGLE, false, false, FfmpegDownloadConsent.DECLINED, true, false));
    }

    @Test
    void mutedHintAfterDeclineOrFailedDownload() {
        assertTrue(FfmpegPromptPolicy.shouldShowFixAudioMutedHint(false, false, FfmpegDownloadConsent.DECLINED));
        assertTrue(FfmpegPromptPolicy.shouldShowFixAudioMutedHint(false, false, FfmpegDownloadConsent.ALLOWED));
        assertFalse(FfmpegPromptPolicy.shouldShowFixAudioMutedHint(false, false, FfmpegDownloadConsent.UNSET));
        assertFalse(FfmpegPromptPolicy.shouldShowFixAudioMutedHint(true, false, FfmpegDownloadConsent.DECLINED));
    }
}
