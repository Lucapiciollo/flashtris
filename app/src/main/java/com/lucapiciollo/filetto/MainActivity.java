package com.lucapiciollo.filetto;

import android.Manifest;
import android.animation.Animator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Single-activity FlashTris: Nearby Connections P2P tic-tac-toe with a
 * fully programmatic "gamer neon" UI (see {@link GameTheme}, {@link GameSounds},
 * {@link GameAnimations}). All multiplayer logic/protocol is unchanged from
 * the original implementation; only the screen-building/UI layer was redesigned.
 */
public class MainActivity extends Activity {

    private static final String TAG = "FlashTris";
    private static final String SERVICE_ID = "com.lucapiciollo.filetto.nearby";
    private static final Strategy STRATEGY = Strategy.P2P_POINT_TO_POINT;
    private static final int REQ_PERMISSIONS = 900;

    private final Random random = new Random();
    private ConnectionsClient connectionsClient;
    private GameSounds sounds;
    private GameStats stats;
    private String endpointId;
    private boolean host;
    private boolean vsCpu;
    private boolean connected;
    private boolean nicknameSent;
    private String myNickname = "";
    private String opponentNickname = "";
    private String hostCode = "";
    private char mySymbol = ' ';
    private char opponentSymbol = ' ';
    private char turn = 'X';
    private boolean gameOver;
    private boolean awaitingMoveResult;
    private int lastMoveCell = -1;
    private long matchStartMs;
    private int turnsPlayed;
    private final char[] board = new char[9];

    private LinearLayout root;
    private TextView statusText;
    private TextView titleText;
    private final List<Button> cellButtons = new ArrayList<>();
    private Animator activeAmbientAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        connectionsClient = Nearby.getConnectionsClient(this);
        sounds = new GameSounds(this);
        stats = new GameStats(this);
        showHome();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        stopEverything();
        GameAnimations.stop(activeAmbientAnimator);
        if (sounds != null) sounds.release();
    }

    private void showHome() {
        stopAmbientAnimator();
        stopEverything();
        resetSession();
        root = baseRoot();

        root.addView(neonDivider());
        titleText = title("FLASHTRIS");
        titleText.setTextSize(40);
        root.addView(titleText);
        TextView payoff = subtitle("GIOCA  •  SFIDA  •  CONNETTITI");
        payoff.setTextColor(GameTheme.CYAN);
        payoff.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        payoff.setTextSize(13);
        root.addView(payoff);
        root.addView(neonDivider());
        root.addView(space(10));

        root.addView(miniGridPreview());
        root.addView(space(22));

        Button create = primaryButton("CREA PARTITA");
        create.setOnClickListener(v -> {
            sounds.tap();
            host = true;
            if (ensurePermissions()) startAdvertising();
        });
        root.addView(create);
        root.addView(space(12));

        Button find = secondaryButton("TROVA PARTITA");
        find.setOnClickListener(v -> {
            sounds.tap();
            host = false;
            if (ensurePermissions()) startDiscovery();
        });
        root.addView(find);
        root.addView(space(12));

        Button cpuButton = secondaryButton("🤖 SFIDA LA CPU");
        cpuButton.setOnClickListener(v -> {
            sounds.tap();
            startVsCpu();
        });
        root.addView(cpuButton);
        root.addView(space(20));

        LinearLayout menuRow = new LinearLayout(this);
        menuRow.setOrientation(LinearLayout.HORIZONTAL);
        menuRow.setGravity(Gravity.CENTER_VERTICAL);
        menuRow.addView(iconMenuTile("🏆", "CLASSIFICA", this::showLeaderboardScreen), weighted());
        menuRow.addView(spaceHorizontal(8));
        menuRow.addView(iconMenuTile("👤", "PROFILO", this::showProfileScreen), weighted());
        menuRow.addView(spaceHorizontal(8));
        menuRow.addView(iconMenuTile("⚙", "IMPOSTAZIONI", this::showSettingsScreen), weighted());
        menuRow.addView(spaceHorizontal(8));
        menuRow.addView(audioMenuTile(), weighted());
        root.addView(menuRow, matchWrap(0));

        root.addView(space(16));
        root.addView(caption("Connessione locale via Nearby Connections. Nessun backend, nessuna registrazione."));
        renderScreen(root);
    }

    /** Square icon tile with a caption underneath, used for the Home bottom menu (Classifica/Profilo/Impostazioni). */
    private View iconMenuTile(String glyph, String label, Runnable onClick) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        int padH = dp(4);
        int padV = dp(10);
        tile.setPadding(padH, padV, padH, padV);
        tile.setBackground(GameTheme.withRipple(GameTheme.roundedStroke(GameTheme.BG_PANEL, GameTheme.BG_PANEL_LIGHT, dp(14), dp(2)), GameTheme.CYAN));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setMinimumHeight(dp(76));
        tile.setElevation(dp(3));
        GameAnimations.pressFeedback(tile);
        TextView glyphView = new TextView(this);
        glyphView.setText(glyph);
        glyphView.setTextSize(22);
        glyphView.setGravity(Gravity.CENTER);
        tile.addView(glyphView);
        TextView labelView = caption(label);
        labelView.setTextSize(9);
        labelView.setSingleLine(true);
        labelView.setPadding(0, dp(4), 0, 0);
        tile.addView(labelView);
        tile.setOnClickListener(v -> {
            sounds.tap();
            onClick.run();
        });
        return tile;
    }

    /** Same square tile style as {@link #iconMenuTile}, but toggles sound on/off in place (small status dot). */
    private View audioMenuTile() {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        int padH = dp(4);
        int padV = dp(10);
        tile.setPadding(padH, padV, padH, padV);
        tile.setBackground(GameTheme.withRipple(GameTheme.roundedStroke(GameTheme.BG_PANEL, GameTheme.BG_PANEL_LIGHT, dp(14), dp(2)), GameTheme.CYAN));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setMinimumHeight(dp(76));
        tile.setElevation(dp(3));
        GameAnimations.pressFeedback(tile);

        FrameLayout glyphWrap = new FrameLayout(this);
        TextView glyphView = new TextView(this);
        glyphView.setText("🔊");
        glyphView.setTextSize(22);
        glyphView.setGravity(Gravity.CENTER);
        glyphWrap.addView(glyphView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        View dot = new View(this);
        int dotSize = dp(9);
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dotSize, dotSize, Gravity.TOP | Gravity.END);
        glyphWrap.addView(dot, dotLp);
        tile.addView(glyphWrap);
        TextView labelView = caption("AUDIO");
        labelView.setTextSize(9);
        labelView.setSingleLine(true);
        labelView.setPadding(0, dp(4), 0, 0);
        tile.addView(labelView);

        Runnable refresh = () -> dot.setBackground(GameTheme.ovalFill(sounds.isSoundEnabled() ? GameTheme.LIME : GameTheme.TEXT_MUTED));
        refresh.run();
        tile.setOnClickListener(v -> {
            boolean enable = !sounds.isSoundEnabled();
            sounds.setSoundEnabled(enable);
            if (enable) sounds.tap();
            refresh.run();
        });
        return tile;
    }

    /** Small static, non-interactive tic-tac-toe grid used as a decorative preview on the Home screen. */
    private View miniGridPreview() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(GameTheme.glowPanel(GameTheme.BG_PANEL, GameTheme.VIOLET, dp(18), dp(2), dp(6)));
        card.setElevation(dp(4));
        int pad = dp(14);
        card.setPadding(pad, pad, pad, pad);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(3);
        char[] preview = {'X', 'O', 'X', 'O', 'X', 'O', 'X', 'O', 'X'};
        for (int i = 0; i < 9; i++) {
            TextView cell = new TextView(this);
            cell.setText(String.valueOf(preview[i]));
            cell.setGravity(Gravity.CENTER);
            cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cell.setTextSize(20);
            cell.setTextColor(preview[i] == 'X' ? GameTheme.SYMBOL_X : GameTheme.SYMBOL_O);
            cell.setBackground(GameTheme.insetFill(GameTheme.BG_CELL, dp(8)));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(46);
            lp.height = dp(46);
            lp.columnSpec = GridLayout.spec(i % 3, 1f);
            lp.rowSpec = GridLayout.spec(i / 3, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(cell, lp);
        }
        card.addView(grid, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return wrapper;
    }

    private Button soundToggleButton() {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(38));
        b.setPadding(dp(18), 0, dp(18), 0);
        b.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        GameAnimations.pressFeedback(b);
        updateSoundToggleLabel(b);
        b.setOnClickListener(v -> {
            boolean enable = !sounds.isSoundEnabled();
            sounds.setSoundEnabled(enable);
            if (enable) sounds.tap();
            updateSoundToggleLabel(b);
        });
        return b;
    }

    private void updateSoundToggleLabel(Button b) {
        boolean on = sounds.isSoundEnabled();
        b.setText(on ? "🔊 ON" : "🔇 OFF");
        b.setTextColor(on ? GameTheme.BG_NIGHT : GameTheme.TEXT_SECONDARY);
        b.setBackground(GameTheme.withRipple(
                on ? GameTheme.roundedFill(GameTheme.LIME, dp(19)) : GameTheme.roundedStroke(GameTheme.BG_PANEL_LIGHT, GameTheme.TEXT_MUTED, dp(19), dp(2)),
                on ? GameTheme.BG_NIGHT : GameTheme.CYAN));
    }

    private View neonDivider() {
        View v = new View(this);
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.TRANSPARENT, GameTheme.CYAN, GameTheme.MAGENTA, Color.TRANSPARENT});
        v.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2));
        lp.setMargins(0, dp(10), 0, dp(10));
        v.setLayoutParams(lp);
        return v;
    }

    private void startAdvertising() {
        hostCode = String.format(Locale.ITALY, "%04d", random.nextInt(10000));
        showWaiting("Partita creata", "Sto aspettando un amico vicino…");
        String endpointName = "FlashTris-" + hostCode;
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        try {
            Log.i(TAG, "startAdvertising as " + endpointName);
            connectionsClient.startAdvertising(endpointName, SERVICE_ID, lifecycleCallback, options)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "startAdvertising failed", e);
                        fail("Impossibile creare la partita: " + e.getMessage());
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "startAdvertising missing permission", e);
            fail("Permessi mancanti per creare la partita.");
        }
    }

    private void startDiscovery() {
        showWaiting("Cerco partite", "Tieni aperta l'app dell'amico che ha creato la partita.");
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        try {
            Log.i(TAG, "startDiscovery");
            connectionsClient.startDiscovery(SERVICE_ID, discoveryCallback, options)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "startDiscovery failed", e);
                        fail("Ricerca non disponibile: " + e.getMessage());
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "startDiscovery missing permission", e);
            fail("Permessi mancanti per cercare partite.");
        }
    }

    private final EndpointDiscoveryCallback discoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(String id, DiscoveredEndpointInfo info) {
            if (connected || endpointId != null) return;
            endpointId = id;
            Log.i(TAG, "onEndpointFound " + id + " (" + info.getEndpointName() + ")");
            if (statusText != null) statusText.setText("Partita trovata: " + info.getEndpointName() + "\nConnessione…");
            try {
                connectionsClient.stopDiscovery();
                connectionsClient.requestConnection("Giocatore", id, lifecycleCallback)
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "requestConnection failed", e);
                            endpointId = null;
                            fail("Connessione fallita: " + e.getMessage());
                        });
            } catch (SecurityException e) {
                Log.e(TAG, "requestConnection missing permission", e);
                endpointId = null;
                fail("Permessi mancanti per connettersi.");
            }
        }

        @Override
        public void onEndpointLost(String id) {
            Log.w(TAG, "onEndpointLost " + id);
            if (id.equals(endpointId) && !connected) endpointId = null;
        }
    };

    private final ConnectionLifecycleCallback lifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String id, ConnectionInfo info) {
            Log.i(TAG, "onConnectionInitiated " + id);
            endpointId = id;
            try {
                connectionsClient.acceptConnection(id, payloadCallback);
            } catch (SecurityException e) {
                Log.e(TAG, "acceptConnection missing permission", e);
                runOnUiThread(() -> fail("Permessi mancanti per accettare la connessione."));
            }
        }

        @Override
        public void onConnectionResult(String id, ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                Log.i(TAG, "onConnectionResult success " + id);
                connected = true;
                endpointId = id;
                stopDiscoverySafely();
                stopAdvertisingSafely();
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        sounds.connected();
                        showNicknameScreen();
                    }
                });
            } else {
                Log.w(TAG, "onConnectionResult failed " + id + " status=" + result.getStatus());
                endpointId = null;
                connected = false;
                runOnUiThread(() -> fail("Connessione rifiutata o non riuscita."));
            }
        }

        @Override
        public void onDisconnected(String id) {
            Log.w(TAG, "onDisconnected " + id);
            connected = false;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                styledDialog("AMICO DISCONNESSO", "La partita è terminata.", false,
                        "TORNA ALLA HOME", (d, w) -> showHome(), null, null).show();
            });
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String id, Payload payload) {
            if (payload.getType() != Payload.Type.BYTES || payload.asBytes() == null) return;
            String raw = new String(payload.asBytes(), StandardCharsets.UTF_8);
            Log.d(TAG, "onPayloadReceived " + raw);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) handleMessage(raw);
            });
        }

        @Override
        public void onPayloadTransferUpdate(String id, PayloadTransferUpdate update) { }
    };

    private void stopDiscoverySafely() {
        try { connectionsClient.stopDiscovery(); } catch (Exception e) { Log.w(TAG, "stopDiscovery failed", e); }
    }

    private void stopAdvertisingSafely() {
        try { connectionsClient.stopAdvertising(); } catch (Exception e) { Log.w(TAG, "stopAdvertising failed", e); }
    }

    private void showNicknameScreen() {
        stopAmbientAnimator();
        root = baseRoot();
        root.addView(title("SEI DENTRO ✓"));
        root.addView(subtitle("Scegli il nome da mostrare solo per questa partita."));
        root.addView(space(22));

        EditText input = new EditText(this);
        input.setHint("Il tuo nickname");
        input.setHintTextColor(GameTheme.TEXT_MUTED);
        input.setTextColor(GameTheme.TEXT_PRIMARY);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setTextSize(18);
        input.setPadding(dp(18), dp(14), dp(18), dp(14));
        input.setBackground(GameTheme.roundedStroke(GameTheme.BG_PANEL, GameTheme.CYAN, dp(14), dp(2)));
        root.addView(input, matchWrap(0));

        TextView errorText = caption("");
        errorText.setTextColor(GameTheme.DANGER);
        errorText.setVisibility(View.GONE);
        root.addView(errorText, matchWrap(dp(4)));

        root.addView(caption("Vale solo per questa partita: non viene salvato né condiviso altrove."));
        root.addView(space(10));

        Button continueBtn = primaryButton("CONTINUA");
        continueBtn.setOnClickListener(v -> {
            sounds.tap();
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                errorText.setText("Inserisci un nickname per continuare");
                errorText.setVisibility(View.VISIBLE);
                return;
            }
            errorText.setVisibility(View.GONE);
            myNickname = name.length() > 18 ? name.substring(0, 18) : name;
            nicknameSent = true;
            JSONObject nicknameMsg = message("NICKNAME");
            try { nicknameMsg.put("name", myNickname); } catch (JSONException ignored) { }
            Log.i(TAG, "Sending nickname");
            send(nicknameMsg);
            showWaiting("Perfetto, " + myNickname, "Aspetto il nickname del tuo amico…");
            maybeProceedAfterNicknames();
        });
        root.addView(continueBtn);
        renderScreen(root);
    }

    private void maybeProceedAfterNicknames() {
        if (!nicknameSent || opponentNickname.isEmpty()) return;
        if (host) showSymbolChoice();
        else showWaiting("Ci siamo", opponentNickname + " sta scegliendo X oppure O…");
    }

    private void showSymbolChoice() {
        stopAmbientAnimator();
        root = baseRoot();
        root.addView(title("SCEGLI IL SIMBOLO"));
        root.addView(subtitle("Tu scegli. " + opponentNickname + " riceverà automaticamente l'altro simbolo."));
        root.addView(space(24));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        View x = symbolCard('X', GameTheme.SYMBOL_X);
        View o = symbolCard('O', GameTheme.SYMBOL_O);
        row.addView(x, weighted());
        row.addView(spaceHorizontal(14));
        row.addView(o, weighted());
        root.addView(row, matchWrap(0));

        x.setOnClickListener(v -> {
            sounds.tap();
            GameAnimations.celebrate(v);
            v.postDelayed(() -> selectHostSymbol('X'), 180);
        });
        o.setOnClickListener(v -> {
            sounds.tap();
            GameAnimations.celebrate(v);
            v.postDelayed(() -> selectHostSymbol('O'), 180);
        });
        renderScreen(root);
    }

    /** Big glowing selectable card used on the symbol-choice screen. */
    private View symbolCard(char symbol, int accentColor) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(GameTheme.glowPanel(GameTheme.BG_PANEL, accentColor, dp(20), dp(3), dp(8)));
        card.setElevation(dp(4));
        card.setClickable(true);
        card.setFocusable(true);
        GameAnimations.pressFeedback(card);
        TextView letter = new TextView(this);
        letter.setText(String.valueOf(symbol));
        letter.setTextSize(56);
        letter.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        letter.setTextColor(accentColor);
        letter.setGravity(Gravity.CENTER);
        card.addView(letter, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        card.setLayoutParams(new LinearLayout.LayoutParams(0, dp(140), 1f));
        return card;
    }

    /** Starts a local single-player match against the {@link GameAI} opponent (no Nearby connection involved). */
    private void startVsCpu() {
        vsCpu = true;
        host = true;
        myNickname = stats.getNickname("Tu");
        opponentNickname = "CPU";
        showSymbolChoice();
    }

    private void selectHostSymbol(char symbol) {
        mySymbol = symbol;
        opponentSymbol = symbol == 'X' ? 'O' : 'X';
        turn = 'X';
        gameOver = false;
        lastMoveCell = -1;
        matchStartMs = System.currentTimeMillis();
        turnsPlayed = 0;
        clearBoard();
        JSONObject msg = message("START");
        try {
            msg.put("guestSymbol", String.valueOf(opponentSymbol));
            msg.put("hostName", myNickname);
            msg.put("guestName", opponentNickname);
        } catch (JSONException ignored) { }
        send(msg);
        showGame();
        maybeTriggerCpuMove();
    }

    private void showGame() {
        stopAmbientAnimator();
        root = baseRoot();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(playerChip(myNickname, mySymbol, true, stats.getAvatar()), weighted());
        Button audioToggle = soundToggleButton();
        header.addView(audioToggle);
        header.addView(playerChip(opponentNickname, opponentSymbol, false, vsCpu ? "🤖" : "🎮"), weighted());
        root.addView(header, matchWrap(0));
        root.addView(space(14));

        statusText = new TextView(this);
        statusText.setTextSize(17);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);
        statusText.setTextColor(GameTheme.TEXT_PRIMARY);
        statusText.setBackground(GameTheme.roundedStroke(GameTheme.BG_PANEL, GameTheme.BG_PANEL_LIGHT, dp(20), dp(2)));
        statusText.setPadding(dp(20), dp(10), dp(20), dp(10));
        root.addView(statusText, matchWrap(dp(6)));
        root.addView(space(16));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(3);
        grid.setUseDefaultMargins(true);
        cellButtons.clear();
        for (int i = 0; i < 9; i++) {
            final int cell = i;
            Button b = new Button(this);
            b.setTextSize(38);
            b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            b.setAllCaps(false);
            b.setMinHeight(dp(94));
            b.setBackground(GameTheme.withRipple(GameTheme.insetStroke(GameTheme.BG_CELL, GameTheme.BG_PANEL_LIGHT, dp(12), dp(2)), GameTheme.CYAN));
            GameAnimations.pressFeedback(b);
            b.setOnClickListener(v -> onCellPressed(cell));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(100);
            lp.columnSpec = GridLayout.spec(i % 3, 1f);
            lp.rowSpec = GridLayout.spec(i / 3, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(b, lp);
            cellButtons.add(b);
        }
        root.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(330)));
        root.addView(space(18));

        Button leave = dangerButton("ESCI DALLA PARTITA");
        leave.setOnClickListener(v -> {
            sounds.tap();
            send(message("LEAVE"));
            showHome();
        });
        root.addView(leave);
        renderScreen(root);
        renderBoard();
    }

    /** Compact avatar + nickname + symbol chip shown in the gameplay header. */
    private View playerChip(String nickname, char symbol, boolean mine, String avatar) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        int accent = symbol == 'X' ? GameTheme.SYMBOL_X : GameTheme.SYMBOL_O;
        TextView avatarView = new TextView(this);
        avatarView.setText(avatar);
        avatarView.setTextSize(22);
        avatarView.setGravity(Gravity.CENTER);
        avatarView.setBackground(GameTheme.roundedStroke(GameTheme.BG_PANEL, accent, dp(20), dp(2)));
        chip.addView(avatarView, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView name = new TextView(this);
        name.setText(nickname.isEmpty() ? (mine ? "Tu" : "Avversario") : nickname);
        name.setTextSize(13);
        name.setTextColor(GameTheme.TEXT_SECONDARY);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setPadding(0, dp(4), 0, 0);
        chip.addView(name);
        TextView symbolView = new TextView(this);
        symbolView.setText(String.valueOf(symbol));
        symbolView.setTextSize(26);
        symbolView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        symbolView.setTextColor(accent);
        symbolView.setGravity(Gravity.CENTER);
        chip.addView(symbolView);
        return chip;
    }

    private void onCellPressed(int cell) {
        if (gameOver || board[cell] != ' ' || turn != mySymbol || awaitingMoveResult) return;
        if (mySymbol == 'X') sounds.moveX(); else sounds.moveO();
        if (host) {
            applyMove(cell, mySymbol);
        } else {
            awaitingMoveResult = true;
            renderBoard();
            JSONObject msg = message("MOVE_REQUEST");
            try { msg.put("cell", cell); } catch (JSONException ignored) { }
            Log.d(TAG, "Sending MOVE_REQUEST cell=" + cell);
            send(msg);
        }
    }

    private void applyMove(int cell, char symbol) {
        if (!host || gameOver || cell < 0 || cell > 8 || board[cell] != ' ' || turn != symbol) {
            Log.w(TAG, "applyMove rejected cell=" + cell + " symbol=" + symbol);
            return;
        }
        board[cell] = symbol;
        lastMoveCell = cell;
        turnsPlayed++;
        char winner = winner();
        if (winner != ' ') gameOver = true;
        else if (isDraw()) gameOver = true;
        else turn = turn == 'X' ? 'O' : 'X';
        Log.d(TAG, "applyMove cell=" + cell + " symbol=" + symbol + " gameOver=" + gameOver);
        broadcastState();
        renderBoard();
        if (gameOver) showEndDialogAfterDelay();
        else maybeTriggerCpuMove();
    }

    /** If it's the CPU opponent's turn in vs-CPU mode, computes and plays its move after a short "thinking" delay. */
    private void maybeTriggerCpuMove() {
        if (!vsCpu || gameOver || turn != opponentSymbol || root == null) return;
        root.postDelayed(() -> {
            if (isFinishing() || isDestroyed() || !vsCpu || gameOver || turn != opponentSymbol) return;
            int cell = GameAI.bestMove(board, opponentSymbol, mySymbol);
            if (cell < 0) return;
            if (opponentSymbol == 'X') sounds.moveX(); else sounds.moveO();
            applyMove(cell, opponentSymbol);
        }, 550);
    }

    private void broadcastState() {
        JSONObject msg = message("STATE");
        try {
            JSONArray arr = new JSONArray();
            for (char c : board) arr.put(c == ' ' ? "" : String.valueOf(c));
            msg.put("board", arr);
            msg.put("turn", String.valueOf(turn));
            msg.put("gameOver", gameOver);
            msg.put("winner", winner() == ' ' ? "" : String.valueOf(winner()));
        } catch (JSONException ignored) { }
        send(msg);
    }

    private void handleMessage(String raw) {
        try {
            JSONObject msg = new JSONObject(raw);
            String type = msg.optString("type");
            Log.d(TAG, "handleMessage type=" + type);
            switch (type) {
                case "NICKNAME":
                    opponentNickname = msg.optString("name", "Amico");
                    maybeProceedAfterNicknames();
                    break;
                case "START":
                    if (!host) {
                        mySymbol = msg.optString("guestSymbol", "O").charAt(0);
                        opponentSymbol = mySymbol == 'X' ? 'O' : 'X';
                        turn = 'X';
                        gameOver = false;
                        awaitingMoveResult = false;
                        lastMoveCell = -1;
                        matchStartMs = System.currentTimeMillis();
                        turnsPlayed = 0;
                        clearBoard();
                        showGame();
                    }
                    break;
                case "MOVE_REQUEST":
                    if (host) applyMove(msg.optInt("cell", -1), opponentSymbol);
                    break;
                case "STATE":
                    if (!host) {
                        JSONArray arr = msg.getJSONArray("board");
                        int changedCell = -1;
                        for (int i = 0; i < 9; i++) {
                            String v = arr.optString(i, "");
                            char newValue = v.isEmpty() ? ' ' : v.charAt(0);
                            if (newValue != board[i] && newValue != ' ') changedCell = i;
                            board[i] = newValue;
                        }
                        if (changedCell != -1) {
                            lastMoveCell = changedCell;
                            turnsPlayed++;
                            if (board[changedCell] == 'X') sounds.moveX(); else sounds.moveO();
                        }
                        String t = msg.optString("turn", "X");
                        turn = t.isEmpty() ? 'X' : t.charAt(0);
                        gameOver = msg.optBoolean("gameOver", false);
                        awaitingMoveResult = false;
                        renderBoard();
                        if (gameOver) showEndDialogAfterDelay();
                    }
                    break;
                case "REMATCH_REQUEST":
                    if (host && !isFinishing() && !isDestroyed()) {
                        styledDialog(opponentNickname.toUpperCase(Locale.ITALY) + " VUOLE LA RIVINCITA", "Accetti una nuova partita?", true,
                                "ACCETTA", (d, w) -> startRematch(), "NO", null).show();
                    }
                    break;
                case "REMATCH_START":
                    if (!host) {
                        char next = msg.optString("guestSymbol", "O").charAt(0);
                        mySymbol = next;
                        opponentSymbol = next == 'X' ? 'O' : 'X';
                        clearBoard();
                        turn = 'X';
                        gameOver = false;
                        awaitingMoveResult = false;
                        lastMoveCell = -1;
                        matchStartMs = System.currentTimeMillis();
                        turnsPlayed = 0;
                        showGame();
                    }
                    break;
                case "LEAVE":
                    if (isFinishing() || isDestroyed()) break;
                    styledDialog("PARTITA TERMINATA", opponentNickname + " è uscito dalla partita.", false,
                            "HOME", (d, w) -> showHome(), null, null).show();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "handleMessage failed to parse payload", e);
            Toast.makeText(this, "Messaggio non valido", Toast.LENGTH_SHORT).show();
        }
    }

    private void startRematch() {
        if (!host) return;
        char old = mySymbol;
        mySymbol = opponentSymbol;
        opponentSymbol = old;
        clearBoard();
        turn = 'X';
        gameOver = false;
        awaitingMoveResult = false;
        lastMoveCell = -1;
        matchStartMs = System.currentTimeMillis();
        turnsPlayed = 0;
        JSONObject msg = message("REMATCH_START");
        try { msg.put("guestSymbol", String.valueOf(opponentSymbol)); } catch (JSONException ignored) { }
        Log.i(TAG, "startRematch mySymbol=" + mySymbol);
        send(msg);
        showGame();
        maybeTriggerCpuMove();
    }

    private void showEndDialogAfterDelay() {
        if (root == null) return;
        root.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            showResultScreen();
        }, 250);
    }

    /** Full-screen result view shown at the end of a match, replacing the old AlertDialog. */
    private void showResultScreen() {
        stopAmbientAnimator();
        char w = winner();
        boolean iWon = w != ' ' && w == mySymbol;
        boolean draw = w == ' ';
        String headline = draw ? "PAREGGIO" : (iWon ? "HAI VINTO!" : "HAI PERSO");
        int accent = draw ? GameTheme.VIOLET : (iWon ? GameTheme.LIME : GameTheme.DANGER);
        if (draw) sounds.draw(); else if (iWon) sounds.win(); else sounds.lose();

        stats.recordResult(vsCpu, draw ? GameStats.Outcome.DRAW : (iWon ? GameStats.Outcome.WIN : GameStats.Outcome.LOSS));
        long durationSec = Math.max(0, (System.currentTimeMillis() - matchStartMs) / 1000);
        String durationLabel = String.format(Locale.ITALY, "%02d:%02d", durationSec / 60, durationSec % 60);
        int streak = stats.getCurrentStreak();

        root = baseRoot();
        TextView headlineView = title(headline);
        headlineView.setTextColor(accent);
        headlineView.setTextSize(38);
        root.addView(headlineView);
        root.addView(space(6));
        root.addView(caption(draw ? "Nessun vincitore questa volta." : (iWon ? "Ottima partita, " + myNickname + "!" : opponentNickname + " se l'è cavata meglio.")));
        root.addView(space(20));

        int[] winLine = winningLineCells();
        FrameLayout boardCard = new FrameLayout(this);
        boardCard.setBackground(GameTheme.glowPanel(GameTheme.BG_PANEL, accent, dp(18), dp(2), dp(6)));
        boardCard.setElevation(dp(4));
        int pad = dp(14);
        boardCard.setPadding(pad, pad, pad, pad);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(3);
        for (int i = 0; i < 9; i++) {
            boolean onLine = winLine != null && (i == winLine[0] || i == winLine[1] || i == winLine[2]);
            TextView cell = new TextView(this);
            char c = board[i];
            cell.setText(c == ' ' ? "" : String.valueOf(c));
            cell.setGravity(Gravity.CENTER);
            cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cell.setTextSize(24);
            cell.setTextColor(c == 'X' ? GameTheme.SYMBOL_X : GameTheme.SYMBOL_O);
            cell.setBackground(onLine
                    ? GameTheme.insetStroke(GameTheme.BG_CELL, GameTheme.LIME, dp(8), dp(2))
                    : GameTheme.insetFill(GameTheme.BG_CELL, dp(8)));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(58);
            lp.height = dp(58);
            lp.columnSpec = GridLayout.spec(i % 3, 1f);
            lp.rowSpec = GridLayout.spec(i / 3, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(cell, lp);
            if (onLine) GameAnimations.celebrate(cell);
        }
        boardCard.addView(grid, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        FrameLayout boardWrapper = new FrameLayout(this);
        boardWrapper.addView(boardCard, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(boardWrapper, matchWrap(0));
        root.addView(space(20));

        FrameLayout matchStatsCard = new FrameLayout(this);
        matchStatsCard.setBackground(GameTheme.roundedStroke(GameTheme.BG_PANEL, GameTheme.BG_PANEL_LIGHT, dp(16), dp(2)));
        LinearLayout matchStatsRow = new LinearLayout(this);
        matchStatsRow.setOrientation(LinearLayout.HORIZONTAL);
        int statsPad = dp(14);
        matchStatsRow.setPadding(statsPad, statsPad, statsPad, statsPad);
        matchStatsRow.addView(statBlock("DURATA", durationLabel), weighted());
        matchStatsRow.addView(statBlock("TURNI", String.valueOf(turnsPlayed)), weighted());
        matchStatsRow.addView(statBlock("SERIE VITTORIE", String.valueOf(streak)), weighted());
        matchStatsCard.addView(matchStatsRow);
        root.addView(matchStatsCard, matchWrap(0));
        root.addView(space(20));

        if (host) {
            Button rematch = primaryButton("RIVINCITA");
            rematch.setOnClickListener(v -> {
                sounds.tap();
                startRematch();
            });
            root.addView(rematch);
        } else {
            Button askRematch = primaryButton("CHIEDI RIVINCITA");
            askRematch.setOnClickListener(v -> {
                sounds.tap();
                send(message("REMATCH_REQUEST"));
                askRematch.setEnabled(false);
                askRematch.setText("RICHIESTA INVIATA…");
                Toast.makeText(this, "Richiesta inviata", Toast.LENGTH_SHORT).show();
            });
            root.addView(askRematch);
        }
        root.addView(space(12));
        Button newHome = secondaryButton("NUOVA HOME");
        newHome.setOnClickListener(v -> {
            sounds.tap();
            showHome();
        });
        root.addView(newHome);

        renderScreen(root);
        if (iWon) GameAnimations.celebrate(headlineView);
    }

    private static final int[][] WIN_LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}
    };

    private int[] winningLineCells() {
        for (int[] l : WIN_LINES) {
            if (board[l[0]] != ' ' && board[l[0]] == board[l[1]] && board[l[1]] == board[l[2]]) return l;
        }
        return null;
    }

    private void renderBoard() {
        if (cellButtons.size() != 9 || statusText == null) return;
        int[] winLine = gameOver ? winningLineCells() : null;
        for (int i = 0; i < 9; i++) {
            char c = board[i];
            Button b = cellButtons.get(i);
            b.setText(c == ' ' ? "" : String.valueOf(c));
            b.setTextColor(c == 'X' ? GameTheme.SYMBOL_X : (c == 'O' ? GameTheme.SYMBOL_O : GameTheme.TEXT_PRIMARY));
            b.setEnabled(!gameOver && c == ' ' && turn == mySymbol && !awaitingMoveResult);
            boolean onLine = winLine != null && (i == winLine[0] || i == winLine[1] || i == winLine[2]);
            int borderColor = onLine ? GameTheme.LIME : (i == lastMoveCell ? GameTheme.CYAN : GameTheme.BG_PANEL_LIGHT);
            b.setBackground(GameTheme.withRipple(GameTheme.insetStroke(GameTheme.BG_CELL, borderColor, dp(12), dp(2)), GameTheme.CYAN));
            if (i == lastMoveCell && c != ' ') GameAnimations.popIn(b);
        }
        if (gameOver) {
            char w = winner();
            if (w == ' ') statusText.setText("Pareggio");
            else statusText.setText(w == mySymbol ? "Hai vinto 🎉" : opponentNickname + " ha vinto");
            GameAnimations.stop(activeAmbientAnimator);
            activeAmbientAnimator = null;
            statusText.setAlpha(1f);
        } else if (turn == mySymbol) {
            statusText.setText("Tocca a te • " + mySymbol);
            statusText.setTextColor(GameTheme.LIME);
            GameAnimations.stop(activeAmbientAnimator);
            activeAmbientAnimator = GameAnimations.startAlphaPulse(statusText);
        } else {
            statusText.setText("Tocca a " + opponentNickname + " • " + opponentSymbol);
            statusText.setTextColor(GameTheme.TEXT_SECONDARY);
            GameAnimations.stop(activeAmbientAnimator);
            activeAmbientAnimator = null;
            statusText.setAlpha(1f);
        }
    }

    private char winner() {
        for (int[] l : WIN_LINES) {
            if (board[l[0]] != ' ' && board[l[0]] == board[l[1]] && board[l[1]] == board[l[2]]) return board[l[0]];
        }
        return ' ';
    }

    private boolean isDraw() {
        if (winner() != ' ') return false;
        for (char c : board) if (c == ' ') return false;
        return true;
    }

    private void showWaiting(String title, String status) {
        stopAmbientAnimator();
        root = baseRoot();
        root.addView(title(title.toUpperCase(Locale.ITALY)));
        statusText = subtitle(status);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);
        root.addView(space(20));
        if (host && !hostCode.isEmpty()) {
            root.addView(hostCodeCard());
            root.addView(space(14));
        }
        root.addView(radarView());
        root.addView(space(10));
        Button cancel = secondaryButton("ANNULLA");
        cancel.setOnClickListener(v -> {
            sounds.tap();
            showHome();
        });
        root.addView(cancel);
        renderScreen(root);
    }

    /** Card showing the host's match code (real, taken from the advertised endpoint name) with copy/share actions. */
    private View hostCodeCard() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(GameTheme.roundedStroke(GameTheme.BG_PANEL, GameTheme.BG_PANEL_LIGHT, dp(16), dp(2)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        int pad = dp(16);
        col.setPadding(pad, pad, pad, pad);
        col.addView(caption("CODICE PARTITA"));
        TextView codeView = new TextView(this);
        codeView.setText(hostCode);
        codeView.setTextSize(30);
        codeView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        codeView.setTextColor(GameTheme.CYAN);
        codeView.setGravity(Gravity.CENTER);
        codeView.setPadding(0, dp(4), 0, dp(10));
        col.addView(codeView);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        Button copy = secondaryButton("COPIA CODICE");
        copy.setTextSize(13);
        copy.setOnClickListener(v -> {
            sounds.tap();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Codice FlashTris", hostCode));
                Toast.makeText(this, "Codice copiato", Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(copy, weighted());
        row.addView(spaceHorizontal(10));
        Button share = secondaryButton("CONDIVIDI");
        share.setTextSize(13);
        share.setOnClickListener(v -> {
            sounds.tap();
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, "Sfidami su FlashTris! Il mio codice partita è " + hostCode + " — avvicinati e apri l'app per giocare.");
            startActivity(Intent.createChooser(send, "Condividi codice partita"));
        });
        row.addView(share, weighted());
        col.addView(row);
        card.addView(col);
        return card;
    }

    /** Simple, lightweight radar/scan decoration used on waiting/connecting screens (no custom Canvas work needed). */
    private View radarView() {
        FrameLayout container = new FrameLayout(this);
        int size = dp(160);
        container.setLayoutParams(new LinearLayout.LayoutParams(size, size));

        View outerRing = new View(this);
        outerRing.setBackground(GameTheme.ovalStroke(GameTheme.withAlpha(GameTheme.CYAN, 90), dp(2)));
        container.addView(outerRing, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));

        View midRing = new View(this);
        int midSize = dp(110);
        midRing.setBackground(GameTheme.ovalStroke(GameTheme.withAlpha(GameTheme.CYAN, 150), dp(2)));
        container.addView(midRing, new FrameLayout.LayoutParams(midSize, midSize, Gravity.CENTER));

        View core = new View(this);
        int coreSize = dp(56);
        core.setBackground(GameTheme.ovalFill(GameTheme.VIOLET));
        container.addView(core, new FrameLayout.LayoutParams(coreSize, coreSize, Gravity.CENTER));

        GameAnimations.stop(activeAmbientAnimator);
        activeAmbientAnimator = GameAnimations.startPulse(outerRing);
        return container;
    }

    private boolean ensurePermissions() {
        List<String> required = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            required.add(Manifest.permission.BLUETOOTH_SCAN);
            required.add(Manifest.permission.BLUETOOTH_CONNECT);
            required.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            required.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        } else if (Build.VERSION.SDK_INT >= 31) {
            required.add(Manifest.permission.BLUETOOTH_SCAN);
            required.add(Manifest.permission.BLUETOOTH_CONNECT);
            required.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        } else {
            required.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        List<String> missing = new ArrayList<>();
        for (String p : required) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (!missing.isEmpty()) {
            Log.i(TAG, "Requesting missing permissions: " + missing);
            requestPermissions(missing.toArray(new String[0]), REQ_PERMISSIONS);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMISSIONS) return;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Permission denied by user");
                Toast.makeText(this, "Servono i permessi per trovare telefoni vicini.", Toast.LENGTH_LONG).show();
                showHome();
                return;
            }
        }
        Log.i(TAG, "All permissions granted");
        if (host) startAdvertising(); else startDiscovery();
    }

    private JSONObject message(String type) {
        JSONObject o = new JSONObject();
        try { o.put("type", type); } catch (JSONException ignored) { }
        return o;
    }

    private void send(JSONObject message) {
        if (!connected || endpointId == null) return;
        byte[] bytes = message.toString().getBytes(StandardCharsets.UTF_8);
        try {
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes));
        } catch (Exception e) {
            Log.e(TAG, "sendPayload failed", e);
        }
    }

    private void fail(String message) {
        Log.e(TAG, "fail: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        showHome();
    }

    private void stopEverything() {
        if (connectionsClient == null) return;
        Log.i(TAG, "stopEverything");
        try { connectionsClient.stopAdvertising(); } catch (Exception ignored) { }
        try { connectionsClient.stopDiscovery(); } catch (Exception ignored) { }
        try { connectionsClient.stopAllEndpoints(); } catch (Exception ignored) { }
    }

    private void resetSession() {
        endpointId = null;
        host = false;
        vsCpu = false;
        connected = false;
        nicknameSent = false;
        myNickname = "";
        opponentNickname = "";
        hostCode = "";
        mySymbol = ' ';
        opponentSymbol = ' ';
        turn = 'X';
        gameOver = false;
        awaitingMoveResult = false;
        clearBoard();
    }

    private void clearBoard() {
        for (int i = 0; i < board.length; i++) board[i] = ' ';
    }

    private ScrollView wrap(View view) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setBackground(GameTheme.screenBackground());
        s.addView(view);
        return s;
    }

    /** Applies the screen content, resetting any leftover ambient animator and playing the entrance animation. */
    private void renderScreen(LinearLayout content) {
        setContentView(wrap(content));
        GameAnimations.fadeSlideIn(content);
    }

    private void stopAmbientAnimator() {
        GameAnimations.stop(activeAmbientAnimator);
        activeAmbientAnimator = null;
    }

    private LinearLayout baseRoot() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(24), dp(32), dp(24), dp(30));
        return l;
    }

    private TextView title(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(34);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setTextColor(GameTheme.TEXT_PRIMARY);
        v.setGravity(Gravity.CENTER);
        v.setShadowLayer(dp(10), 0, 0, GameTheme.withAlpha(GameTheme.CYAN, 140));
        return v;
    }

    private TextView subtitle(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(17);
        v.setTextColor(GameTheme.TEXT_SECONDARY);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(0, dp(10), 0, dp(10));
        return v;
    }

    private TextView caption(String t) {
        TextView v = subtitle(t);
        v.setTextSize(13);
        v.setTextColor(GameTheme.TEXT_MUTED);
        return v;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(GameTheme.BG_NIGHT);
        b.setBackground(GameTheme.primaryButtonBackground(dp(16)));
        b.setMinHeight(dp(58));
        b.setLayoutParams(matchWrap(dp(4)));
        b.setElevation(dp(5));
        GameAnimations.pressFeedback(b);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(GameTheme.CYAN);
        b.setBackground(GameTheme.secondaryButtonBackground(dp(16)));
        b.setMinHeight(dp(54));
        b.setLayoutParams(matchWrap(dp(4)));
        b.setElevation(dp(3));
        GameAnimations.pressFeedback(b);
        return b;
    }

    private Button dangerButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(GameTheme.DANGER);
        b.setBackground(GameTheme.dangerButtonBackground(dp(16)));
        b.setMinHeight(dp(48));
        b.setLayoutParams(matchWrap(dp(4)));
        b.setElevation(dp(2));
        GameAnimations.pressFeedback(b);
        return b;
    }

    /**
     * Minimal dark-theme reskin of the standard AlertDialog: a themed custom
     * title, message body and tinted action buttons. Kept intentionally
     * lightweight (no custom layout resource) since these dialogs are used
     * only for short, transient decisions (rematch request, opponent left).
     */
    private AlertDialog styledDialog(String titleText, String message, boolean cancelable,
                                      String positiveText, android.content.DialogInterface.OnClickListener positiveListener,
                                      String negativeText, android.content.DialogInterface.OnClickListener negativeListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        TextView titleView = title(titleText);
        titleView.setTextSize(19);
        titleView.setPadding(dp(20), dp(20), dp(20), dp(6));
        builder.setCustomTitle(titleView);
        TextView messageView = subtitle(message);
        messageView.setPadding(dp(20), 0, dp(20), dp(12));
        builder.setView(messageView);
        builder.setCancelable(cancelable);
        builder.setPositiveButton(positiveText, positiveListener);
        if (negativeText != null) builder.setNegativeButton(negativeText, negativeListener);
        AlertDialog dialog = builder.create();
        Window w = dialog.getWindow();
        if (w != null) w.setBackgroundDrawable(GameTheme.roundedStroke(GameTheme.BG_PANEL, GameTheme.VIOLET, dp(18), dp(2)));
        dialog.setOnShowListener(d -> {
            Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) pos.setTextColor(GameTheme.LIME);
            Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) neg.setTextColor(GameTheme.TEXT_SECONDARY);
        });
        return dialog;
    }

    /** Local win/loss/draw statistics screen, reachable from the Home menu. All data stays on-device. */
    private void showLeaderboardScreen() {
        stopAmbientAnimator();
        root = baseRoot();
        root.addView(title("CLASSIFICA"));
        root.addView(subtitle("Le tue statistiche, salvate solo su questo telefono."));
        root.addView(space(18));

        root.addView(statsSection("ONLINE (Nearby)", stats.getWins(false), stats.getLosses(false), stats.getDraws(false), GameTheme.CYAN));
        root.addView(space(14));
        root.addView(statsSection("VS CPU", stats.getWins(true), stats.getLosses(true), stats.getDraws(true), GameTheme.VIOLET));
        root.addView(space(14));

        FrameLayout streakCard = new FrameLayout(this);
        streakCard.setBackground(GameTheme.glowPanel(GameTheme.BG_PANEL, GameTheme.LIME, dp(16), dp(2), dp(6)));
        streakCard.setElevation(dp(4));
        LinearLayout streakRow = new LinearLayout(this);
        streakRow.setOrientation(LinearLayout.HORIZONTAL);
        int streakPad = dp(16);
        streakRow.setPadding(streakPad, streakPad, streakPad, streakPad);
        streakRow.addView(statBlock("SERIE ATTUALE", String.valueOf(stats.getCurrentStreak())), weighted());
        streakRow.addView(statBlock("RECORD SERIE", String.valueOf(stats.getBestStreak())), weighted());
        streakCard.addView(streakRow);
        root.addView(streakCard, matchWrap(0));
        root.addView(space(24));

        Button back = secondaryButton("INDIETRO");
        back.setOnClickListener(v -> {
            sounds.tap();
            showHome();
        });
        root.addView(back);
        renderScreen(root);
    }

    /** Card with a section title and a VITTORIE/SCONFITTE/PAREGGI row, used on the leaderboard screen. */
    private View statsSection(String label, int wins, int losses, int draws, int accent) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(GameTheme.roundedStroke(GameTheme.BG_PANEL, GameTheme.BG_PANEL_LIGHT, dp(16), dp(2)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        col.setPadding(pad, pad, pad, pad);
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labelView.setTextColor(accent);
        col.addView(labelView);
        col.addView(space(10));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(statBlock("VITTORIE", String.valueOf(wins)), weighted());
        row.addView(statBlock("SCONFITTE", String.valueOf(losses)), weighted());
        row.addView(statBlock("PAREGGI", String.valueOf(draws)), weighted());
        col.addView(row);
        card.addView(col);
        return card;
    }

    /** Single centered value+caption block (e.g. "3" / "VITTORIE") used in stats rows. */
    private View statBlock(String label, String value) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(24);
        valueView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        valueView.setTextColor(GameTheme.TEXT_PRIMARY);
        valueView.setGravity(Gravity.CENTER);
        col.addView(valueView);
        TextView labelView = caption(label);
        labelView.setTextSize(10);
        col.addView(labelView);
        return col;
    }

    /** Local profile screen: persistent nickname + avatar shown to opponents, reachable from the Home menu. */
    private void showProfileScreen() {
        stopAmbientAnimator();
        root = baseRoot();
        root.addView(title("PROFILO"));
        root.addView(subtitle("Nome e avatar mostrati nelle tue partite."));
        root.addView(space(18));

        FrameLayout avatarCard = new FrameLayout(this);
        avatarCard.setBackground(GameTheme.glowPanel(GameTheme.BG_PANEL, GameTheme.VIOLET, dp(50), dp(2), dp(6)));
        avatarCard.setElevation(dp(4));
        TextView avatarView = new TextView(this);
        avatarView.setText(stats.getAvatar());
        avatarView.setTextSize(48);
        avatarView.setGravity(Gravity.CENTER);
        avatarCard.addView(avatarView, new FrameLayout.LayoutParams(dp(100), dp(100), Gravity.CENTER));
        FrameLayout avatarWrap = new FrameLayout(this);
        avatarWrap.addView(avatarCard, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(avatarWrap, matchWrap(0));
        root.addView(space(18));

        EditText nameInput = new EditText(this);
        nameInput.setText(stats.getNickname("FlashPlayer"));
        nameInput.setHint("Il tuo nickname");
        nameInput.setHintTextColor(GameTheme.TEXT_MUTED);
        nameInput.setTextColor(GameTheme.TEXT_PRIMARY);
        nameInput.setSingleLine(true);
        nameInput.setGravity(Gravity.CENTER);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameInput.setTextSize(18);
        nameInput.setPadding(dp(18), dp(14), dp(18), dp(14));
        nameInput.setBackground(GameTheme.roundedStroke(GameTheme.BG_PANEL, GameTheme.CYAN, dp(14), dp(2)));
        root.addView(nameInput, matchWrap(0));
        root.addView(space(18));

        root.addView(caption("Scegli un avatar"));
        root.addView(space(8));
        GridLayout avatarGrid = new GridLayout(this);
        avatarGrid.setColumnCount(5);
        for (String avatar : GameStats.AVATARS) {
            boolean selected = avatar.equals(stats.getAvatar());
            TextView cell = new TextView(this);
            cell.setText(avatar);
            cell.setTextSize(26);
            cell.setGravity(Gravity.CENTER);
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setBackground(selected
                    ? GameTheme.roundedStroke(GameTheme.BG_PANEL_LIGHT, GameTheme.LIME, dp(12), dp(2))
                    : GameTheme.roundedFill(GameTheme.BG_PANEL, dp(12)));
            GameAnimations.pressFeedback(cell);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(52);
            lp.height = dp(52);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            cell.setOnClickListener(v -> {
                sounds.tap();
                stats.setAvatar(avatar);
                showProfileScreen();
            });
            avatarGrid.addView(cell, lp);
        }
        FrameLayout gridWrap = new FrameLayout(this);
        gridWrap.addView(avatarGrid, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(gridWrap, matchWrap(0));
        root.addView(space(20));

        int totalWins = stats.getWins(false) + stats.getWins(true);
        int totalLosses = stats.getLosses(false) + stats.getLosses(true);
        root.addView(caption("Vittorie totali: " + totalWins + "  •  Sconfitte totali: " + totalLosses));
        root.addView(space(20));

        Button save = primaryButton("SALVA E TORNA");
        save.setOnClickListener(v -> {
            sounds.tap();
            String name = nameInput.getText().toString().trim();
            stats.setNickname(name.isEmpty() ? "FlashPlayer" : (name.length() > 18 ? name.substring(0, 18) : name));
            showHome();
        });
        root.addView(save);
        renderScreen(root);
    }

    /** Local audio/vibration settings screen, reachable from the Home gear icon. */
    private void showSettingsScreen() {
        stopAmbientAnimator();
        root = baseRoot();
        root.addView(title("IMPOSTAZIONI"));
        root.addView(subtitle("Preferenze audio, salvate solo su questo telefono."));
        root.addView(space(20));

        root.addView(settingsToggleRow("Effetti sonori", sounds.isSoundEnabled(), enabled -> {
            sounds.setSoundEnabled(enabled);
            if (enabled) sounds.tap();
        }));
        root.addView(space(12));
        root.addView(settingsToggleRow("Vibrazione", sounds.isVibrationEnabled(), sounds::setVibrationEnabled));
        root.addView(space(24));

        root.addView(neonDivider());
        TextView appInfo = caption(appVersionLabel());
        root.addView(appInfo);
        TextView authorInfo = caption("Sviluppato da Luca Piciollo");
        root.addView(authorInfo);
        root.addView(space(20));

        Button back = secondaryButton("INDIETRO");
        back.setOnClickListener(v -> {
            sounds.tap();
            showHome();
        });
        root.addView(back);
        renderScreen(root);
    }

    /** "FlashTris vX.Y.Z" label built from the installed package's versionName (falls back gracefully). */
    private String appVersionLabel() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return "FlashTris v" + (versionName != null ? versionName : "?");
        } catch (PackageManager.NameNotFoundException e) {
            return "FlashTris";
        }
    }

    private interface BoolConsumer {
        void accept(boolean value);
    }

    private View settingsToggleRow(String label, boolean initiallyOn, BoolConsumer onChange) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(GameTheme.roundedStroke(GameTheme.BG_PANEL, GameTheme.BG_PANEL_LIGHT, dp(14), dp(2)));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(16);
        row.setPadding(pad, pad, pad, pad);
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(16);
        labelView.setTextColor(GameTheme.TEXT_PRIMARY);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(labelView, weighted());
        Button toggle = new Button(this);
        toggle.setAllCaps(false);
        toggle.setTextSize(13);
        toggle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toggle.setMinHeight(dp(38));
        toggle.setPadding(dp(18), 0, dp(18), 0);
        GameAnimations.pressFeedback(toggle);
        final boolean[] state = {initiallyOn};
        Runnable refresh = () -> {
            boolean on = state[0];
            toggle.setText(on ? "ON" : "OFF");
            toggle.setTextColor(on ? GameTheme.BG_NIGHT : GameTheme.TEXT_SECONDARY);
            toggle.setBackground(GameTheme.withRipple(
                    on ? GameTheme.roundedFill(GameTheme.LIME, dp(19)) : GameTheme.roundedStroke(GameTheme.BG_PANEL_LIGHT, GameTheme.TEXT_MUTED, dp(19), dp(2)),
                    on ? GameTheme.BG_NIGHT : GameTheme.CYAN));
        };
        refresh.run();
        toggle.setOnClickListener(v -> {
            state[0] = !state[0];
            onChange.accept(state[0]);
            refresh.run();
        });
        row.addView(toggle);
        card.addView(row);
        return card;
    }

    private SpaceView space(int dp) { return new SpaceView(this, dp, false); }
    private SpaceView spaceHorizontal(int dp) { return new SpaceView(this, dp, true); }

    private LinearLayout.LayoutParams matchWrap(int marginTop) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, marginTop, 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class SpaceView extends View {
        SpaceView(Activity context, int sizeDp, boolean horizontal) {
            super(context);
            int px = Math.round(sizeDp * context.getResources().getDisplayMetrics().density);
            setLayoutParams(new LinearLayout.LayoutParams(horizontal ? px : 1, horizontal ? 1 : px));
        }
    }
}
