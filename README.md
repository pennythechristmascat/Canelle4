# Canelle — compagnon de poche (Android)

Canelle est un petit panda roux en pixels qui vit dans ton téléphone. Il discute avec toi, prend de tes nouvelles et sait se servir du téléphone. **Tout fonctionne sur le téléphone** : pas de clé API, pas de compte, pas d'abonnement.

## Le cerveau local

Canelle réfléchit avec **Gemma 4 E2B**, un modèle d'IA de Google (licence libre Apache 2.0) qui tourne **directement sur le téléphone** grâce à LiteRT-LM.

- Au premier lancement, Canelle propose de télécharger son cerveau : **2,6 Go, une seule fois** (depuis Hugging Face). Le téléchargement continue même si tu fermes l'appli. Par défaut, il attend le wifi.
- Ensuite, la discussion marche **sans internet**, et tes messages ne quittent jamais le téléphone.
- Il faut **Android 12 ou plus récent**, et idéalement **6 Go de mémoire ou plus**. Sur un téléphone récent, la réponse arrive en quelques secondes ; sur un téléphone plus ancien, c'est plus lent.
- Au démarrage, Canelle essaie d'utiliser la puce graphique (plus rapide). Si elle ne marche pas sur ton téléphone, il passe tout seul au processeur. Réglable dans **Menu → Réglages**.
- Menu → Réglages → **Mon cerveau** : état du téléchargement, bouton pour le supprimer (libère 2,6 Go).

## Ce que Canelle sait faire (même sans le cerveau)

Ces demandes sont reconnues directement, sans IA : c'est instantané et fiable.

| Fonction | Exemples |
|---|---|
| Batterie | « Ma batterie ? », « Je dois recharger ? » — et le temps restant avant la charge complète quand le téléphone est branché. |
| Météo | « Météo », « Météo à Rennes demain », « Je prends un parapluie ? », « La météo de la semaine » |
| Heure et date | « Quelle heure est-il ? », « Quelle heure est-il à Tokyo ? », « On est quel jour ? » |
| Lieux et Google Maps | « Une pharmacie près d'ici », « Où est le McDo le plus proche ? », « Emmène-moi à la gare », « Itinéraire vers Saint-Brieuc » |
| Réveils et minuteurs | « Réveille-moi à 7 h 30 », « Réveil à 6 h tous les jours », « Minuteur de 10 minutes », « Mes réveils » |
| Calculs | « 12 fois 4 », « 15 % de 80 », « Racine carrée de 144 » |
| Mémoire | « Je m'appelle Léa », « Retiens que j'adore les chats » |

Des **raccourcis** au-dessus de la zone de texte permettent de tout faire d'un toucher. La météo et les lieux utilisent internet (Open-Meteo et OpenStreetMap, gratuits et sans clé).

Et aussi : **prises de nouvelles** par notification (tu réponds directement dans la notification), **micro** pour parler, **voix** pour que Canelle lise ses réponses, affichage **plein écran**.

## Mettre à jour ton dépôt GitHub

1. Sur la page de ton dépôt : **Add file → Upload files**.
2. Dézippe `canelle-android.zip` sur ton ordinateur, ouvre le dossier, et **glisse tout son contenu** (les dossiers `app`, `gradle`, `.github` et les fichiers) dans la page. Les anciens fichiers seront remplacés.
   - Le dossier `.github` est caché sur certains ordinateurs : sur Windows, active « Afficher les éléments masqués » ; sur Mac, appuie sur Cmd + Maj + point.
3. Clique sur **Commit changes**.
4. Onglet **Actions** : la construction « Canelle APK » démarre toute seule (environ 5 à 10 minutes). Quand le rond devient vert, ouvre-la et télécharge l'APK en bas de la page, dans **Artifacts**.

## Installer

**Important, une seule fois :** désinstalle l'ancienne version de Canelle avant d'installer celle-ci. L'ancienne était signée avec une clé temporaire différente à chaque construction ; cette version a une signature fixe (`app/canelle.keystore`), donc **les prochaines mises à jour s'installeront par-dessus**, sans rien perdre.

Ensuite, ouvre l'APK sur le téléphone et accepte l'installation depuis cette source si Android le demande.

## Si la construction échoue

Ouvre la construction en rouge dans l'onglet **Actions**, clique sur l'étape en erreur et copie les lignes rouges (souvent celles qui commencent par `e:` ou `What went wrong`). Elles suffisent pour corriger.

## Vie privée

- Les discussions, les souvenirs et les réglages restent sur le téléphone.
- Le cerveau tourne hors ligne : aucun message n'est envoyé à un serveur.
- Seules la météo et la recherche de lieux utilisent internet : ta position est alors envoyée à Open-Meteo et à OpenStreetMap.

## Détails techniques

- Kotlin 2.4, Android Gradle Plugin 8.13, Gradle 8.14.3, compileSdk 36, minSdk 31.
- IA locale : `com.google.ai.edge.litertlm:litertlm-android:0.17.1`, modèle `gemma-4-E2B-it.litertlm` (litert-community sur Hugging Face).
- Interface : page web locale (`app/src/main/assets/index.html`) dans une WebView, reliée au code Kotlin par un pont JavaScript.
