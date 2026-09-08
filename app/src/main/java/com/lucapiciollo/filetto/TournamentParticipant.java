package com.lucapiciollo.filetto;

/**
 * One participant at a tournament table, identified by a stable session id
 * (the Nearby endpoint id, or {@link TournamentEngine#HOST_ID} for the host).
 * The id -- never the nickname -- is the key used everywhere in
 * {@link TournamentEngine} (queue, seats, leaderboard) so that two guests
 * with the same nickname never collide.
 */
public final class TournamentParticipant {

    /** Lifecycle status of a single participant within the session. */
    public enum Status {
        /** Waiting in the FIFO queue for a seat. */
        QUEUED,
        /** Currently occupying seat A or seat B. */
        ACTIVE,
        /** Lost a series (or forfeited by disconnecting while active); out for the rest of the session. */
        ELIMINATED,
        /** Last participant standing after enrollment closed. */
        CHAMPION
    }

    private final String id;
    private String nickname;
    private final int joinOrder;
    private int seriesWins;
    private int eliminations;
    private Status status;

    public TournamentParticipant(String id, String nickname, int joinOrder) {
        this.id = id;
        this.nickname = (nickname == null || nickname.trim().isEmpty()) ? "Sfidante" : nickname;
        this.joinOrder = joinOrder;
        this.status = Status.QUEUED;
    }

    public String id() {
        return id;
    }

    public String nickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        if (nickname != null && !nickname.trim().isEmpty()) {
            this.nickname = nickname;
        }
    }

    /** Insertion order, used as a stable leaderboard tie-break. */
    public int joinOrder() {
        return joinOrder;
    }

    public int seriesWins() {
        return seriesWins;
    }

    public int eliminations() {
        return eliminations;
    }

    public Status status() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void recordSeriesWin() {
        seriesWins++;
    }

    /** Marks this participant as permanently out for the rest of the session. */
    public void recordElimination() {
        eliminations++;
        status = Status.ELIMINATED;
    }

    public boolean isEliminated() {
        return status == Status.ELIMINATED;
    }
}
