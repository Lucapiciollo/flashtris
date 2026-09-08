package com.lucapiciollo.filetto;

/**
 * Explicit table mode chosen by the host when creating a room.
 * The mode is NEVER inferred from the number of connected players: it is a
 * deliberate choice made once, before advertising starts, and never changes
 * for the lifetime of the session.
 */
public enum TournamentMode {
    /**
     * Classic 1-vs-1 Nearby match: exactly one guest can join, no queue, no
     * leaderboard, no automatic series rotation. Either side can opt into an
     * individual rematch after a match ends via REMATCH_REQUEST/REMATCH_START.
     */
    CLASSIC_P2P,

    /**
     * Multi-guest tournament table: FIFO queue, automatic best-of-N series
     * rotation, session leaderboard keyed by participant id, host-controlled
     * enrollment closure and champion proclamation. No individual rematch.
     */
    TOURNAMENT
}
