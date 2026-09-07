package com.lucapiciollo.filetto;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

public class MainActivity extends Activity {

    private static final String SERVICE_ID = "com.lucapiciollo.filetto.nearby";
    private static final Strategy STRATEGY = Strategy.P2P_POINT_TO_POINT;
    private static final int REQ_PERMISSIONS = 900;

    private final Random random = new Random();
    private ConnectionsClient connectionsClient;
    private String endpointId;
    private boolean host;
    private boolean connected;
    private boolean nicknameSent;
    private String myNickname = "";
    private String opponentNickname = "";
    private char mySymbol = ' ';
    private char opponentSymbol = ' ';
    private char turn = 'X';
    private boolean gameOver;
    private final char[] board = new char[9];

    private LinearLayout root;
    private TextView statusText;
    private TextView titleText;
    private final List<Button> cellButtons = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectionsClient = Nearby.getConnectionsClient(this);
        showHome();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopEverything();
    }

    private void showHome() {
        stopEverything();
        resetSession();
        root = baseRoot();
        titleText = title("FLASH TRIS");
        root.addView(titleText);
        root.addView(subtitle("Due telefoni. Zero account. Una partita al volo."));
        root.addView(space(24));

        Button create = primaryButton("CREA PARTITA");
        create.setOnClickListener(v -> {
            host = true;
            if (ensurePermissions()) startAdvertising();
        });
        root.addView(create);

        Button find = secondaryButton("TROVA PARTITA");
        find.setOnClickListener(v -> {
            host = false;
            if (ensurePermissions()) startDiscovery();
        });
        root.addView(find);

        root.addView(space(18));
        root.addView(caption("La connessione è locale tramite Nearby Connections. Nessun backend e nessuna registrazione."));
        setContentView(wrap(root));
    }

    private void startAdvertising() {
        showWaiting("Partita creata", "Sto aspettando un amico vicino…");
        String endpointName = "FlashTris-" + String.format(Locale.ITALY, "%04d", random.nextInt(10000));
        AdvertisingOptions options = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(endpointName, SERVICE_ID, lifecycleCallback, options)
                .addOnFailureListener(e -> fail("Impossibile creare la partita: " + e.getMessage()));
    }

    private void startDiscovery() {
        showWaiting("Cerco partite", "Tieni aperta l'app dell'amico che ha creato la partita.");
        DiscoveryOptions options = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, discoveryCallback, options)
                .addOnFailureListener(e -> fail("Ricerca non disponibile: " + e.getMessage()));
    }

    private final EndpointDiscoveryCallback discoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(String id, DiscoveredEndpointInfo info) {
            if (connected || endpointId != null) return;
            endpointId = id;
            if (statusText != null) statusText.setText("Partita trovata: " + info.getEndpointName() + "\nConnessione…");
            connectionsClient.stopDiscovery();
            connectionsClient.requestConnection("Giocatore", id, lifecycleCallback)
                    .addOnFailureListener(e -> {
                        endpointId = null;
                        fail("Connessione fallita: " + e.getMessage());
                    });
        }

        @Override
        public void onEndpointLost(String id) {
            if (id.equals(endpointId) && !connected) endpointId = null;
        }
    };

    private final ConnectionLifecycleCallback lifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String id, ConnectionInfo info) {
            endpointId = id;
            connectionsClient.acceptConnection(id, payloadCallback);
        }

        @Override
        public void onConnectionResult(String id, ConnectionResolution result) {
            if (result.getStatus().isSuccess()) {
                connected = true;
                endpointId = id;
                connectionsClient.stopAdvertising();
                connectionsClient.stopDiscovery();
                runOnUiThread(MainActivity.this::showNicknameScreen);
            } else {
                endpointId = null;
                connected = false;
                runOnUiThread(() -> fail("Connessione rifiutata o non riuscita."));
            }
        }

        @Override
        public void onDisconnected(String id) {
            connected = false;
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Amico disconnesso")
                    .setMessage("La partita è terminata.")
                    .setCancelable(false)
                    .setPositiveButton("TORNA ALLA HOME", (d, w) -> showHome())
                    .show());
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String id, Payload payload) {
            if (payload.getType() != Payload.Type.BYTES || payload.asBytes() == null) return;
            String raw = new String(payload.asBytes(), StandardCharsets.UTF_8);
            runOnUiThread(() -> handleMessage(raw));
        }

        @Override
        public void onPayloadTransferUpdate(String id, PayloadTransferUpdate update) { }
    };

    private void showNicknameScreen() {
        root = baseRoot();
        root.addView(title("CONNESSI ✓"));
        root.addView(subtitle("Ora scegli il nome da mostrare solo per questa partita."));
        root.addView(space(22));

        EditText input = new EditText(this);
        input.setHint("Il tuo nickname");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setTextSize(18);
        input.setPadding(dp(18), dp(14), dp(18), dp(14));
        root.addView(input, matchWrap(0));

        Button continueBtn = primaryButton("CONTINUA");
        continueBtn.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError("Inserisci un nickname");
                return;
            }
            myNickname = name.length() > 18 ? name.substring(0, 18) : name;
            nicknameSent = true;
            send(message("NICKNAME").putOpt("name", myNickname));
            showWaiting("Perfetto, " + myNickname, "Aspetto il nickname del tuo amico…");
            maybeProceedAfterNicknames();
        });
        root.addView(continueBtn);
        setContentView(wrap(root));
    }

    private void maybeProceedAfterNicknames() {
        if (!nicknameSent || opponentNickname.isEmpty()) return;
        if (host) showSymbolChoice();
        else showWaiting("Ci siamo", opponentNickname + " sta scegliendo X oppure O…");
    }

    private void showSymbolChoice() {
        root = baseRoot();
        root.addView(title("SCEGLI IL SIMBOLO"));
        root.addView(subtitle("Tu scegli. " + opponentNickname + " riceverà automaticamente l'altro simbolo."));
        root.addView(space(24));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        Button x = symbolButton("X");
        Button o = symbolButton("O");
        row.addView(x, weighted());
        row.addView(spaceHorizontal(12));
        row.addView(o, weighted());
        root.addView(row, matchWrap(0));

        x.setOnClickListener(v -> selectHostSymbol('X'));
        o.setOnClickListener(v -> selectHostSymbol('O'));
        setContentView(wrap(root));
    }

    private void selectHostSymbol(char symbol) {
        mySymbol = symbol;
        opponentSymbol = symbol == 'X' ? 'O' : 'X';
        turn = 'X';
        gameOver = false;
        clearBoard();
        JSONObject msg = message("START");
        try {
            msg.put("guestSymbol", String.valueOf(opponentSymbol));
            msg.put("hostName", myNickname);
            msg.put("guestName", opponentNickname);
        } catch (JSONException ignored) { }
        send(msg);
        showGame();
    }

    private void showGame() {
        root = baseRoot();
        root.addView(title("FLASH TRIS"));
        TextView players = subtitle(myNickname + "  " + mySymbol + "    •    " + opponentSymbol + "  " + opponentNickname);
        players.setGravity(Gravity.CENTER);
        root.addView(players);
        root.addView(space(18));

        statusText = new TextView(this);
        statusText.setTextSize(19);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);
        statusText.setTextColor(Color.rgb(35, 39, 55));
        root.addView(statusText, matchWrap(dp(14)));

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
            b.setBackgroundColor(Color.WHITE);
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

        Button leave = secondaryButton("ESCI DALLA PARTITA");
        leave.setOnClickListener(v -> {
            send(message("LEAVE"));
            showHome();
        });
        root.addView(leave);
        setContentView(wrap(root));
        renderBoard();
    }

    private void onCellPressed(int cell) {
        if (gameOver || board[cell] != ' ' || turn != mySymbol) return;
        if (host) {
            applyMove(cell, mySymbol);
        } else {
            JSONObject msg = message("MOVE_REQUEST");
            try { msg.put("cell", cell); } catch (JSONException ignored) { }
            send(msg);
        }
    }

    private void applyMove(int cell, char symbol) {
        if (!host || gameOver || cell < 0 || cell > 8 || board[cell] != ' ' || turn != symbol) return;
        board[cell] = symbol;
        char winner = winner();
        if (winner != ' ') gameOver = true;
        else if (isDraw()) gameOver = true;
        else turn = turn == 'X' ? 'O' : 'X';
        broadcastState();
        renderBoard();
        if (gameOver) showEndDialogAfterDelay();
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
                        for (int i = 0; i < 9; i++) {
                            String v = arr.optString(i, "");
                            board[i] = v.isEmpty() ? ' ' : v.charAt(0);
                        }
                        String t = msg.optString("turn", "X");
                        turn = t.isEmpty() ? 'X' : t.charAt(0);
                        gameOver = msg.optBoolean("gameOver", false);
                        renderBoard();
                        if (gameOver) showEndDialogAfterDelay();
                    }
                    break;
                case "REMATCH_REQUEST":
                    if (host) {
                        new AlertDialog.Builder(this)
                                .setTitle(opponentNickname + " vuole la rivincita")
                                .setPositiveButton("ACCETTA", (d, w) -> startRematch())
                                .setNegativeButton("NO", null)
                                .show();
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
                        showGame();
                    }
                    break;
                case "LEAVE":
                    new AlertDialog.Builder(this)
                            .setTitle("Partita terminata")
                            .setMessage(opponentNickname + " è uscito dalla partita.")
                            .setPositiveButton("HOME", (d, w) -> showHome())
                            .show();
                    break;
            }
        } catch (Exception e) {
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
        JSONObject msg = message("REMATCH_START");
        try { msg.put("guestSymbol", String.valueOf(opponentSymbol)); } catch (JSONException ignored) { }
        send(msg);
        showGame();
    }

    private void showEndDialogAfterDelay() {
        if (root == null) return;
        root.postDelayed(() -> {
            char w = winner();
            String text;
            if (w == ' ') text = "Pareggio!";
            else if (w == mySymbol) text = "Hai vinto! 🎉";
            else text = opponentNickname + " ha vinto.";

            AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                    .setTitle(text)
                    .setNegativeButton("ESCI", (d, which) -> showHome());
            if (host) dialog.setPositiveButton("RIVINCITA", (d, which) -> startRematch());
            else dialog.setPositiveButton("CHIEDI RIVINCITA", (d, which) -> {
                send(message("REMATCH_REQUEST"));
                Toast.makeText(this, "Richiesta inviata", Toast.LENGTH_SHORT).show();
            });
            dialog.show();
        }, 250);
    }

    private void renderBoard() {
        if (cellButtons.size() != 9 || statusText == null) return;
        for (int i = 0; i < 9; i++) {
            char c = board[i];
            cellButtons.get(i).setText(c == ' ' ? "" : String.valueOf(c));
            cellButtons.get(i).setEnabled(!gameOver && c == ' ' && turn == mySymbol);
        }
        if (gameOver) {
            char w = winner();
            if (w == ' ') statusText.setText("Pareggio");
            else statusText.setText(w == mySymbol ? "Hai vinto 🎉" : opponentNickname + " ha vinto");
        } else if (turn == mySymbol) {
            statusText.setText("Tocca a te • " + mySymbol);
        } else {
            statusText.setText("Tocca a " + opponentNickname + " • " + opponentSymbol);
        }
    }

    private char winner() {
        int[][] lines = {
                {0,1,2},{3,4,5},{6,7,8},
                {0,3,6},{1,4,7},{2,5,8},
                {0,4,8},{2,4,6}
        };
        for (int[] l : lines) {
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
        root = baseRoot();
        root.addView(title(title.toUpperCase(Locale.ITALY)));
        statusText = subtitle(status);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);
        root.addView(space(26));
        TextView pulse = new TextView(this);
        pulse.setText("◎");
        pulse.setGravity(Gravity.CENTER);
        pulse.setTextSize(72);
        pulse.setTextColor(Color.rgb(91, 103, 241));
        root.addView(pulse, matchWrap(dp(24)));
        Button cancel = secondaryButton("ANNULLA");
        cancel.setOnClickListener(v -> showHome());
        root.addView(cancel);
        setContentView(wrap(root));
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
                Toast.makeText(this, "Servono i permessi per trovare telefoni vicini.", Toast.LENGTH_LONG).show();
                showHome();
                return;
            }
        }
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
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes));
    }

    private void fail(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        showHome();
    }

    private void stopEverything() {
        if (connectionsClient == null) return;
        try { connectionsClient.stopAdvertising(); } catch (Exception ignored) { }
        try { connectionsClient.stopDiscovery(); } catch (Exception ignored) { }
        try { connectionsClient.stopAllEndpoints(); } catch (Exception ignored) { }
    }

    private void resetSession() {
        endpointId = null;
        host = false;
        connected = false;
        nicknameSent = false;
        myNickname = "";
        opponentNickname = "";
        mySymbol = ' ';
        opponentSymbol = ' ';
        turn = 'X';
        gameOver = false;
        clearBoard();
    }

    private void clearBoard() {
        for (int i = 0; i < board.length; i++) board[i] = ' ';
    }

    private ScrollView wrap(View view) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setBackgroundColor(Color.rgb(247, 248, 252));
        s.addView(view);
        return s;
    }

    private LinearLayout baseRoot() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(24), dp(40), dp(24), dp(30));
        return l;
    }

    private TextView title(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(34);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setTextColor(Color.rgb(20, 23, 32));
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private TextView subtitle(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(17);
        v.setTextColor(Color.rgb(94, 99, 116));
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(0, dp(10), 0, dp(10));
        return v;
    }

    private TextView caption(String t) {
        TextView v = subtitle(t);
        v.setTextSize(13);
        return v;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(17);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(91, 103, 241));
        b.setMinHeight(dp(58));
        LinearLayout.LayoutParams lp = matchWrap(dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(Color.rgb(50, 55, 73));
        b.setBackgroundColor(Color.rgb(232, 234, 242));
        b.setMinHeight(dp(54));
        LinearLayout.LayoutParams lp = matchWrap(dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    private Button symbolButton(String text) {
        Button b = primaryButton(text);
        b.setTextSize(44);
        b.setMinHeight(dp(120));
        return b;
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
