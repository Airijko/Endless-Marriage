/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.commands.subcommands;

import com.airijko.endlessleveling.util.Lang;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;

/**
 * Localization key registry + chat helpers for EndlessMarriage commands.
 * Keys live in core {@code endlessleveling.lang} under {@code ui.marriage.*}.
 */
public final class MarriageMessages {

    public static final String PREFIX = "ui.marriage.prefix";
    public static final String SHORT_PREFIX = "ui.marriage.short_prefix";
    public static final String DEBUG_PREFIX = "ui.marriage.debug_prefix";

    // MarriageMainPage UI variants
    public static final String SETHOME_SUCCESS_SHORT = "ui.marriage.sethome.success_short";
    public static final String HOME_NO_HOME_SHORT = "ui.marriage.home.no_home_short";
    public static final String DIVORCED_SIMPLE = "ui.marriage.divorced_simple";
    public static final String ACCEPTED_PRIEST_NEEDED_SIMPLE = "ui.marriage.accepted_priest_needed_simple";
    public static final String ACCEPTED_BY_SHORT = "ui.marriage.accepted_by_short";
    public static final String COOLDOWN_HM = "ui.marriage.cooldown.hm";

    // Wedding ring
    public static final String RING_MAX = "ui.marriage.ring.max";
    public static final String RING_NEED_PRESTIGE = "ui.marriage.ring.need_prestige";
    public static final String RING_UPGRADED = "ui.marriage.ring.upgraded";
    public static final String RING_SPOUSE_UPGRADED = "ui.marriage.ring.spouse_upgraded";

    // Debug
    public static final String DEBUG_PIGGY_NPC = "ui.marriage.debug.piggy_npc";
    public static final String DEBUG_PIGGY_NPC_FAIL = "ui.marriage.debug.piggy_npc_fail";
    public static final String DEBUG_KISS_NPC = "ui.marriage.debug.kiss_npc";
    public static final String DEBUG_KISS_NPC_FAIL = "ui.marriage.debug.kiss_npc_fail";

    // ProposePage
    public static final String TARGET_ALREADY_MARRIED_SHORT = "ui.marriage.target_already_married_short";
    public static final String TARGET_OFFLINE_SHORT = "ui.marriage.target_offline_short";
    public static final String RECEIVED_PROPOSAL_SHORT = "ui.marriage.received_proposal_short";
    public static final String INVALID_PLAYER = "ui.marriage.invalid_player";

    // PriestPage
    public static final String NO_PENDING_MARRIAGE_SHORT = "ui.marriage.no_pending_marriage_short";
    public static final String OFFICIATION_REQUEST_SENT = "ui.marriage.officiation_request_sent";
    public static final String OFFICIATION_REQUEST_RECEIVED = "ui.marriage.officiation_request_received";
    public static final String INVALID_PRIEST = "ui.marriage.invalid_priest";

    // RingPage
    public static final String INVALID_RING_TIER = "ui.marriage.invalid_ring_tier";
    public static final String RING_NEED_PRESTIGE_TIER = "ui.marriage.ring.need_prestige_tier";
    public static final String RING_EQUIPPED = "ui.marriage.ring.equipped";
    public static final String RING_SPOUSE_EQUIPPED = "ui.marriage.ring.spouse_equipped";

    // TieredRingBrowser
    public static final String RINGS_PREFIX = "ui.marriage.rings.prefix";
    public static final String RINGS_UNKNOWN_ID = "ui.marriage.rings.unknown_id";
    public static final String RINGS_EQUIPPED = "ui.marriage.rings.equipped";
    public static final String RINGS_FAILED = "ui.marriage.rings.failed";
    public static final String RINGS_UNEQUIPPED = "ui.marriage.rings.unequipped";

    // OfficiatePage
    public static final String MARRIAGE_NOT_PENDING = "ui.marriage.marriage_not_pending";
    public static final String CANNOT_DETERMINE_POSITION = "ui.marriage.cannot_determine_position";
    public static final String BOTH_MUST_BE_ONLINE_OFFICIATE = "ui.marriage.both_must_be_online_officiate";
    public static final String BOTH_MUST_BE_IN_WORLD = "ui.marriage.both_must_be_in_world";
    public static final String SAME_WORLD_REQUIRED = "ui.marriage.same_world_required";
    public static final String CANNOT_LOCATE_BOTH = "ui.marriage.cannot_locate_both";
    public static final String OFFICIATE_TOO_FAR = "ui.marriage.officiate_too_far";
    public static final String OFFICIATED_OF = "ui.marriage.officiated_of";
    public static final String NOW_MARRIED_OFFICIATED_BY = "ui.marriage.now_married_officiated_by";
    public static final String INVALID_PLAYERS = "ui.marriage.invalid_players";
    public static final String DIVORCE_NOT_PENDING = "ui.marriage.divorce_not_pending";
    public static final String PLAYER_NOT_MARRIED = "ui.marriage.player_not_married";
    public static final String DIVORCE_GRANTED_FOR_BOTH = "ui.marriage.divorce_granted_for_both";
    public static final String YOUR_DIVORCE_GRANTED = "ui.marriage.your_divorce_granted";

    // Generic / shared
    public static final String MUST_BE_MARRIED = "ui.marriage.must_be_married";
    public static final String NOT_MARRIED = "ui.marriage.not_married";
    public static final String SPOUSE_NOT_ONLINE = "ui.marriage.spouse_not_online";
    public static final String SPOUSE_NOT_IN_WORLD = "ui.marriage.spouse_not_in_world";
    public static final String SPOUSE_DIFFERENT_WORLD = "ui.marriage.spouse_different_world";
    public static final String CANNOT_FIND_SPOUSE = "ui.marriage.cannot_find_spouse";

    // Accept
    public static final String NO_PENDING_PROPOSALS = "ui.marriage.no_pending_proposals";
    public static final String COOLDOWN_ACCEPTOR = "ui.marriage.cooldown.acceptor";
    public static final String COOLDOWN_PROPOSER = "ui.marriage.cooldown.proposer";
    public static final String NOW_MARRIED_TO = "ui.marriage.now_married_to";
    public static final String PROPOSAL_ACCEPTED_BY = "ui.marriage.proposal_accepted_by";
    public static final String PRIEST_REQUIRED = "ui.marriage.priest_required";
    public static final String PRIEST_HINT = "ui.marriage.priest_hint";
    public static final String PROPOSER_ACCEPTED_PRIEST_NEEDED = "ui.marriage.proposer_accepted_priest_needed";

    // Deny
    public static final String PROPOSAL_DENIED = "ui.marriage.proposal_denied";
    public static final String PROPOSER_DENIED = "ui.marriage.proposer_denied";

    // Propose
    public static final String ALREADY_MARRIED_SELF = "ui.marriage.already_married_self";
    public static final String ALREADY_HAVE_PROPOSAL = "ui.marriage.already_have_proposal";
    public static final String COOLDOWN_SELF = "ui.marriage.cooldown.self";
    public static final String TARGET_OFFLINE = "ui.marriage.target_offline";
    public static final String CANNOT_PROPOSE_SELF = "ui.marriage.cannot_propose_self";
    public static final String TARGET_ALREADY_MARRIED = "ui.marriage.target_already_married";
    public static final String COOLDOWN_TARGET = "ui.marriage.cooldown.target";
    public static final String PROPOSED_TO = "ui.marriage.proposed_to";
    public static final String RECEIVED_PROPOSAL = "ui.marriage.received_proposal";

    // Divorce
    public static final String MIN_MARRIAGE_TIME = "ui.marriage.min_marriage_time";
    public static final String DIVORCED_FROM = "ui.marriage.divorced_from";
    public static final String SPOUSE_DIVORCED_YOU = "ui.marriage.spouse_divorced_you";
    public static final String DIVORCE_PENDING = "ui.marriage.divorce_pending";
    public static final String DIVORCE_MAGISTRATE_HINT = "ui.marriage.divorce_magistrate_hint";
    public static final String SPOUSE_REQUESTED_DIVORCE = "ui.marriage.spouse_requested_divorce";

    // Officiate
    public static final String PRIEST_ONLY = "ui.marriage.priest_only";
    public static final String BOTH_MUST_BE_ONLINE = "ui.marriage.both_must_be_online";
    public static final String NO_PENDING_PAIR = "ui.marriage.no_pending_pair";
    public static final String PAIR_MISMATCH = "ui.marriage.pair_mismatch";
    public static final String PRIEST_POSITION_UNKNOWN = "ui.marriage.priest_position_unknown";
    public static final String PRIEST_PLAYER_DIFF_WORLD = "ui.marriage.priest_player_diff_world";
    public static final String PRIEST_TOO_FAR = "ui.marriage.priest_too_far";
    public static final String OFFICIATED_MARRIAGE = "ui.marriage.officiated_marriage";
    public static final String NOW_MARRIED_OFFICIATED = "ui.marriage.now_married_officiated";

    // Status
    public static final String STATUS_NOT_MARRIED = "ui.marriage.status.not_married";
    public static final String STATUS_OUTGOING_PROPOSAL = "ui.marriage.status.outgoing_proposal";
    public static final String STATUS_INCOMING_PROPOSAL = "ui.marriage.status.incoming_proposal";
    public static final String STATUS_PENDING_PRIEST = "ui.marriage.status.pending_priest";
    public static final String STATUS_MARRIED_TO = "ui.marriage.status.married_to";
    public static final String STATUS_SINCE = "ui.marriage.status.since";
    public static final String STATUS_OFFICIATED_BY = "ui.marriage.status.officiated_by";
    public static final String STATUS_PENDING_DIVORCE = "ui.marriage.status.pending_divorce";

    // Home
    public static final String HOME_NO_HOME = "ui.marriage.home.no_home";
    public static final String HOME_WORLD_MISSING = "ui.marriage.home.world_missing";
    public static final String HOME_TELEPORTING = "ui.marriage.home.teleporting";

    // SetHome
    public static final String SETHOME_MUST_BE_MARRIED = "ui.marriage.sethome.must_be_married";
    public static final String SETHOME_POSITION_UNKNOWN = "ui.marriage.sethome.position_unknown";
    public static final String SETHOME_SUCCESS = "ui.marriage.sethome.success";

    // TpPartner
    public static final String TP_CANNOT_LOCATE = "ui.marriage.tp.cannot_locate";
    public static final String TP_TELEPORTING_TO = "ui.marriage.tp.teleporting_to";

    // Inventory
    public static final String INV_CANNOT_ACCESS = "ui.marriage.inv.cannot_access";
    public static final String INV_VIEWING = "ui.marriage.inv.viewing";

    // Kiss
    public static final String KISS_SUCCESS_SELF = "ui.marriage.kiss.success_self";
    public static final String KISS_RECEIVED = "ui.marriage.kiss.received";
    public static final String KISS_TOO_FAR = "ui.marriage.kiss.too_far";
    public static final String KISS_ERROR = "ui.marriage.kiss.error";

    // Piggyback
    public static final String PIGGYBACK_DISMOUNT_SELF = "ui.marriage.piggyback.dismount_self";
    public static final String PIGGYBACK_SHAKE_OFF = "ui.marriage.piggyback.shake_off";
    public static final String PIGGYBACK_SUCCESS_SELF = "ui.marriage.piggyback.success_self";
    public static final String PIGGYBACK_SPOUSE_RIDING = "ui.marriage.piggyback.spouse_riding";
    public static final String PIGGYBACK_TOO_FAR = "ui.marriage.piggyback.too_far";
    public static final String PIGGYBACK_ALREADY_MOUNTED = "ui.marriage.piggyback.already_mounted";
    public static final String PIGGYBACK_SPOUSE_BUSY = "ui.marriage.piggyback.spouse_busy";
    public static final String PIGGYBACK_ERROR = "ui.marriage.piggyback.error";

    // Dismount
    public static final String DISMOUNT_NOT_IN_SESSION = "ui.marriage.dismount.not_in_session";

    // FindPriest
    public static final String FINDPRIEST_NO_PENDING = "ui.marriage.findpriest.no_pending";

    // Reload
    public static final String RELOAD_MUST_BE_OP = "ui.marriage.reload.must_be_op";
    public static final String RELOAD_SUCCESS = "ui.marriage.reload.success";
    public static final String RELOAD_FAILED = "ui.marriage.reload.failed";

    // Records
    public static final String RECORDS_NONE = "ui.marriage.records.none";
    public static final String RECORDS_HEADER = "ui.marriage.records.header";
    public static final String RECORDS_ENTRY = "ui.marriage.records.entry";
    public static final String RECORDS_TYPE_MARRIAGE = "ui.marriage.records.type.marriage";
    public static final String RECORDS_TYPE_DIVORCE = "ui.marriage.records.type.divorce";

    // GrantDivorce
    public static final String GRANT_MAGISTRATE_ONLY = "ui.marriage.grant.magistrate_only";
    public static final String GRANT_NO_REQUEST = "ui.marriage.grant.no_request";
    public static final String GRANT_NOT_MARRIED = "ui.marriage.grant.not_married";
    public static final String GRANT_GRANTED_FOR = "ui.marriage.grant.granted_for";
    public static final String GRANT_YOUR_DIVORCE_GRANTED = "ui.marriage.grant.your_divorce_granted";
    public static final String GRANT_SPOUSE_DIVORCE_GRANTED = "ui.marriage.grant.spouse_divorce_granted";

    // Announcer
    public static final String ANNOUNCE_TITLE = "ui.marriage.announce.title";
    public static final String ANNOUNCE_SUBTITLE = "ui.marriage.announce.subtitle";
    public static final String ANNOUNCE_CHAT = "ui.marriage.announce.chat";
    public static final String ANNOUNCE_OFFICIANT_SUFFIX = "ui.marriage.announce.officiant_suffix";

    /**
     * Centralized chat palette, mirroring EndlessLeveling core's {@code ChatMessageStrings.Color}.
     * The brand-colored prefix is rendered separately from the body so the tag pops the same way
     * the {@code [EndlessLeveling]} prefix does, with the body free to carry its own semantic color.
     */
    public static final class Color {
        /** Romance-rose brand color for the {@code [Endless Marriage]} / {@code [Marriage]} prefixes. */
        public static final String PREFIX_BRAND = "#ff7fb0";
        /** Muted slate for the {@code [Marriage Debug]} / {@code [Marriage Admin]} prefixes. */
        public static final String PREFIX_MUTED = "#9fb6d3";

        public static final String SUCCESS = "#66ff66";
        public static final String ERROR = "#ff6666";
        public static final String INFO = "#4fd7f7";
        public static final String WARN = "#ff9900";
        /** Pink accent used for affection flavor (kiss, piggyback, ring sparkle). */
        public static final String ROMANCE = "#f2a2e8";
        public static final String MUTED = "#9fb6d3";

        private Color() {
        }
    }

    private MarriageMessages() {
    }

    /** Joins a brand-colored prefix to an already-colored body, EL-core {@code prefixed(..)} style. */
    @Nonnull
    private static Message prefixed(@Nonnull String prefixText, @Nonnull String prefixColor, @Nonnull Message body) {
        String tag = prefixText.endsWith(" ") ? prefixText : prefixText + " ";
        return Message.join(Message.raw(tag).color(prefixColor), body);
    }

    /** Localized PREFIX-prepended chat line. Args feed into {0}, {1} of {@code key}. */
    @Nonnull
    public static Message chat(@Nonnull String key, @Nonnull String color, Object... args) {
        return prefixed(Lang.tr(PREFIX, "[Endless Marriage] "), Color.PREFIX_BRAND,
                Message.raw(Lang.tr(key, key, args)).color(color));
    }

    /** Same as {@link #chat} but uses the shorter {@code [Marriage]} prefix used by the main UI page. */
    @Nonnull
    public static Message shortChat(@Nonnull String key, @Nonnull String color, Object... args) {
        return prefixed(Lang.tr(SHORT_PREFIX, "[Marriage] "), Color.PREFIX_BRAND,
                Message.raw(Lang.tr(key, key, args)).color(color));
    }

    /** Same as {@link #chat} but uses the {@code [Marriage Debug]} prefix for dev/admin paths. */
    @Nonnull
    public static Message debugChat(@Nonnull String key, @Nonnull String color, Object... args) {
        return prefixed(Lang.tr(DEBUG_PREFIX, "[Marriage Debug] "), Color.PREFIX_MUTED,
                Message.raw(Lang.tr(key, key, args)).color(color));
    }

    /** Same as {@link #chat} but uses the {@code [Rings]} prefix used by the tiered ring browser. */
    @Nonnull
    public static Message ringsChat(@Nonnull String key, @Nonnull String color, Object... args) {
        return prefixed(Lang.tr(RINGS_PREFIX, "[Rings] "), Color.PREFIX_BRAND,
                Message.raw(Lang.tr(key, key, args)).color(color));
    }

    /** Localized line without the marriage PREFIX. */
    @Nonnull
    public static Message line(@Nonnull String key, @Nonnull String color, Object... args) {
        return Message.raw(Lang.tr(key, key, args)).color(color);
    }

    // --- Raw (non-localized) builders for inline call sites that compose their own text ---

    /** Brand prefix {@code [Endless Marriage]} + an already-built raw body string. */
    @Nonnull
    public static Message prefixedLine(@Nonnull String body, @Nonnull String color) {
        return prefixed(Lang.tr(PREFIX, "[Endless Marriage] "), Color.PREFIX_BRAND, Message.raw(body).color(color));
    }

    /** Brand prefix {@code [Marriage]} + an already-built raw body string. */
    @Nonnull
    public static Message shortLine(@Nonnull String body, @Nonnull String color) {
        return prefixed(Lang.tr(SHORT_PREFIX, "[Marriage] "), Color.PREFIX_BRAND, Message.raw(body).color(color));
    }

    /** Muted prefix {@code [Marriage Debug]} + an already-built raw body string. */
    @Nonnull
    public static Message debugLine(@Nonnull String body, @Nonnull String color) {
        return prefixed(Lang.tr(DEBUG_PREFIX, "[Marriage Debug] "), Color.PREFIX_MUTED, Message.raw(body).color(color));
    }

    /** Muted prefix {@code [Marriage Admin]} + an already-built raw body string. */
    @Nonnull
    public static Message adminLine(@Nonnull String body, @Nonnull String color) {
        return prefixed("[Marriage Admin] ", Color.PREFIX_MUTED, Message.raw(body).color(color));
    }

    /** Resolve a key to its localized String (no formatting wrapper). */
    @Nonnull
    public static String text(@Nonnull String key, Object... args) {
        return Lang.tr(key, key, args);
    }
}
