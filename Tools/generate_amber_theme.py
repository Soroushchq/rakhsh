#!/usr/bin/env python3
"""
Generate TMessagesProj/src/main/assets/amber.attheme  (Amber OLED theme)

Base: darkblue.attheme (complete, battle-tested dark theme).
Transform: HSV rules -> amber palette, then hand-tuned overrides for
the exact Amber OLED identity (pure black surfaces + orange accents).

A second override table fills registry keys that darkblue.attheme does not
carry (they would otherwise fall back to LIGHT defaults because the Amber
default accent equals accentBaseColor and skips the recolor engine).

Usage: python3 Tools/generate_amber_theme.py
"""
import colorsys
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'TMessagesProj/src/main/assets/darkblue.attheme')
DST = os.path.join(ROOT, 'TMessagesProj/src/main/assets/amber.attheme')
REGISTRY = '/tmp/theme_registry.json'

# OLED surface scale (warm neutral dark)
OLED_BLACK = (0x00, 0x00, 0x00)     # 0.00-0.10
OLED_1 = (0x0A, 0x0A, 0x0B)         # 0.10-0.16
OLED_2 = (0x12, 0x12, 0x13)         # 0.16-0.22
OLED_3 = (0x17, 0x17, 0x1A)         # 0.22-0.28
OLED_4 = (0x1D, 0x1D, 0x21)         # 0.28-0.35

ACCENT = 0xFFF97316
ACCENT_LIGHT = 0xFFFB923C
ACCENT_LIGHTER = 0xFFFDBA74
ACCENT_DEEP = 0xFFB45309
GRAY_1 = 0xFF8E8E90
GRAY_2 = 0xFF9C9CA1
TEXT_MAIN = 0xFFF0E6DC

def oled_for_v(v):
    if v <= 0.10: return OLED_BLACK
    if v <= 0.16: return OLED_1
    if v <= 0.22: return OLED_2
    if v <= 0.28: return OLED_3
    return OLED_4

def rgb_to_hsv(r, g, b):
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return h * 360, s, v

def hsv_to_rgb_int(h, s, v):
    r, g, b = colorsys.hsv_to_rgb((h % 360) / 360, min(1.0, s), min(1.0, v))
    return (int(round(r * 255)), int(round(g * 255)), int(round(b * 255)))

def map_color(argb):
    a = (argb >> 24) & 0xFF
    r = (argb >> 16) & 0xFF
    g = (argb >> 8) & 0xFF
    b = argb & 0xFF
    h, s, v = rgb_to_hsv(r, g, b)

    if s < 0.08:
        return (a << 24) | (r << 16) | (g << 8) | b

    blue_family = 178 <= h <= 258
    if not blue_family:
        return (a << 24) | (r << 16) | (g << 8) | b

    if v <= 0.35:
        rr, gg, bb = oled_for_v(v)
    elif s >= 0.20 and v > 0.35:
        s2 = 0.55 + s * 0.55
        s2 = max(0.55, min(0.92, s2))
        v2 = min(1.0, v * 1.04)
        rr, gg, bb = hsv_to_rgb_int(25, s2, v2)
    else:
        rr, gg, bb = hsv_to_rgb_int(30, s * 0.15, v)
    return (a << 24) | (rr << 16) | (gg << 8) | bb

# Hand-tuned identity keys (exact Amber OLED look). Key strings must exist in
# ThemeColors.createColorKeysMap() registry; verified at the end of generation.
OVERRIDES = {
    # pure black surfaces (OLED)
    'windowBackgroundWhite': 0xFF000000,
    'windowBackgroundGray': 0xFF0A0A0B,
    'chat_wallpaper': 0xFF000000,
    'dialogBackground': 0xFF0A0A0B,
    'dialogBackgroundGray': 0xFF121213,
    'actionBarDefault': 0xF20A0A0B,
    'actionBarDefaultArchived': 0xF20A0A0B,
    'actionBarDefaultSelector': 0x1FF97316,
    'actionBarDefaultArchivedSelector': 0x1FF97316,
    'actionBarActionModeDefault': 0xF21A120B,
    'actionBarActionModeDefaultTop': 0xF20A0A0B,
    # chat bubbles: incoming = graphite, outgoing = dark amber
    'chat_inBubble': 0xFF131316,
    'chat_inBubbleSelected': 0xFF1D1D21,
    'chat_inBubbleShadow': 0x32000000,
    'chat_outBubble': 0xFF33200E,
    'chat_outBubbleSelected': 0xFF3F2712,
    'chat_outBubbleShadow': 0x32000000,
    'chat_outBubbleGradientSelectedOverlay': 0x14F97316,
    # service messages: translucent graphite
    'chat_serviceBackground': 0x82141416,
    'chat_serviceBackgroundSelected': 0x821C1C20,
    # counters / primary accents
    'chats_unreadCounter': 0xFFF97316,
    'chats_unreadCounterMuted': 0xFF8E8E90,
    # orange text accents (key names keep upstream "blue" naming)
    'dialogTextBlue': 0xFFFB923C,
    'windowBackgroundWhiteBlueText': 0xFFFB923C,
    'windowBackgroundWhiteBlueHeader': 0xFFFB923C,
    'windowBackgroundWhiteBlueIcon': 0xFFFB923C,
    'windowBackgroundWhiteValueText': 0xFFFB923C,
    'windowBackgroundChecked': 0xFFB45309,
    'avatar_backgroundActionBarBlue': 0xFF1C1C1F,
    'avatar_subtitleInProfileBlue': 0xFF9C9CA1,
    'chats_menuBackground': 0xF20A0A0B,
    'chats_menuPhone': 0xFFFB923C,
    'chats_nameMessage': 0xFFFB923C,
    'chats_name': 0xFFFB923C,
    'chat_status': 0xFFFB923C,
    'profile_status': 0xFFFB923C,
    'profile_title': 0xFFFFFFFF,
    'chat_inViews': 0xFF9C9CA1,
    'chat_inTimeText': 0xFF8E8E90,
    'chat_outTimeText': 0xFFF2B183,
    'chat_messageLinkOut': 0xFFFDBA74,
    'chat_outPreviewInstantText': 0xFFFDBA74,
    'chat_outPreviewLine': 0xFFF97316,
    'chat_outInstant': 0xFFFDBA74,
    'chat_outForwardedNameText': 0xFFFDBA74,
    'chat_outViaBotNameText': 0xFFFDBA74,
    'chat_outReplyLine': 0xFFF97316,
    'chat_outReplyMessageText': 0xFFF2B183,
    'chat_outReplyNameText': 0xFFFDBA74,
    'chat_inReplyLine': 0xFFF97316,
    'chat_inReplyNameText': 0xFFFB923C,
    'chat_messagePanelSend': 0xFFF97316,
    'chat_replyPanelIcons': 0xFFF97316,
    'chat_topPanelBackground': 0xF21A120B,
    'chat_topPanelTitle': 0xFFFDBA74,
    'chat_topPanelLine': 0xFF3F2712,
    'chat_goDownButton': 0xF2141416,
    'chat_goDownButtonCounterBackground': 0xFFF97316,
    'chat_outLoader': 0xFFF97316,
    'chat_inLoader': 0xFFF97316,
    'chat_outLoaderSelected': 0xFFFDBA74,
    'chat_inLoaderSelected': 0xFFFDBA74,
    'chat_outMediaIcon': 0xFFF2B183,
    'chat_outMediaIconSelected': 0xFFFDBA74,
    'chat_inMediaIcon': 0xFFF2B183,
    'chat_outVoiceSeekbar': 0x66B45309,
    'chat_outVoiceSeekbarFill': 0xFFFDBA74,
    'chat_outVoiceSeekbarSelected': 0xFFFDBA74,
    'chat_outAudioSeekbar': 0x66B45309,
    'chat_outAudioSeekbarFill': 0xFFFDBA74,
    'chat_inVoiceSeekbar': 0x66F97316,
    'chat_inVoiceSeekbarFill': 0xFFF97316,
    'chat_inAudioSeekbar': 0x66F97316,
    'chat_messagePanelBackground': 0xFF0E0E10,
    'chat_messagePanelIcons': 0xFF9C9CA1,
    'chat_messagePanelHint': 0xFF8E8E90,
    'chat_emojiPanelBackground': 0xFF121213,
    'chat_emojiPanelIcon': 0xFF8E8E90,
    'chat_emojiPanelIconSelected': 0xFFF97316,
    'chat_emojiPanelBackspace': 0xFF9C9CA1,
    'chat_inForwardedNameText': 0xFFFB923C,
    'chat_inViaBotNameText': 0xFFFB923C,
    'chat_inInstant': 0xFFFB923C,
    'chat_inPreviewLine': 0xFFF97316,
    'chat_inReplyMediaMessageSelectedText': 0xFF9C9CA1,
    'chat_outReplyMediaMessageSelectedText': 0xFFFDBA74,
    'chat_outReplyMediaMessageText': 0xFFF2B183,
    'chat_inReplyMediaMessageText': 0xFF9C9CA1,
    'progressCircle': 0xFFF97316,
    'dialogLineProgress': 0xFFF97316,
    'dialogLineProgressBackground': 0xFF232326,
    'dialogRadioBackground': 0xFF545458,
    'dialogCheckboxSquareUnchecked': 0xFF545458,
    'checkboxSquareBackground': 0xFFF97316,
    'radioBackgroundChecked': 0xFFF97316,
    'switchTrack': 0xFF3A3A3E,
    'switchTrackChecked': 0xFFF97316,
    'profile_actionBackground': 0xFF141416,
    'profile_actionPressedBackground': 0xFF1D1D21,
    'profile_actionIcon': 0xFFFB923C,
    'picker_enabledButton': 0xFFF97316,
    'picker_disabledButton': 0xFF3A3A3E,
    'statisticChartLine_blue': 0xFFF97316,
    'avatar_actionBarIconBlue': 0xFFFB923C,
    'chats_archiveBackground': 0xFF0A0A0B,
    'actionBarDefaultSubtitle': 0xFF9C9CA1,
    'actionBarDefaultSearch': 0xFFFFFFFF,
    'actionBarDefaultSearchPlaceholder': 0x78FB923C,
    'actionBarTabActiveText': 0xFFFB923C,
    'actionBarTabUnactiveText': 0xFF9C9CA1,
    'actionBarTabLine': 0xFFFB923C,
    'windowBackgroundWhiteGrayText': 0xFF9C9CA1,
    'windowBackgroundWhiteGrayText2': 0xFF8E8E90,
    'windowBackgroundWhiteGrayText3': 0xFF8E8E90,
    'windowBackgroundWhiteGrayIcon': 0xFF9C9CA1,
    'windowBackgroundWhiteBlackText': 0xFFF0E6DC,
    'emptyListPlaceholder': 0xFF8E8E90,
    'dialogTextBlack': 0xFFF0E6DC,
    'dialogTextGray2': 0xFF9C9CA1,
    'dialogTextGray3': 0xFF8E8E90,
    'dialogButtonSelector': 0x1FF97316,
    'dialogButton': 0xFFFB923C,
    'chat_attachAudioBackground': 0xFF33200E,
    'chat_attachContactBackground': 0xFF2E2E32,
    'chat_botKeyboardButtonText': 0xFFFB923C,
    'chat_botKeyboardButtonBackground': 0xFF1D1D21,
    'chat_botKeyboardButtonBackgroundPressed': 0xFF2E2E32,
    'chat_outFileBackground': 0xFF33200E,
    'chat_outFileBackgroundSelected': 0xFF3F2712,
    'chat_inFileBackground': 0xFF1D1D21,
    'chat_inFileBackgroundSelected': 0xFF2E2E32,
    'chat_outFileProgress': 0xFFFDBA74,
    'chat_inFileProgress': 0xFFF97316,
    'chat_outFileNameText': 0xFFF0E6DC,
    'chat_inFileNameText': 0xFFF0E6DC,
    'chat_outFileInfoText': 0xFFF2B183,
    'chat_inFileInfoText': 0xFF9C9CA1,
    'chat_outAudioTitleText': 0xFFF0E6DC,
    'chat_inAudioTitleText': 0xFFF0E6DC,
    'chat_outAudioPerfomerText': 0xFFF2B183,
    'chat_inAudioPerfomerText': 0xFF9C9CA1,
    'chat_outAudioPerfomerSelectedText': 0xFFFDBA74,
    'chat_outTimeSelectedText': 0xFFFDBA74,
    'chat_inSentClockSelectedText': 0xFF9C9CA1,
    'chat_outContactNameText': 0xFFFDBA74,
    'chat_inContactNameText': 0xFFFB923C,
    'chat_inContactPhoneText': 0xFF9C9CA1,
    'chat_outContactPhoneText': 0xFFF2B183,
    'chat_inVenueInfoText': 0xFF9C9CA1,
    'chat_outVenueInfoText': 0xFFF2B183,
    'chat_outPollBar' if False else 'chat_unreadMessagesStartText': 0xFFFB923C,
    'chat_unreadMessagesStartBackground': 0xE60A0A0B,
    'chat_inLocationBackground': 0xFF131316,
    'chat_inContactBackground': 0xFF131316,
    'chat_outBubbleGradient1_placeholder' : None,
}

del OVERRIDES['chat_outBubbleGradient1_placeholder']
OVERRIDES['chat_outPollBar'] = 0xFFFDBA74

# Registry keys missing from darkblue.attheme — without these the Amber theme
# would leak LIGHT default colors (accent id 0 == base color skips the recolor
# engine). Values hand-picked for OLED + amber identity.
OVERRIDES_MISSING = {
    # switches / checkboxes / radios / progress
    'switch2Track': 0xFF3A3A3E,
    'switch2TrackChecked': 0xFFF97316,
    'dialogCheckboxSquareBackground': 0xFFF97316,
    'dialogCheckboxSquareCheck': 0xFFFFFFFF,
    'dialogRadioBackgroundChecked': 0xFFF97316,
    'dialogInputFieldActivated': 0xFFF97316,
    'dialog_inlineProgress': 0xFFF97316,
    'dialogRoundCheckBoxCheck': 0xFFFFFFFF,
    'checkbox': 0xFFF97316,
    'checkboxCheck': 0xFFFFFFFF,
    'checkboxDisabled': 0xFF545458,
    'checkboxSquareCheck': 0xFFFFFFFF,
    'listSelectorSDK21': 0x1FF97316,
    'graySection': 0xFF121213,
    'key_graySectionText': 0xFF9C9CA1,
    # floating buttons
    'dialogFloatingButton': 0xFFF97316,
    'dialogFloatingButtonPressed': 0xFFEA580C,
    'dialogFloatingIcon': 0xFFFFFFFF,
    'chats_actionIcon': 0xFFFB923C,
    'chats_actionPressedBackground': 0x1FF97316,
    'contacts_inviteBackground': 0xFFF97316,
    'contacts_inviteText': 0xFFFFFFFF,
    'location_actionBackground': 0xFFF97316,
    'location_actionIcon': 0xFFFFFFFF,
    'location_actionPressedBackground': 0x1FF97316,
    'location_sendLocationIcon': 0xFFF97316,
    'location_sendLocationText': 0xFFF97316,
    'location_sendLiveLocationBackground': 0xFFF97316,
    'location_sendLiveLocationIcon': 0xFFFFFFFF,
    'location_sendLiveLocationText': 0xFFF97316,
    'featuredStickers_addButton': 0xFFF97316,
    'featuredStickers_addButton2': 0xFFEA580C,
    'featuredStickers_addButtonPressed': 0xFFEA580C,
    'featuredStickers_buttonText': 0xFFFFFFFF,
    'featuredStickers_unread': 0xFFF97316,
    'featuredStickers_buttonProgress': 0xFFFB923C,
    # chats list
    'chats_onlineCircle': 0xFF000000,
    'chats_verifiedCheck': 0xFFF97316,
    'chats_sentReadCheck': 0xFFFB923C,
    'chats_sentError': 0xFFE5646C,
    'chats_sentErrorIcon': 0xFFE5646C,
    'chats_mentionIcon': 0xFFF97316,
    'chats_menuItemCheck': 0xFFFB923C,
    'chats_menuName': 0xFFF0E6DC,
    'chats_menuTopBackground': 0xF20A0A0B,
    'chats_archiveIcon': 0xFF9C9CA1,
    'chats_archiveText': 0xFF9C9CA1,
    'chats_nameArchived': 0xFF9C9CA1,
    'chats_messageArchived': 0xFF8E8E90,
    'chats_nameMessageArchived_threeLines': 0xFF9C9CA1,
    'chats_unreadCounterText': 0xFFFFFFFF,
    'chats_date_bold': 0xFF9C9CA1,
    'chats_tabUnreadActiveBackground': 0xFFF97316,
    'chats_tabUnreadUnactiveBackground': 0xFF232326,
    'topics_unreadCounter': 0xFFF97316,
    'topics_unreadCounterMuted': 0xFF8E8E90,
    'chats_archivePullDownBackground': 0xFF141416,
    'chats_archivePullDownBackgroundActive': 0xFFF97316,
    'avatar_text': 0xFFFFFFFF,
    # chat
    'chat_messagePanelCursor': 0xFFF97316,
    'chat_emojiBottomPanelIcon': 0xFF9C9CA1,
    'chat_botButtonText': 0xFFFB923C,
    'chat_attachIcon': 0xFFFB923C,
    'chat_attachCheckBoxBackground': 0x66000000,
    'chat_attachCheckBoxCheck': 0xFFF97316,
    'chat_attachContactText': 0xFFF0E6DC,
    'chat_attachPermissionText': 0xFF9C9CA1,
    'chat_attachPermissionMark': 0xFF9C9CA1,
    'chat_attachPhotoBackground': 0xFF33200E,
    'chat_attachLocationBackground': 0xFF2E2E32,
    'chat_adminText': 0xFFFB923C,
    'chat_outAdminText': 0xFFFDBA74,
    'chat_mediaViews': 0xFF1A1A1C,
    'chat_mediaInfoText': 0xFFFFFFFF,
    'chat_mediaLoaderPhoto': 0xFF1D1D21,
    'chat_mediaLoaderPhotoSelected': 0xFF2E2E32,
    'chat_mediaLoaderPhotoIcon': 0xFF9C9CA1,
    'chat_mediaLoaderPhotoIconSelected': 0xFF9C9CA1,
    'chat_mediaProgress': 0xFFFFFFFF,
    'chat_mediaSentCheck': 0xFFFFFFFF,
    'chat_mediaTimeBackground': 0x66000000,
    'chat_mediaTimeText': 0xFFFFFFFF,
    'chat_outSentCheckRead': 0xFFC08A5A,
    'chat_outSentCheckReadSelected': 0xFFFDBA74,
    'chat_sentErrorIcon': 0xFFE5646C,
    'chat_outBubbleGradient': 0xFF3F2712,
    'chat_outBubbleGradient2': 0xFF331F0D,
    'chat_outBubbleGradient3': 0xFF2A1A0B,
    'chat_outBubbleGradientAnimated': 0x00000000,
    'chat_outDivider': 0x33F97316,
    'chat_inDivider': 0x33FFFFFF,
    'chat_outReplyLine2': 0xFFF97316,
    'chat_outLinkSelectBackground': 0x33F97316,
    'chat_outQuote': 0xFF3F2712,
    'chat_inReactionButtonBackground': 0xFF232326,
    'chat_outReactionButtonTextSelected': 0xFFFDBA74,
    'chat_reactionServiceButtonBackgroundSelected': 0xFFF97316,
    'chat_reactionServiceButtonTextSelected': 0xFFFFFFFF,
    'chat_recordedVoiceBackground': 0xFF3F2712,
    'chat_recordedVoiceDarkerBackground': 0xFF33200E,
    'chat_recordedVoicePlayPause': 0xFFFDBA74,
    'chat_recordedVoiceProgress': 0xFFFDBA74,
    'chat_recordedVoiceProgressInner': 0xFF3F2712,
    'chat_previewDurationText': 0xFFFFFFFF,
    'chat_previewGameText': 0xFFFFFFFF,
    'chat_gifSaveHintBackground': 0x82141416,
    'chat_gifSaveHintText': 0xFFFFFFFF,
    'chat_serviceText': 0xFFFFFFFF,
    'chat_serviceIcon': 0xFFFFFFFF,
    'chat_serviceLink': 0xFFFDBA74,
    'chat_serviceBackgroundSelector': 0x33000000,
    'chat_stickerNameText': 0xFFF0E6DC,
    'chat_stickerReplyLine': 0xFFFDBA74,
    'chat_stickerReplyNameText': 0xFFFDBA74,
    'chat_stickerReplyMessageText': 0xFFF2B183,
    'chat_stickerViaBotNameText': 0xFFFDBA74,
    'chat_TextSelectionCursor': 0xFFF97316,
    'chat_outTextSelectionCursor': 0xFFF97316,
    'chat_outTextSelectionHighlight': 0x66F97316,
    'chat_inTextSelectionHighlight': 0x66F97316,
    'chat_inPollCorrectAnswer': 0xFF7BD88F,
    'chat_inPollWrongAnswer': 0xFFE5646C,
    'chat_outPollCorrectAnswer': 0xFF7BD88F,
    'chat_outPollWrongAnswer': 0xFFE5646C,
    'chat_inPsaNameText': 0xFFFB923C,
    'chat_outPsaNameText': 0xFFFDBA74,
    'chat_inDownCall': 0xFFE5646C,
    'chat_outUpCall': 0xFFFDBA74,
    'chat_emojiPanelStickerPackSelectorLine': 0xFFF97316,
    'chat_emojiPanelStickerSetName': 0xFF9C9CA1,
    'chat_emojiPanelStickerSetNameHighlight': 0xFFFB923C,
    'chat_emojiPanelStickerSetNameIcon': 0xFF9C9CA1,
    'chat_emojiPanelNewTrending': 0xFFF97316,
    'chat_emojiSearchBackground': 0xFF1D1D21,
    'chat_emojiSearchIcon': 0xFF9C9CA1,
    'chat_messagePanelVoiceBackground': 0xFFC2410C,
    'chat_messagePanelVoicePressed': 0x33000000,
    'chat_messagePanelVoiceDuration': 0xFFFFFFFF,
    'key_chat_messagePanelVoiceLockShadow': 0x32000000,
    'chat_editMediaButton': 0xFFF97316,
    'chat_inArticleCodeBackground': 0xFF1D1D21,
    'chat_outArticleCodeBackground': 0xFF3F2712,
    'chat_inArticleCodeScrollbar': 0xFF545458,
    'chat_outArticleCodeScrollbar': 0xFFC08A5A,
    'chat_inArticleCodeScrollbarBackground': 0xFF121213,
    'chat_outArticleCodeScrollbarBackground': 0xFF2A1A0B,
    'chat_inArticleDetailsArrow': 0xFF9C9CA1,
    'chat_outArticleDetailsArrow': 0xFFF2B183,
    'chat_inArticleDetailsLine': 0xFF232326,
    'chat_outArticleDetailsLine': 0xFF3F2712,
    'chat_inTableBackground': 0xFF1D1D21,
    'chat_outTableBackground': 0xFF3F2712,
    'chat_inTableBorder': 0xFF2E2E32,
    'chat_outTableBorder': 0xFF4A3218,
    'chat_msgIvButtonDefaultInText': 0xFF9C9CA1,
    'chat_msgIvButtonDefaultOutText': 0xFFF2B183,
    'chat_msgIvButtonDefaultOut': 0x19298acf if False else 0x19F97316,
    'chat_msgIvButtonDefaultOutPressed': 0x28F97316,
    'chat_msgIvButtonDefaultInPressed': 0x28FFFFFF,
    'chat_msgIvButtonDefaultInlineOut': 0x19F97316,
    'chat_msgIvButtonDefaultInlineOutPressed': 0x28F97316,
    'chat_msgIvButtonDefaultInlineOutText': 0xFFFB923C,
    'chat_msgIvButtonDangerIn': 0x19E5646C,
    'chat_msgIvButtonDangerInPressed': 0x28E5646C,
    'chat_msgIvButtonDangerOut': 0x19E5646C,
    'chat_msgIvButtonDangerOutPressed': 0x28E5646C,
    'chat_msgIvButtonDangerOutText': 0xFFE5646C,
    'chat_msgIvButtonSuccessIn': 0x197BD88F,
    'chat_msgIvButtonSuccessInPressed': 0x287BD88F,
    'chat_msgIvButtonSuccessOut': 0x197BD88F,
    'chat_msgIvButtonSuccessOutPressed': 0x287BD88F,
    'chat_msgIvButtonSuccessOutText': 0xFF7BD88F,
    # dialogs / lists
    'dialogEmptyImage': 0xFF545458,
    'dialogEmptyText': 0xFF8E8E90,
    'dialogSwipeRemove': 0xFFF97316,
    'dialogReactionMentionBackground': 0xFFF97316,
    'dialogScrollGlow': 0xFF0A0A0B,
    'dialogLinkSelection': 0x33F97316,
    'windowBackgroundWhiteBlueButton': 0xFFFB923C,
    'windowBackgroundWhiteBlueText6': 0xFFFB923C,
    'windowBackgroundWhiteGrayText7': 0xFF8E8E90,
    'windowBackgroundGrayShadow': 0x14000000,
    'fastScrollActive': 0xFFF97316,
    'fastScrollText': 0xFFFFFFFF,
    'profile_tabSelectedLine': 0xFFFB923C,
    'profile_tabSelectedText': 0xFFFB923C,
    'contextProgressInner2': 0xFF3F2712,
    'contextProgressInner3': 0xFFB45309,
    'contextProgressInner4': 0xFFF97316,
    'contextProgressOuter2': 0xFF232326,
    'contextProgressOuter3': 0xFF545458,
    'contextProgressOuter4': 0xFF8E8E90,
    'buttonNeutral': 0xFF1D1D21,
    'buttonNeutralText': 0xFFFB923C,
    'undo_cancelColor': 0xFFFB923C,
    'undo_infoColor': 0xFF9C9CA1,
    'pollCreateIcons': 0xFFFB923C,
    'telegram_color': 0xFFF97316,
    'telegram_color_text': 0xFFFB923C,
    'telegram_color_dialogsLogo': 0xFFF97316,
    'fill_RedNormal': 0xFFE5646C,
    'fill_RedDark': 0xFFC2410C,
    'sessions_devicesImage': 0xFF33200E,
    'groupcreate_sectionShadow': 0x14000000,
    'groupcreate_spanDelete': 0xFFE5646C,
    'sharedMedia_linkPlaceholderText': 0xFF8E8E90,
    'sharedMedia_photoPlaceholder': 0xFF1A1A1C,
    'files_iconText': 0xFF9C9CA1,
    'glass_tabSelected': 0x1FF97316,
    'glass_tabSelectedText': 0xFFFB923C,
    'glass_tabUnselected': 0xFF8E8E90,
    'reactionStarSelector': 0xFFF97316,
    'stories_circle_live1': 0xFFF97316,
    'stories_circle_live2': 0xFFEA580C,
    'stickers_menuSelector': 0x1FF97316,
    'passport_authorizeText': 0xFFFB923C,
    'login_progressInner': 0xFFF97316,
    'login_progressOuter': 0xFF3A3A3E,
    'changephoneinfo_image2': 0xFFF97316,
    'picker_badgeText': 0xFFFFFFFF,
    'returnToCallMutedBackground': 0xFF3A3A3E,
    'returnToCallText': 0xFFFFFFFF,
    'actionBarBrowser': 0xF20A0A0B,
    'actionBarDefaultSearchArchivedPlaceholder': 0x78FB923C,
    'botKeyboard_button_primary': 0xFFF97316,
    'botKeyboard_button_success': 0xFF7BD88F,
    'botKeyboard_button_danger': 0xFFE5646C,
    'chat_wallpaper_gradient_to': 0xFF000000,
    'key_chat_wallpaper_gradient_to2': 0xFF000000,
    'key_chat_wallpaper_gradient_to3': 0xFF000000,
    # group calls (voip) — dark surfaces + amber accents
    'voipgroup_actionBar': 0xF20A0A0B,
    'voipgroup_actionBarItems': 0xFFF0E6DC,
    'voipgroup_actionBarItemsSelector': 0x1FF97316,
    'voipgroup_actionBarUnscrolled': 0xF20A0A0B,
    'voipgroup_checkMenu': 0xFFFB923C,
    'voipgroup_dialogBackground': 0xFF0A0A0B,
    'voipgroup_disabledButton': 0xFF3A3A3E,
    'voipgroup_disabledButtonActive': 0xFF545458,
    'voipgroup_disabledButtonActiveScrolled': 0xFF545458,
    'voipgroup_inviteMembersBackground': 0xFF141416,
    'voipgroup_lastSeenText': 0xFF8E8E90,
    'voipgroup_lastSeenTextUnscrolled': 0xFF8E8E90,
    'voipgroup_leaveButton': 0xFFE5646C,
    'voipgroup_leaveButtonScrolled': 0xFFE5646C,
    'voipgroup_leaveCallMenu': 0xFF2A1214,
    'voipgroup_listSelector': 0x1FF97316,
    'voipgroup_listViewBackground': 0xFF0A0A0B,
    'voipgroup_listViewBackgroundUnscrolled': 0xFF0A0A0B,
    'voipgroup_listeningText': 0xFFFB923C,
    'voipgroup_muteButton': 0xFFF97316,
    'voipgroup_muteButton2': 0xFFEA580C,
    'voipgroup_muteButton3': 0xFFEA580C,
    'voipgroup_mutedByAdminGradient': 0xFF3A3A3E,
    'voipgroup_mutedByAdminGradient2': 0xFF545458,
    'voipgroup_mutedByAdminGradient3': 0xFF3A3A3E,
    'voipgroup_mutedByAdminIcon': 0xFFFFFFFF,
    'voipgroup_mutedByAdminMuteButton': 0xFF545458,
    'voipgroup_mutedByAdminMuteButtonDisabled': 0xFF3A3A3E,
    'voipgroup_mutedIcon': 0xFFFFFFFF,
    'voipgroup_mutedIconUnscrolled': 0xFFFFFFFF,
    'voipgroup_nameText': 0xFFF0E6DC,
    'voipgroup_overlayAlertGradientMuted': 0xFF141416,
    'voipgroup_overlayAlertGradientMuted2': 0xFF1D1D21,
    'voipgroup_overlayAlertGradientUnmuted': 0xFF141416,
    'voipgroup_overlayAlertGradientUnmuted2': 0xFF1D1D21,
    'voipgroup_overlayAlertMutedByAdmin': 0xFF141416,
    'voipgroup_overlayAlertMutedByAdmin2': 0xFF1D1D21,
    'voipgroup_overlayBlue1': 0xFF3F2712,
    'voipgroup_overlayBlue2': 0xFF331F0D,
    'voipgroup_overlayGreen1': 0xFF2A1A0B,
    'voipgroup_overlayGreen2': 0xFF3F2712,
    'voipgroup_rtmpButton': 0xFFF97316,
    'voipgroup_scrollUp': 0xFF141416,
    'voipgroup_searchBackground': 0xFF1D1D21,
    'voipgroup_searchPlaceholder': 0xFF8E8E90,
    'voipgroup_searchText': 0xFFF0E6DC,
    'voipgroup_soundButton': 0xFF1D1D21,
    'voipgroup_soundButton2': 0xFF2E2E32,
    'voipgroup_soundButtonActive': 0xFFF97316,
    'voipgroup_soundButtonActive2': 0xFFEA580C,
    'voipgroup_soundButtonActive2Scrolled': 0xFFEA580C,
    'voipgroup_soundButtonActiveScrolled': 0xFFF97316,
    'voipgroup_speakingText': 0xFFFB923C,
    'voipgroup_topPanelBlue1': 0xFF3F2712,
    'voipgroup_topPanelBlue2': 0xFF331F0D,
    'voipgroup_topPanelGreen1': 0xFF2A1A0B,
    'voipgroup_topPanelGreen2': 0xFF3F2712,
    'voipgroup_unmuteButton': 0xFFF97316,
    'voipgroup_unmuteButton2': 0xFFEA580C,
    'voipgroup_windowBackgroundWhiteInputField': 0xFF1D1D21,
    'voipgroup_windowBackgroundWhiteInputFieldActivated': 0xFFF97316,
    'iv_ab_progress': 0xFFF97316,
}

def to_signed(c):
    c &= 0xFFFFFFFF
    return c if c < 0x80000000 else c - 0x100000000

def parse_and_generate():
    registry = json.load(open(REGISTRY))

    all_overrides = {}
    all_overrides.update(OVERRIDES)
    all_overrides.update(OVERRIDES_MISSING)

    # validate override key names against the registry
    bad = [k for k in all_overrides if k not in registry]
    if bad:
        print("WARNING: override keys not in registry (removed):")
        for k in bad:
            print("  -", k)
        for k in bad:
            del all_overrides[k]

    out_lines = []
    overridden = set()
    with open(SRC, encoding='utf-8') as f:
        for raw in f.read().splitlines():
            line = raw.strip()
            if not line or '=' not in line:
                continue
            key, val = line.split('=', 1)
            key = key.strip()
            val = val.strip()

            if key in all_overrides:
                out_lines.append(f"{key}={to_signed(all_overrides[key])}")
                overridden.add(key)
                continue

            if val.startswith('#'):
                argb = int(val.lstrip('#'), 16)
            else:
                try:
                    argb = int(val) & 0xFFFFFFFF
                except ValueError:
                    out_lines.append(line)
                    continue

            new = map_color(argb)
            out_lines.append(f"{key}={to_signed(new)}")

    # append overrides for registry keys absent from the base theme
    appended = 0
    for key, color in all_overrides.items():
        if key not in overridden:
            out_lines.append(f"{key}={to_signed(color)}")
            appended += 1

    with open(DST, 'w', encoding='utf-8') as f:
        f.write('\n'.join(out_lines) + '\n')

    print(f"wrote {DST}: {len(out_lines)} keys ({appended} appended for missing base keys)")

if __name__ == '__main__':
    parse_and_generate()
