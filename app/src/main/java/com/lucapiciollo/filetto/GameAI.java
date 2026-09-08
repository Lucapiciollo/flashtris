package com.lucapiciollo.filetto;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Minimax + alpha-beta tic-tac-toe engine used for the single-player "vs CPU" mode. The CPU
 * occasionally plays a random legal move instead of the perfect one, so a human can actually win
 * sometimes - a flawless, unbeatable CPU made the "vittorie" stat impossible to ever increase,
 * which felt broken in a casual pastime game.
 *
 * <p>Difficulty ramps up with the player's own track record: it starts fairly forgiving and gets
 * tougher (down to unbeatable) as the player accumulates more CPU wins, based on the win count
 * already persisted in {@link GameStats}. No separate skill-tracking system needed.
 */
final class GameAI {

    /** Mistake chance used for a brand-new player (0 CPU wins so far): fairly forgiving. */
    private static final double START_MISTAKE_CHANCE = 0.35;

    /** Mistake chance once the player reaches {@link #WINS_FOR_MAX_DIFFICULTY} wins: perfect play, unbeatable. */
    private static final double MIN_MISTAKE_CHANCE = 0.0;

    /** Number of CPU wins after which the CPU plays at full (perfect) difficulty. */
    private static final int WINS_FOR_MAX_DIFFICULTY = 10;

    private static final Random RANDOM = new Random();

    private static final int[][] WIN_LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}
    };

    private GameAI() {
    }

    /** Returns the CPU's current mistake probability given how many CPU matches the player has already won. */
    static double mistakeChanceForWins(int winsVsCpu) {
        double progress = Math.min(1.0, Math.max(0.0, winsVsCpu / (double) WINS_FOR_MAX_DIFFICULTY));
        return START_MISTAKE_CHANCE - progress * (START_MISTAKE_CHANCE - MIN_MISTAKE_CHANCE);
    }

    /** Returns the best cell (0-8) for {@code aiSymbol} to play, or -1 if the board is full. Difficulty scales with {@code winsVsCpu}. */
    static int bestMove(char[] board, char aiSymbol, char humanSymbol, int winsVsCpu) {
        List<Integer> emptyCells = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (board[i] == ' ') emptyCells.add(i);
        if (emptyCells.isEmpty()) return -1;

        if (RANDOM.nextDouble() < mistakeChanceForWins(winsVsCpu)) {
            return emptyCells.get(RANDOM.nextInt(emptyCells.size()));
        }

        int bestScore = Integer.MIN_VALUE;
        int bestCell = -1;
        for (int i : emptyCells) {
            board[i] = aiSymbol;
            int score = minimax(board, 0, false, aiSymbol, humanSymbol, Integer.MIN_VALUE, Integer.MAX_VALUE);
            board[i] = ' ';
            if (score > bestScore) {
                bestScore = score;
                bestCell = i;
            }
        }
        return bestCell;
    }

    private static int minimax(char[] board, int depth, boolean maximizing, char aiSymbol, char humanSymbol, int alpha, int beta) {
        char winner = winner(board);
        if (winner == aiSymbol) return 10 - depth;
        if (winner == humanSymbol) return depth - 10;
        if (isFull(board)) return 0;

        if (maximizing) {
            int best = Integer.MIN_VALUE;
            for (int i = 0; i < 9; i++) {
                if (board[i] != ' ') continue;
                board[i] = aiSymbol;
                best = Math.max(best, minimax(board, depth + 1, false, aiSymbol, humanSymbol, alpha, beta));
                board[i] = ' ';
                alpha = Math.max(alpha, best);
                if (beta <= alpha) break;
            }
            return best;
        } else {
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < 9; i++) {
                if (board[i] != ' ') continue;
                board[i] = humanSymbol;
                best = Math.min(best, minimax(board, depth + 1, true, aiSymbol, humanSymbol, alpha, beta));
                board[i] = ' ';
                beta = Math.min(beta, best);
                if (beta <= alpha) break;
            }
            return best;
        }
    }

    private static char winner(char[] board) {
        for (int[] l : WIN_LINES) {
            if (board[l[0]] != ' ' && board[l[0]] == board[l[1]] && board[l[1]] == board[l[2]]) return board[l[0]];
        }
        return ' ';
    }

    private static boolean isFull(char[] board) {
        for (char c : board) if (c == ' ') return false;
        return true;
    }
}
