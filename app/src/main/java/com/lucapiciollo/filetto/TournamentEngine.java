package com.lucapiciollo.filetto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java domain engine for a Nearby tournament table. Contains ZERO
 * Android/Nearby dependencies on purpose, so it can be exercised by plain
 * JUnit tests without Robolectric/instrumentation.
 *
 * <p>Responsibilities: FIFO enrollment queue (keyed by stable participant id,
 * never nickname), seat assignment, best-of-N series scoring
 * ({@link #winsToAdvance()}), rotation after a series is decided (winner
 * stays, loser eliminated, next challenger promoted from the queue),
 * enrollment closure semantics (OPEN -&gt; CLOSED -&gt; FINISHED, one-way),
 * champion proclamation, disconnection handling (queued vs. active forfeit)
 * and a match generation counter used by the caller to guard against stale
 * delayed callbacks (e.g. an Android {@code postDelayed} scheduled for a
 * match that has since been superseded by a new rotation).
 *
 * <p>Everything Android/Nearby-transport-specific (accepting connections,
 * sending JSON messages, showing UI) stays in {@code MainActivity}, which
 * only reacts to the outcome objects returned by this class.
 */
public final class TournamentEngine {

    /** Stable id used for the host seat, distinct from any possible Nearby endpoint id. */
    public static final String HOST_ID = "\u0000HOST\u0000";

    /** Result of {@link #admitGuest(String, String)}. */
    public enum AdmitOutcome {
        /** No one was waiting for a challenger: this guest was seated immediately. */
        SEATED_AS_CHALLENGER,
        /** A seat was already taken: this guest was appended to the FIFO queue. */
        QUEUED,
        /** Enrollment is CLOSED or FINISHED: the guest was not admitted. */
        REJECTED_CLOSED,
        /** The table already has the maximum number of participants (host + guests). */
        REJECTED_FULL,
        /** This id was already known (duplicate NICKNAME message); nickname text was refreshed, nothing else changed. */
        IGNORED_DUPLICATE
    }

    /** Outcome of a series conclusion or a forfeit, describing what rotation happened. */
    public static final class RotationOutcome {
        public final String eliminatedId;
        /** Null if the eliminated participant had no opponent (was waiting alone). */
        public final String stayingId;
        /** Non-null only when a new challenger was promoted from the queue into the vacated seat. */
        public final String promotedChallengerId;
        /** True when the staying participant now has no opponent and the queue is empty. */
        public final boolean waitingForChallenger;
        public final boolean championDeclared;
        /** Non-null only when {@link #championDeclared} is true and someone remains. */
        public final String championId;
        public final String finishReason;

        RotationOutcome(String eliminatedId, String stayingId, String promotedChallengerId,
                         boolean waitingForChallenger, boolean championDeclared,
                         String championId, String finishReason) {
            this.eliminatedId = eliminatedId;
            this.stayingId = stayingId;
            this.promotedChallengerId = promotedChallengerId;
            this.waitingForChallenger = waitingForChallenger;
            this.championDeclared = championDeclared;
            this.championId = championId;
            this.finishReason = finishReason;
        }
    }

    /** Outcome of {@link #disconnect(String)}. */
    public static final class DisconnectOutcome {
        public final boolean known;
        public final boolean wasQueued;
        public final boolean wasActiveSeat;
        /** Non-null only when an active-seat disconnect left rotation info to react to. */
        public final RotationOutcome rotation;

        private DisconnectOutcome(boolean known, boolean wasQueued, boolean wasActiveSeat, RotationOutcome rotation) {
            this.known = known;
            this.wasQueued = wasQueued;
            this.wasActiveSeat = wasActiveSeat;
            this.rotation = rotation;
        }

        static DisconnectOutcome unknown() {
            return new DisconnectOutcome(false, false, false, null);
        }

        static DisconnectOutcome queueOnly(boolean wasQueued) {
            return new DisconnectOutcome(true, wasQueued, false, null);
        }

        static DisconnectOutcome activeSeat(boolean wasQueued, RotationOutcome rotation) {
            return new DisconnectOutcome(true, wasQueued, true, rotation);
        }
    }

    /** Outcome of {@link #recordGameResult(char)}. */
    public static final class MatchOutcome {
        public final boolean seriesDecided;

        MatchOutcome(boolean seriesDecided) {
            this.seriesDecided = seriesDecided;
        }
    }

    private final Map<String, TournamentParticipant> participants = new LinkedHashMap<>();
    private final LinkedList<String> queue = new LinkedList<>();
    private final int maxGuests;
    private final int winsToAdvance;
    private int joinCounter = 0;

    private TableState tableState = TableState.OPEN;

    private String seatAId;
    private String seatBId;
    private char seatASymbol = 'X';
    private char seatBSymbol = 'O';
    private int seatAWins;
    private int seatBWins;

    private int matchGeneration;
    private String championId;
    private String finishReason;

    public TournamentEngine(int winsToAdvance, int maxGuests) {
        this.winsToAdvance = Math.max(1, winsToAdvance);
        this.maxGuests = Math.max(1, maxGuests);
    }

    // ---------------------------------------------------------------- setup

    /** Seats the host in seat A with the chosen symbol. Must be called exactly once, before any guest is admitted. */
    public void seatHostFirst(String hostNickname, char hostSymbol) {
        TournamentParticipant hostP = new TournamentParticipant(HOST_ID, hostNickname, joinCounter++);
        hostP.setStatus(TournamentParticipant.Status.ACTIVE);
        participants.put(HOST_ID, hostP);
        seatAId = HOST_ID;
        seatASymbol = hostSymbol;
        seatBSymbol = hostSymbol == 'X' ? 'O' : 'X';
        seatBId = null;
        seatAWins = 0;
        seatBWins = 0;
        matchGeneration++;
    }

    // ------------------------------------------------------------ admission

    /**
     * Admits a guest by stable id. The very first free seat found is filled
     * immediately; anyone else is appended to the FIFO queue. Duplicate ids
     * (resent NICKNAME messages) are ignored except for a nickname refresh.
     */
    public AdmitOutcome admitGuest(String id, String nickname) {
        if (id == null) {
            return AdmitOutcome.IGNORED_DUPLICATE;
        }
        TournamentParticipant existing = participants.get(id);
        if (existing != null) {
            existing.setNickname(nickname);
            return AdmitOutcome.IGNORED_DUPLICATE;
        }
        if (tableState != TableState.OPEN) {
            return AdmitOutcome.REJECTED_CLOSED;
        }
        if (participants.size() >= maxGuests + 1) {
            return AdmitOutcome.REJECTED_FULL;
        }
        TournamentParticipant p = new TournamentParticipant(id, nickname, joinCounter++);
        participants.put(id, p);
        if (seatAId != null && seatBId == null) {
            seatBId = id;
            p.setStatus(TournamentParticipant.Status.ACTIVE);
            seatAWins = 0;
            seatBWins = 0;
            matchGeneration++;
            return AdmitOutcome.SEATED_AS_CHALLENGER;
        }
        p.setStatus(TournamentParticipant.Status.QUEUED);
        queue.addLast(id);
        return AdmitOutcome.QUEUED;
    }

    public int totalParticipantCount() {
        return participants.size();
    }

    public int maxGuests() {
        return maxGuests;
    }

    public boolean isKnownParticipant(String id) {
        return participants.containsKey(id);
    }

    // --------------------------------------------------------- table state

    public TableState tableState() {
        return tableState;
    }

    /** OPEN -&gt; CLOSED only. Returns false (no-op) if already CLOSED or FINISHED. Never disconnects anyone. */
    public boolean closeEnrollment() {
        if (tableState != TableState.OPEN) {
            return false;
        }
        tableState = TableState.CLOSED;
        return true;
    }

    public boolean isFinished() {
        return tableState == TableState.FINISHED;
    }

    public String championId() {
        return championId;
    }

    public String finishReason() {
        return finishReason;
    }

    // ---------------------------------------------------------- game state

    public String seatAId() {
        return seatAId;
    }

    public String seatBId() {
        return seatBId;
    }

    public char seatASymbol() {
        return seatASymbol;
    }

    public char seatBSymbol() {
        return seatBSymbol;
    }

    public int seatAWins() {
        return seatAWins;
    }

    public int seatBWins() {
        return seatBWins;
    }

    public int winsToAdvance() {
        return winsToAdvance;
    }

    public int matchGeneration() {
        return matchGeneration;
    }

    public boolean isHostActive() {
        return HOST_ID.equals(seatAId) || HOST_ID.equals(seatBId);
    }

    public char symbolFor(String id) {
        if (id == null) return ' ';
        if (id.equals(seatAId)) return seatASymbol;
        if (id.equals(seatBId)) return seatBSymbol;
        return ' ';
    }

    public String nicknameOf(String id) {
        if (id == null) return "";
        TournamentParticipant p = participants.get(id);
        return p == null ? "" : p.nickname();
    }

    public TournamentParticipant participant(String id) {
        return participants.get(id);
    }

    public int queueLength() {
        return queue.size();
    }

    /** 1-based position in the FIFO queue, or 0 if not queued. */
    public int queuePositionOf(String id) {
        int pos = 1;
        for (String q : queue) {
            if (q.equals(id)) return pos;
            pos++;
        }
        return 0;
    }

    public List<String> queueSnapshot() {
        return new ArrayList<>(queue);
    }

    /** Sorted by series wins desc, then join order asc (stable). Never null. */
    public List<TournamentParticipant> leaderboardSnapshot() {
        List<TournamentParticipant> list = new ArrayList<>(participants.values());
        Collections.sort(list, (a, b) -> {
            if (b.seriesWins() != a.seriesWins()) return b.seriesWins() - a.seriesWins();
            return a.joinOrder() - b.joinOrder();
        });
        return list;
    }

    // -------------------------------------------------------------- series

    /**
     * Records the result of one completed match within the current series.
     * {@code winnerSymbol} is {@code ' '} for a draw (no score change).
     */
    public MatchOutcome recordGameResult(char winnerSymbol) {
        if (winnerSymbol == ' ') {
            return new MatchOutcome(false);
        }
        if (winnerSymbol == seatASymbol) {
            seatAWins++;
        } else if (winnerSymbol == seatBSymbol) {
            seatBWins++;
        }
        boolean decided = seatAWins >= winsToAdvance || seatBWins >= winsToAdvance;
        return new MatchOutcome(decided);
    }

    /** Continues an undecided series: same two seats, symbols swapped for the next match. */
    public void continueSeriesWithSwappedSymbols() {
        char oldA = seatASymbol;
        seatASymbol = seatBSymbol;
        seatBSymbol = oldA;
        matchGeneration++;
    }

    /** Call once {@link #recordGameResult(char)} reports the series decided. Rotates seats and checks for a champion. */
    public RotationOutcome concludeSeriesAndRotate() {
        boolean aWon = seatAWins >= winsToAdvance;
        String winnerId = aWon ? seatAId : seatBId;
        String loserId = aWon ? seatBId : seatAId;
        return eliminateAndRotate(loserId, winnerId, "Serie conclusa: " + winsToAdvance + " vittorie raggiunte");
    }

    /** Treats an active-seat disconnect as a forfeit: the remaining seat occupant (if any) wins by default. */
    public RotationOutcome forfeitActiveParticipant(String disconnectedId) {
        String remaining = disconnectedId.equals(seatAId) ? seatBId : seatAId;
        return eliminateAndRotate(disconnectedId, remaining, "Disconnessione durante la partita (forfait)");
    }

    private RotationOutcome eliminateAndRotate(String loserId, String winnerId, String reason) {
        if (loserId != null) {
            TournamentParticipant loser = participants.get(loserId);
            if (loser != null) {
                loser.recordElimination();
            }
        }
        if (winnerId != null) {
            TournamentParticipant winner = participants.get(winnerId);
            if (winner != null) {
                winner.recordSeriesWin();
                winner.setStatus(TournamentParticipant.Status.ACTIVE);
            }
        }
        seatAWins = 0;
        seatBWins = 0;

        if (winnerId == null) {
            // Nobody remains at this seat pair at all.
            seatAId = null;
            seatBId = null;
            matchGeneration++;
            boolean champion = maybeDeclareChampion();
            return new RotationOutcome(loserId, null, null, !champion, champion, championId, champion ? finishReason : null);
        }

        if (queue.isEmpty()) {
            seatAId = winnerId;
            seatBId = null;
            matchGeneration++;
            boolean champion = maybeDeclareChampion();
            return new RotationOutcome(loserId, winnerId, null, !champion, champion, championId, champion ? finishReason : null);
        }

        String nextId = queue.removeFirst();
        seatAId = winnerId;
        seatBId = nextId;
        seatASymbol = 'X';
        seatBSymbol = 'O';
        TournamentParticipant next = participants.get(nextId);
        if (next != null) {
            next.setStatus(TournamentParticipant.Status.ACTIVE);
        }
        matchGeneration++;
        return new RotationOutcome(loserId, winnerId, nextId, false, false, null, null);
    }

    // -------------------------------------------------------- disconnection

    /**
     * Handles a participant leaving the table (Nearby disconnect). Queued
     * participants are removed without disturbing the order of the rest.
     * An active-seat disconnect is treated as a forfeit (see
     * {@link #forfeitActiveParticipant(String)}). Unknown ids are a no-op.
     */
    public DisconnectOutcome disconnect(String id) {
        if (id == null || !participants.containsKey(id)) {
            return DisconnectOutcome.unknown();
        }
        boolean wasQueued = queue.remove(id);
        boolean wasActiveSeat = id.equals(seatAId) || id.equals(seatBId);
        if (!wasActiveSeat) {
            return DisconnectOutcome.queueOnly(wasQueued);
        }
        RotationOutcome rotation = forfeitActiveParticipant(id);
        return DisconnectOutcome.activeSeat(wasQueued, rotation);
    }

    // ------------------------------------------------------------- private

    /**
     * Single point of truth for champion proclamation (requirement: exactly
     * one host-side method decides this). Only ever declares FINISHED when
     * enrollment is already CLOSED and at most one non-eliminated participant
     * remains.
     */
    private boolean maybeDeclareChampion() {
        if (tableState == TableState.FINISHED) {
            return true;
        }
        if (tableState != TableState.CLOSED) {
            return false;
        }
        int remaining = 0;
        String onlyRemainingId = null;
        for (TournamentParticipant p : participants.values()) {
            if (!p.isEliminated()) {
                remaining++;
                onlyRemainingId = p.id();
            }
        }
        if (remaining == 1) {
            tableState = TableState.FINISHED;
            championId = onlyRemainingId;
            TournamentParticipant champ = participants.get(onlyRemainingId);
            if (champ != null) {
                champ.setStatus(TournamentParticipant.Status.CHAMPION);
            }
            finishReason = "Ultimo partecipante rimasto dopo la chiusura delle iscrizioni";
            matchGeneration++;
            return true;
        }
        if (remaining == 0) {
            tableState = TableState.FINISHED;
            championId = null;
            finishReason = "Tutti i partecipanti hanno abbandonato dopo la chiusura delle iscrizioni";
            matchGeneration++;
            return true;
        }
        return false;
    }
}
