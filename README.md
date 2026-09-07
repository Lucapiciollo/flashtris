# FlashTris

Gioco del tris Android peer-to-peer tra due telefoni, senza backend e senza registrazione.

## Flusso
1. Un telefono sceglie **Crea partita**.
2. L'altro sceglie **Trova partita**.
3. Nearby Connections associa i due telefoni (advertising + discovery, `Strategy.P2P_POINT_TO_POINT`).
4. Solo dopo la connessione entrambi inseriscono il nickname (nessuna registrazione, il nome vive solo per la sessione).
5. L'host sceglie **X** oppure **O**; l'altro simbolo va automaticamente all'ospite.
6. X inizia sempre.
7. Lo stato del gioco è validato dall'host (autorità unica) e sincronizzato via payload BYTES locali (JSON su Nearby Connections).
8. Non è possibile giocare fuori turno, su una cella occupata, inviare due mosse consecutive o continuare dopo la fine partita.
9. A fine partita è disponibile la rivincita; i simboli vengono invertiti; alla disconnessione dell'amico si torna alla home in modo sicuro.

## Tecnologia
- Android nativo Java (nessun Kotlin, nessun AppCompat/Jetpack necessario)
- Google Nearby Connections (`play-services-nearby:19.3.0`)
- Nessun server / database / login / Firebase
- `Strategy.P2P_POINT_TO_POINT`

## Requisiti
- Android Studio Ladybug (o più recente) oppure JDK 17 + Android SDK da riga di comando.
- Android SDK: `compileSdk 35`, `minSdk 23`, `targetSdk 35`, Build-Tools compatibili (scaricati automaticamente da Gradle se mancanti e la licenza SDK è accettata).
- Due dispositivi Android fisici con Bluetooth/Wi-Fi attivi per testare la connessione P2P (un emulatore non è sufficiente per Nearby Connections).

## Aprire il progetto
1. Apri la cartella `flashtris` con Android Studio ("Open an existing project").
2. Lascia che Android Studio sincronizzi Gradle (usa il Gradle Wrapper incluso, non serve installare Gradle a parte).
3. Se richiesto, accetta le licenze SDK per `platforms;android-35` e i Build-Tools necessari.

## Compilare da riga di comando
Il progetto include il Gradle Wrapper completo (`gradlew`, `gradlew.bat`, `gradle/wrapper/`), quindi non serve installare Gradle manualmente.

```bash
# Linux/macOS
./gradlew clean assembleDebug

# Windows
gradlew.bat clean assembleDebug
```

## Generare l'APK (debug)
```bash
./gradlew assembleDebug
```
L'APK sarà in `app/build/outputs/apk/debug/app-debug.apk`.

## Generare l'AAB (release, per Play Store)
```bash
./gradlew bundleRelease
```
L'AAB sarà in `app/build/outputs/bundle/release/app-release.aab`.

### Firma di release (locale, senza mettere segreti in Git)

**Il keystore e le password non vanno mai committati in Git**, nemmeno in un repository privato: se il
repository diventasse pubblico, venisse clonato o la history venisse letta, la chiave sarebbe
compromessa per sempre (Google Play non permette di cambiarla facilmente). Il progetto è quindi
configurato per leggere la firma da un file locale escluso da Git.

**Setup già effettuato in locale** (file NON versionati, presenti solo su questa macchina):
- `keystore/flashtris-release.jks` — keystore RSA 2048 bit, validità 30 anni, alias `flashtris`.
- `keystore.properties` (nella root del progetto) — contiene `storeFile`, `storePassword`,
  `keyAlias`, `keyPassword` letti automaticamente da `app/build.gradle`.

Entrambi sono in `.gitignore` (`*.jks`, `keystore/`, `keystore.properties`) e **non verranno mai
committati**. Se cloni il repository su un'altra macchina, questi file non ci saranno: dovrai
rigenerare un keystore (vedi comando sotto) o copiare in modo sicuro (es. password manager, USB
cifrata) quello esistente — **perderlo significa non poter più aggiornare l'app pubblicata con la
stessa firma**. Fai un backup sicuro subito.

Per rigenerare un keystore da zero (esempio):
```bash
keytool -genkeypair -v -keystore keystore/flashtris-release.jks -alias flashtris \
  -keyalg RSA -keysize 2048 -validity 10957 \
  -dname "CN=FlashTris, OU=Dev, O=LucaPiciollo, L=Torino, ST=Piemonte, C=IT"
```
poi crea `keystore.properties` nella root con:
```properties
storeFile=keystore/flashtris-release.jks
storePassword=<password-scelta>
keyAlias=flashtris
keyPassword=<stessa-password-dello-store>
```
(i keystore PKCS12 moderni richiedono store password e key password identiche).

Senza `keystore.properties` **e** senza le variabili d'ambiente `RELEASE_STORE_FILE` /
`RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`, l'AAB/APK release viene
generato **non firmato** (la build comunque completa con successo).

### Firma in CI (GitHub Actions), senza segreti nel repo
Il workflow decodifica il keystore da un secret GitHub in base64 solo a runtime, e non lo scrive mai
nel repository. Per abilitarlo:
1. Converti il keystore in base64:
   ```powershell
   certutil -encode keystore\flashtris-release.jks keystore-b64.txt
   ```
   (su Linux/macOS: `base64 -w0 keystore/flashtris-release.jks > keystore-b64.txt`)
2. Su GitHub → *Settings → Secrets and variables → Actions*, crea questi secrets:
   - `RELEASE_KEYSTORE_BASE64` — contenuto del file base64 generato al punto 1
   - `RELEASE_STORE_PASSWORD`
   - `RELEASE_KEY_ALIAS` (`flashtris`)
   - `RELEASE_KEY_PASSWORD` (uguale a `RELEASE_STORE_PASSWORD` per PKCS12)
3. Elimina il file `keystore-b64.txt` locale dopo averlo incollato nel secret (non committarlo).

Senza questi secrets configurati, la pipeline continua a funzionare e produce comunque un AAB non firmato.

## Come funziona il collegamento tra i due telefoni
1. Entrambi i telefoni devono concedere i permessi runtime richiesti da Nearby Connections (gestiti automaticamente dall'app in base alla versione Android):
   - Android 13+: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `NEARBY_WIFI_DEVICES`
   - Android 12 (API 31-32): `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`
   - Android ≤ 11: `ACCESS_FINE_LOCATION`
2. Il telefono host chiama `Nearby.startAdvertising(...)`; l'altro chiama `Nearby.startDiscovery(...)`.
3. Quando l'endpoint viene trovato, il client richiede la connessione (`requestConnection`); l'host la accetta (`acceptConnection`).
4. Dopo la connessione (`onConnectionResult` con successo) i due dispositivi si scambiano nickname, simbolo e mosse tramite `Payload.fromBytes(...)` con messaggi JSON (`NICKNAME`, `START`, `MOVE_REQUEST`, `STATE`, `REMATCH_REQUEST`, `REMATCH_START`, `LEAVE`).
5. In caso di disconnessione (`onDisconnected`), l'app mostra un avviso e torna alla home in modo sicuro, senza crash.

## CI/CD
Il workflow [`.github/workflows/android-build.yml`](.github/workflows/android-build.yml) esegue automaticamente, ad ogni push/PR su `main`:
1. Checkout del repository
2. Setup JDK 17 (Temurin)
3. `chmod +x gradlew`
4. `./gradlew clean`
5. `./gradlew assembleDebug`
6. `./gradlew bundleRelease`
7. Upload di APK debug e AAB release come artifact della build

## Test che richiedono dispositivi fisici
Le seguenti verifiche funzionali non sono automatizzabili in CI e richiedono due dispositivi Android fisici vicini:
- Advertising/discovery reale via Bluetooth/Wi-Fi Nearby Connections
- Creazione partita, connessione del secondo dispositivo, scelta X/O, inserimento nickname
- Sincronizzazione mosse, turni, vittoria, pareggio
- Gestione della disconnessione e della rivincita

