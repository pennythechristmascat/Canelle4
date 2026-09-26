# Canelle AI — compagnon de poche (Android) · V5.9.0 · Early Access

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
| Batterie | « Ma batterie ? », « Je dois recharger ? » — temps de charge mesuré sur ton téléphone, puissance, vitesse, température, santé, autonomie restante. |
| Météo | « Météo », « Météo à Rennes demain », « Je prends un parapluie ? », « La météo de la semaine » |
| Musique | « Mets de la musique », « Joue Daft Punk sur Spotify », « Ouvre Deezer », « Pause », « Suivante », « Précédente », « C'est quoi cette musique ? » |
| Jeux et interactions | 55 au total : voir « Jouer avec Canelle » ci-dessous, ou les raccourcis **Jeux** et **Interactions** |
| Heure et date | « Quelle heure est-il ? », « Quelle heure est-il à Tokyo ? », « On est quel jour ? » |
| Lieux et Google Maps | « Une pharmacie près d'ici », « Où est le McDo le plus proche ? », « Emmène-moi à la gare », « Itinéraire vers Saint-Brieuc » |
| Réveils et minuteurs | « Réveille-moi à 7 h 30 », « Réveil à 6 h tous les jours », « Minuteur de 10 minutes », « Mes réveils » |
| Calculs | « 12 fois 4 », « 15 % de 80 », « Racine carrée de 144 » |
| Mémoire | « Je m'appelle Léa », « J'adore les chats », « J'ai 17 ans », « Mon chat s'appelle Mimi », « Retiens que mon anniversaire est le 12 mars » |

Des **raccourcis** au-dessus de la zone de texte permettent de tout faire d'un toucher. La météo et les lieux utilisent internet (Open-Meteo et OpenStreetMap, gratuits et sans clé).

Et aussi : **prises de nouvelles** par notification (tu réponds directement dans la notification), **micro** pour parler, **voix** pour que Canelle lise ses réponses, affichage **plein écran**.

Les réponses météo, batterie, heure, réveil, minuteur et calcul s'affichent avec une **carte animée** : Canelle se pousse sur le côté et la carte apparaît (soleil qui tourne, pluie qui tombe, éclairs, horloge qui avance, minuteur qui décompte…). Touche la carte pour la fermer.

## Météo précise

Paliers de température (température actuelle pour aujourd'hui, maximale pour les jours suivants) : moins de 0 °C gel · 0 à 10 °C très froid · 10 à 20 °C froid · 20 à 30 °C doux à chaud · 30 à 35 °C chaud · 35 °C et plus canicule.


« Maintenant » vient du modèle **AROME de Météo-France** (maille de 1,5 km, un point toutes les 15 minutes, recalculé chaque heure), via Open-Meteo, gratuit et sans clé, en France et autour. Le ciel affiché est corrigé avec la pluie qui tombe vraiment et la couverture nuageuse du moment. Les prévisions des jours suivants viennent du meilleur modèle Open-Meteo. La carte indique le ressenti, le vent, l'humidité, la source et l'heure des données. Si Météo-France ne répond pas, Canelle se rabat automatiquement sur Open-Meteo.

Quand l'appli est ouverte, la **position est actualisée toutes les 2 minutes** (et s'arrête en arrière-plan). Elle reste sur le téléphone : le nom de la ville n'est demandé à internet que quand tu demandes la météo ou un lieu.

## Batterie surveillée

- **Temps de charge mesuré** : Canelle note l'heure de chaque pourcent gagné et en déduit la vraie vitesse de ton téléphone avec ton chargeur, en tenant compte du ralentissement après 80 %. Sinon : estimation d'Android, puis calcul d'après le courant.
- **Alertes** : à 20 % (prévoir de charger), 15 % (brancher maintenant) et 5 % (le téléphone va s'éteindre). Appli ouverte : Canelle le dit tout de suite avec la carte batterie. Appli en arrière-plan : notification. Si Android a fermé l'appli, vérification toutes les 15 minutes.
- **Toutes les 30 secondes**, Canelle vérifie son état (batterie, branchement, charge pleine, heure). Ses annonces attendent qu'il ait fini de parler, et il ne répète pas la même chose d'affilée.

## Musique

- Canelle lance la musique dans **Spotify** ou **Deezer** (Menu → Musique pour choisir l'appli préférée). Deezer peut s'ouvrir sur les résultats de recherche au lieu de lancer directement la lecture.
- **Quand Spotify ou Deezer joue**, il met un **casque**, ferme les yeux, bouge et chante. Quand la lecture s'arrête (pause ou fin), il retire son casque. Les autres sons du téléphone (notifications, vidéos, jeux, ses propres bruitages) ne déclenchent rien.
- Pour ça, il faut l'autorisation **Menu → Musique → « Autoriser Canelle à voir Spotify et Deezer »** : sans elle, Android ne dit pas quelle application fait du son.
- Il suit le **rythme réel** si le téléphone le permet (autorisation micro, rien n'est enregistré), sinon un tempo régulier.
- La même autorisation donne le **titre en cours** et les boutons précédent / pause / suivant. Canelle ne lit ni ne garde aucune notification.

## Personnages

Raccourci **Personnages** (ou Menu → Personnages). Chacun a son dessin, sa personnalité, son cri et sa nourriture préférée ; tous gardent la même mémoire de l'utilisateur et toutes les expressions, skins et interactions.

| Personnage | Espèce | Personnalité |
|---|---|---|
| **Canelle** (principal) | panda roux | doux, joueur et curieux |
| **Filou** | raton laveur | malicieux et farceur, fan de biscuits |
| **Mochi** | chat | indépendant, un brin moqueur, câlin quand il veut |
| **Biscotte** | chien | enthousiaste, fidèle, toujours partant |
| **Nyx** | chauve-souris | mystérieuse, rêveuse, amoureuse de la nuit |

Le cerveau adopte la personnalité du personnage choisi. Changer de personnage lui redonne son nom par défaut.

## Jouer avec Canelle

**71 jeux et interactions**, tous accessibles depuis les raccourcis **Jeux** et **Interactions** en bas de l'écran, ou en les écrivant. Ils répondent tout de suite, sans passer par le cerveau.

**15 jeux** : devine le nombre, quiz (20 questions), vrai ou faux, calcul mental, jeu de mémoire (couleurs), test de réflexes, morpion, mots mélangés, devinettes, clique vite, cache-cache, boule magique (« boule magique : est-ce que… ? »), chifoumi, pile ou face, lancer de dé. Pendant un jeu, réponds dans la zone de texte ou sur la carte ; « stop » arrête la partie.

**53 interactions** : donner la patte, guitare, tambour, selfie (photo souvenir), compter jusqu'à 10, histoires, toupie, karaté, fantôme, imiter un animal, crêpe, bisous volants, trampoline, statue, confettis, boules de neige, cri du personnage, high five, check, danse, câlin, bisou, chatouille, manger un bambou, boire, bulles, feu d'artifice, arc-en-ciel, tour de magie, robot, moonwalk, yoga, balle, étoile filante, blague, compliment, anniversaire (gâteau et bougies à souffler), pirouette, saut, rawr, chanson, faire le mort, éternuer, grimace, muscles, pleurer, prendre la pose, roulade, applaudir, coucou, dodo, s'étirer, clin d'œil, « tu as froid ? ».

**Au toucher (3)** : boop sur le nez, caresse, petite tape.

- **Boop** : touche son nez. Cinq boops d'affilée et il voit des étoiles.
- **Caresse** : fais glisser ton doigt sur lui. Il ronronne et des cœurs s'envolent.
- **Toucher** : une petite tape ailleurs sur lui, et il réagit.
- **High five** : dis « high five » ou « check », une patte apparaît : tape dedans !
- **Jeux** : chifoumi (avec le score), pile ou face, lancer de dé, directement dans une carte.
- **25 expressions** : content, clin d'œil, amoureux, rigole, timide, fâché, fier, inquiet, triste, surpris, pensif, endormi, étourdi, bâille, soupçonneux, musique, émerveillé (yeux étoiles), ému, pleure, dégoûté, malade, gourmand, a froid, zen, neutre.
- **Quand tu ne fais rien**, il vit sa vie : il regarde autour de lui, chantonne, s'étire, bâille, se parle tout seul… et après 3 minutes, il s'endort. Touche l'écran pour le réveiller.
- **Après minuit**, il te rappelle gentiment que se coucher tard n'est pas bon et te conseille d'aller dormir (au plus une fois toutes les 30 minutes).

## Ce qu'il retient de toi

Canelle retient tout seul ce que tu dis de toi : ce que tu aimes ou n'aimes pas, ton âge, ta ville, ton travail ou tes études, le nom de ton animal ou de tes proches, tes préférés, ton anniversaire, tes sports… Tout apparaît dans **Menu → Ce qu'il retient de toi**, et le bouton × efface un souvenir. Si tu redonnes une information (ton âge, ta ville…), l'ancienne est remplacée.

## Langues

Canelle AI existe en **anglais** (langue par défaut pour les nouveaux utilisateurs) et en **français** (langue d'origine ; ceux qui utilisaient déjà l'appli la gardent). Bouton **globe** en haut de l'écran, ou Menu → Langue.

- L'anglais a son **patch complet** : `app/src/main/assets/i18n/en.js` contient la traduction de tous les textes de l'appli (interface, phrases du compagnon, jeux, réponses de la partie Android). Tout est immédiat et hors ligne.
- `i18n/keys.js` liste tous les textes (en français). Quand tu ajoutes ou modifies un texte en français, ajoute sa traduction dans `en.js`.
- Les phrases composées (« Ta batterie est à 64 % », « À Paris, il est 14 h 05 ») sont traduites morceau par morceau.
- Le cerveau IA répond directement dans la langue choisie ; la voix, le micro et les dates suivent la langue.
- Pour comprendre les commandes tapées librement en anglais (« what's the weather in Paris? »), l'appli peut télécharger une fois un petit pack de traduction Google (sur le téléphone, rien n'est envoyé).
- Les autres langues (espagnol, portugais, russe, chinois, japonais, arabe) ont été retirées ; si l'une d'elles avait été choisie, l'appli repasse en anglais.

## Visite guidée

Au premier lancement : choix de la langue (anglais présélectionné, la traduction se prépare pendant la suite), avertissement de confidentialité, apparition du compagnon, puis visite de l'écran (le compagnon, la zone de message, les raccourcis, l'onglet Pas, les pièces, le menu et le cerveau). Menu → Réglages → « Revoir la visite guidée » pour la relancer.

## Menu

Le menu s'ouvre sur une fiche du compagnon (dessin, nom, série de jours) et des tuiles : Personnages, Boutique, Mon cerveau, Pas, Souvenirs, Échanges, Musique, Réglages (et Outils dev pour l'équipe). Chaque tuile ouvre sa page ; la flèche ou le bouton retour d'Android ramène à l'accueil du menu.

## Démarrage

L'application s'appelle désormais **Canelle AI**. L'écran de chargement montre Canelle, le nom « Canelle AI » et la mention **Early Access**.

- À chaque ouverture : un **écran de chargement**, puis l'avertissement de confidentialité (case **Ne plus montrer** pour ne plus le voir).
- Au tout premier lancement, Canelle **se matérialise** : faisceau lumineux, pixels qui apparaissent un à un, étincelles.

## Pièces et boutique

Les skins s'achètent avec des **pièces** (le look classique reste gratuit ; le gérant et les développeurs ont tout). On en gagne :
- **bonus du jour** : 20 pièces à la première ouverture, +5 par jour d'affilée (jusqu'à 50) ;
- **jeux** : 5 par partie, 15 par victoire ; **interactions** : 1 pièce (au total 200 pièces par jour au maximum en jouant) ;
- **marche** : 10 pièces tous les 1 000 pas, à récupérer dans l'onglet « Pas » (100 par jour au maximum).

Prix : 50 (Neige, Minuit, Menthe, Chocolat, Océan, Forêt), 120 (Sakura, Panda, Bonbon, Chrome, Aurore, Magma), 200 (Doré, Cyber, Pirate, Galaxie), 300 (Fantôme, Citrouille, Noël). Chacun démarre avec 100 pièces, et le skin déjà porté avant la mise à jour reste acquis. Les pièces sont comptées par la partie Android de l'appli (pas par la page), et restent sur le téléphone.

## Compteur de pas (onglet « Pas »)

En haut de l'écran, deux onglets : le compagnon, et **Pas**. Les pas viennent de **Health Connect**, le service d'Android qui rassemble ceux de Google Fit, Samsung Health, Fitbit… et, depuis Android 14, ceux comptés par le téléphone lui-même (pas besoin d'autre appli). Google Fit est remplacé par Health Connect (ses API s'arrêtent fin 2026).
- Anneau de progression vers l'objectif du jour (réglable), distance et calories estimées, moyenne et graphique des 7 derniers jours.
- Ton compagnon commente ta progression.
- Au premier passage : bouton pour autoriser la lecture des pas (ou installer Health Connect sur Android 12 et 13). Canelle AI ne lit que le nombre de pas ; rien ne quitte le téléphone.

## Skins (pour tout le monde)

Raccourci **Skins** (ou Menu → Skins) : **20 apparences**, pour tous les personnages, avec un aperçu : Classique, Neige, Minuit, Doré (couronne), Sakura (fleur), Menthe, Cyber (lunettes néon), Pirate (cache-œil), Fantôme (translucide et flottant), Magma, Galaxie (pelage étoilé), Panda, Chocolat, Océan, Citrouille (chapeau de sorcière), Noël (bonnet), Bonbon, Chrome, Forêt et Aurore.

## Code d'accès

Menu → Réglages → **Code d'accès**, réservé à l'équipe. Rangs : développeur, gérant, fille du patron (confirmée par son prénom, avec la même cinématique que le gérant) et VIP. Le rang **VIP est en développement** : ses avantages vont changer régulièrement. Un code développeur ou gérant donne un badge dans l'en-tête, un accueil personnalisé et une section **Outils développeur** pour tester chaque animation (matérialisation, cartes, expressions, rappel de nuit, sommeil…). Les codes ne sont pas écrits en clair dans l'application : seule leur empreinte est vérifiée. « Retirer mon rang » annule.

Avec le code gérant, Canelle lance une **cinématique** : bandes noires, il se penche vers toi en plissant les yeux et la musique `question.mp3` démarre (elle s'arrête dès que tu réponds). Tu écris ton nom, `verification.mp3` se joue pendant qu'il vérifie. Bon nom : `verifie.mp3`, et il salue le gérant. Mauvais nom : il s'énerve, te traite de menteur et **ferme l'application**. Au 2e mensonge, il prévient. Au **3e mensonge** (le compte continue même après avoir relancé l'appli), il **refuse de te parler pendant 24 heures** : il boude, regarde ailleurs, et on ne peut plus lui écrire ni ouvrir le menu jusqu'à la fin de la punition (le temps restant s'affiche). Le bon nom remet le compteur à zéro. Reculer l'heure du téléphone ne lève pas la punition ; seul l'effacement des données de l'appli la supprime. Le bouton retour pendant la question annule la cinématique. Les sons sont dans `app/src/main/assets/sounds/` ; ils suivent le réglage du son (rien en mode muet).

## Mettre à jour ton dépôt GitHub

1. Sur la page de ton dépôt : **Add file → Upload files**.
2. Dézippe `canelle-android.zip` sur ton ordinateur, ouvre le dossier, et **glisse tout son contenu** (les dossiers `app`, `gradle`, `.github` et les fichiers) dans la page. Les anciens fichiers seront remplacés.
   - Le dossier `.github` est caché sur certains ordinateurs : sur Windows, active « Afficher les éléments masqués » ; sur Mac, appuie sur Cmd + Maj + point.
3. Clique sur **Commit changes**.
4. Onglet **Actions** : la construction « Canelle APK » démarre toute seule (environ 5 à 10 minutes). Quand le rond devient vert, ouvre-la et télécharge l'APK en bas de la page, dans **Artifacts**.

## Installer

Si tu as déjà installé la version 0.2 (celle avec le cerveau local), installe simplement celle-ci **par-dessus** : tu gardes tes souvenirs et le cerveau déjà téléchargé.

Si tu viens d'une version plus ancienne (0.1, avec la clé API), désinstalle-la **une seule fois** avant : elle avait une signature différente. Depuis la 0.2, la signature est fixe (`app/canelle.keystore`).

Ensuite, ouvre l'APK sur le téléphone et accepte l'installation depuis cette source si Android le demande.

## Choisir son cerveau

Chacun peut choisir le cerveau qu'il veut dans « Mon cerveau », même si le téléphone n'est pas assez puissant. Chaque cerveau est marqué **Conseillé**, **Juste** ou **Risqué** selon la mémoire du téléphone ; pour « Juste » ou « Risqué », une fenêtre prévient que l'application risque de planter.

**Anti-plantage en boucle** : si un cerveau choisi malgré l'avertissement fait planter l'appli, il est **désactivé dès le lancement suivant**. Pour un cerveau conseillé, Canelle retente une fois sur le processeur si la puce graphique a planté, puis le désactive s'il replante. Dans les deux cas, « Mon cerveau » explique ce qui s'est passé et propose de réessayer ou de passer à un cerveau plus léger.

## Un cerveau adapté à chaque téléphone

Au premier lancement, Canelle regarde la mémoire du téléphone et choisit son cerveau tout seul :

| Téléphone | Cerveau |
|---|---|
| 12 Go de mémoire ou plus | Gemma 4 E4B (3,7 Go), le plus intelligent |
| 8 Go | Gemma 4 E2B (2,6 Go) par défaut ; le E4B peut être choisi dans « Mon cerveau » |
| 6 Go | Gemma 4 E2B (2,6 Go), le meilleur compromis |
| Moins de 6 Go | Pas de cerveau : Canelle l'explique, et tout le reste fonctionne (batterie, météo, réveils, musique, jeux…) |

- On peut changer de cerveau dans « Mon cerveau » (« Passer au cerveau E4B » / « Revenir au cerveau E2B ») : l'ancien reste utilisable pendant le téléchargement du nouveau, puis il est supprimé.
- Chaque cerveau a son propre garde-fou : s'il fait fermer l'appli, Canelle passe au niveau plus léger (voir plus bas), et un E4B en pause de sécurité propose de revenir au E2B.
- Les seuils sont réglables dans `LocalModel.kt` (`RAM_E4B_AUTO`, `RAM_E4B_POSSIBLE`, `RAM_E2B_MIN`).
- Le gérant et les développeurs peuvent forcer le téléchargement sur un téléphone jugé trop faible, pour tester.
- Les deux modèles sont sous licence Apache 2.0 (Google, via LiteRT).

## Si l'appli se ferme toute seule (cerveau trop lourd)

Le cerveau pèse 2,6 Go. Sur certains téléphones, surtout sous Android 16 et 17 (qui impose désormais une limite de mémoire à chaque appli), son chargement peut faire fermer l'appli. Canelle se protège tout seul :

1. Au lancement suivant, il sait que le dernier chargement a échoué et passe au niveau plus léger : puce graphique → processeur → mode économe (conversations plus courtes, moins de mémoire).
2. S'il plante encore en mode économe, il se met en **pause de sécurité** : il ne se charge plus tout seul, et « Mon cerveau » propose « Réessayer en mode économe ».
3. Le cerveau ne se charge plus automatiquement tant qu'il n'a jamais démarré avec succès : il se réveille à la première question.
4. Il rend sa mémoire une minute après que l'appli passe en arrière-plan.
5. « Mon cerveau » affiche le rapport d'Android sur le dernier arrêt (raison, mémoire utilisée, modèle du téléphone), avec un bouton **Copier le rapport**. Il reste sur le téléphone.

## Publier sur Google Play (AAB)

Google Play demande un **AAB** (Android App Bundle) et, depuis le 31 août 2026, des applis qui visent **Android 16 (API 36)** : c'est le cas.

1. Dans GitHub → **Actions**, lance la construction. Elle produit deux fichiers : `Canelle-apk` (installation directe) et **`Canelle-aab-google-play`** (le fichier `app-release.aab` à envoyer).
2. Dans la Play Console : Tests → **Test fermé** → Créer une version → envoie `app-release.aab`. Accepte **Play App Signing** (Google garde la clé de signature finale ; la clé du projet sert seulement de clé d'envoi).
3. À chaque nouvel envoi, le `versionCode` (dans `app/build.gradle.kts`) doit être plus grand que le précédent.

**Clé d'envoi.** Par défaut, l'AAB est signé avec `app/canelle.keystore` (mot de passe dans `build.gradle.kts`). Si ton dépôt GitHub est **public**, rends-le privé (Settings → Danger Zone → Change visibility), ou range la clé dans les secrets du dépôt (Settings → Secrets and variables → Actions) : `UPLOAD_KEYSTORE_BASE64` (le fichier .keystore en base64), `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`. Garde toujours la même clé d'envoi : en changer demande une réinitialisation auprès de Google.

**À remplir dans la Play Console** (l'appli utilise des autorisations sensibles) :
- une **politique de confidentialité** (URL publique, par exemple une page du site Cloudflare) ;
- la **sécurité des données** : rien n'est collecté par l'appli elle-même, mais la position est envoyée à Open-Meteo et OpenStreetMap quand on demande la météo ou un lieu, et la dictée vocale passe par le service de reconnaissance vocale du téléphone (Google) ;
- la déclaration **Health Connect** (lecture des pas), celle des **autorisations de localisation** et du **micro** ;
- le **nom du paquet** `fr.canelle.compagnon` est définitif une fois l'appli créée.

Les testeurs qui avaient installé l'APK directement devront le **désinstaller une fois** avant d'installer la version du Play Store (signature différente).

## Si la construction échoue

Ouvre la construction en rouge dans l'onglet **Actions**, clique sur l'étape en erreur et copie les lignes rouges (souvent celles qui commencent par `e:` ou `What went wrong`). Elles suffisent pour corriger.

## Vie privée

- Canelle ne collecte aucune information personnelle. Les discussions, les souvenirs et les réglages restent sur le téléphone.
- Le cerveau tourne hors ligne : aucun message n'est envoyé à un serveur.
- Seules la météo et la recherche de lieux utilisent internet : ta position est alors envoyée à Open-Meteo et à OpenStreetMap.
- La position actualisée toutes les 2 minutes, le titre de la musique et la mesure du rythme restent sur le téléphone.

## Détails techniques

- Kotlin 2.4, Android Gradle Plugin 8.13, Gradle 8.14.3, compileSdk 36, minSdk 31.
- IA locale : `com.google.ai.edge.litertlm:litertlm-android:0.17.1`, modèle `gemma-4-E2B-it.litertlm` (litert-community sur Hugging Face).
- Écran bloqué en portrait. Optimisé : Canelle est dessiné en une seule image, une seule minuterie pour la page, et tout se met en pause en arrière-plan.
- Interface : page web locale (`app/src/main/assets/index.html`) dans une WebView, reliée au code Kotlin par un pont JavaScript.
