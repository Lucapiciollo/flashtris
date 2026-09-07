# FlashTris

Gioco del tris Android peer-to-peer tra due telefoni, senza backend e senza registrazione.

## Flusso
1. Un telefono sceglie **Crea partita**.
2. L'altro sceglie **Trova partita**.
3. Nearby Connections associa i due telefoni.
4. Solo dopo la connessione entrambi inseriscono il nickname.
5. L'host sceglie **X** oppure **O**.
6. X inizia sempre.
7. Lo stato del gioco viene validato dall'host e sincronizzato via payload locali.
8. A fine partita è disponibile la rivincita; i simboli vengono invertiti.

## Tecnologia
- Android nativo Java
- Google Nearby Connections (`play-services-nearby:19.3.0`)
- Nessun server / database / login
- `Strategy.P2P_POINT_TO_POINT`

## Build
Aprire la cartella in Android Studio (SDK 35), sincronizzare Gradle e lanciare `assembleDebug`.
L'APK sarà in `app/build/outputs/apk/debug/app-debug.apk`.
