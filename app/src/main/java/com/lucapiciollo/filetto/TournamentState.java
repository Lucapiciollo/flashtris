package com.lucapiciollo.filetto;

import java.util.Collections;
import java.util.List;

/**
 * Immutable read-only snapshot of a {@link TournamentEngine} at a point in
 * time. Used by MainActivity to render the dashboard and to build outgoing
 * protocol payloads (QUEUE_STATUS / TABLE_STATE / TOURNAMENT_FINISHED)
 * without exposing engine internals or duplicating domain logic in the UI
 * layer.
 */
public final class TournamentState {

    public final TableState tableState;
    public final String activeAId;
    public final String activeANickname;
    /** Null when seat B is currently empty (winner waiting for a challenger). */
    public final String activeBId;
    public final String activeBNickname;
    public final int scoreA;
    public final int scoreB;
    public final int winsToAdvance;
    public final int queueLength;
    /** Sorted by series wins desc, then join order asc. Never null. */
    public final List<TournamentParticipant> leaderboard;
    public final boolean finished;
    /** Null when finished with no remaining participant. */
    public final String championId;
    public final String championNickname;
    /** Null unless {@link #finished} is true. */
    public final String finishReason;

    public TournamentState(TableState tableState,
                            String activeAId, String activeANickname,
                            String activeBId, String activeBNickname,
                            int scoreA, int scoreB,
                            int winsToAdvance, int queueLength,
                            List<TournamentParticipant> leaderboard,
                            boolean finished, String championId, String championNickname,
                            String finishReason) {
        this.tableState = tableState;
        this.activeAId = activeAId;
        this.activeANickname = activeANickname;
        this.activeBId = activeBId;
        this.activeBNickname = activeBNickname;
        this.scoreA = scoreA;
        this.scoreB = scoreB;
        this.winsToAdvance = winsToAdvance;
        this.queueLength = queueLength;
        this.leaderboard = Collections.unmodifiableList(leaderboard);
        this.finished = finished;
        this.championId = championId;
        this.championNickname = championNickname;
        this.finishReason = finishReason;
    }

    public static TournamentState snapshot(TournamentEngine engine) {
        String aId = engine.seatAId();
        String bId = engine.seatBId();
        return new TournamentState(
                engine.tableState(),
                aId, engine.nicknameOf(aId),
                bId, engine.nicknameOf(bId),
                engine.seatAWins(), engine.seatBWins(),
                engine.winsToAdvance(), engine.queueLength(),
                engine.leaderboardSnapshot(),
                engine.isFinished(), engine.championId(), engine.nicknameOf(engine.championId()),
                engine.finishReason());
    }
}
