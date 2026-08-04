# Backup — export automat, restore manual

Backup pentru contacte + istoric de mesaje. NU pentru identitate (deja
recuperabilă din cele 24 de cuvinte) și NU pentru sesiuni Ratchet/prekeys
(secrete vii, n-ar trebui duplicate într-un folder care poate ajunge
sincronizat în cloud — un dispozitiv restaurat oricum reia o sesiune nouă
cu fiecare contact).

## Cum e criptat

Cheia de backup vine din `IdentityGenerator.deriveBackupKey(identity)` —
derivată din același seed ca restul, deci nicio parolă nouă de reținut:
dacă ai cele 24 de cuvinte, poți decripta orice backup al tău. Criptare
prin `CryptoPrimitives.encrypt` (AEAD), cu peerId-ul legat ca associated
data.

## Fișiere noi

- `backup/BackupExporter.java` — serializare + criptare/decriptare. Pur,
  fără `android.*` — de-aia stă în `:app` (depinde de `NulChatRepository`,
  care e Android-specific), dar logica însăși nu ține de platformă.
- `backup/BackupWorker.java` — job-ul WorkManager care rulează efectiv
  export-ul, scrie un fișier nou datat în folderul SAF ales, șterge
  backup-urile mai vechi de 5.
- `backup/BackupScheduler.java` — pornește/oprește programarea periodică
  (zilnic, doar când bateria nu e critică — nu depinde de rețea, fiindcă
  scrie doar local; dacă folderul e de fapt un folder sincronizat cu
  Drive/Dropbox/Syncthing, aplicația aia își vede singură de upload).

## Modificate

- `identity/IdentityGenerator.java` (`:core`) — `deriveBackupKey`.
- `data/NulChatRepository.java` — `getAllMessages()`,
  `restoreMessageIfAbsent()` (idempotent, sigur de rulat de mai multe
  ori), `restorePeer()`.
- `util/IdentityStore.java` — flag enabled + URI-ul folderului ales.
- `SettingsFragment.java` / `fragment_settings.xml` / `strings.xml` —
  secțiune nouă "Backup": switch, "Choose backup folder" (SAF,
  `ACTION_OPEN_DOCUMENT_TREE`), "Restore from backup file…"
  (`ACTION_OPEN_DOCUMENT`).
- `app/build.gradle` — `androidx.work:work-runtime`,
  `androidx.documentfile:documentfile`, `androidx.activity:activity`
  (explicit, pentru `ActivityResultContracts`).

## Ce nu face (încă)

- Nu verifică dacă folderul ales tot există/e scriabil decât când rulează
  efectiv (dacă ștergi folderul, afli abia la următorul backup ratat —
  `Result.retry()`, WorkManager reîncearcă cu backoff).
- Restore face merge (peers upsert, mesaje idempotente după `id`) — nu
  există "preview înainte de restore" sau opțiune de a alege ce anume
  restaurezi.
