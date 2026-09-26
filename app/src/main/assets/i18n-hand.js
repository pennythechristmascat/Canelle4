// Traductions faites à la main (interface, visite guidée, confidentialité).
// Le reste des textes est traduit sur le téléphone, d'avance, au moment où l'on choisit la langue.
(function () {
  const KEYS = [
    "Pas", "Échanges", "Menu", "Fermer", "Boutique", "Personnages", "Mon cerveau", "Souvenirs", "Musique", "Réglages",
    "Langue", "Outils dev", "Récupérer", "Valider", "Enregistrer", "Acheter", "Annuler", "Pas maintenant", "D'accord", "Porté",
    "À toi", "Dé", "Lance un dé", "Chifoumi", "Morpion", "Pile ou face", "Mémoire", "Réflexes", "Quiz", "Jeux",
    "Interactions", "Autour de moi", "Batterie", "Météo", "Heure", "Réveil", "Minuteur", "Calcul", "Skins", "bips",
    "voix", "muet", "← Retour", "Maintenant", "Prévisions", "min", "max", "pluie", "ressenti", "vent",
    "humidité", "pièces", "Objectif du jour", "pas aujourd'hui", "Choisir", "Conseillé", "Juste", "Risqué", "J'ai compris", "Ne plus montrer",
    "Tout oublier", "Code d'accès", "Nom de ton compagnon", "Zone sensible", "Gagner des pièces", "Choisir ton compagnon", "Skins et pièces", "L'IA de ton compagnon", "Ton compteur du jour", "Ce qu'il sait de toi",
    "Vos derniers messages", "Spotify et Deezer", "English, français", "Notifications, code, nom", "Tests et animations", "Devine le nombre", "Vrai ou faux", "Calcul mental", "Mots mélangés", "Clique vite",
    "Cache-cache", "Boule magique", "Devinette", "Tester une notification", "Revoir la visite guidée"
  ];
  const T = {
    en: ["Steps", "Chats", "Menu", "Close", "Shop", "Characters", "My brain", "Memories", "Music", "Settings",
      "Language", "Dev tools", "Collect", "Confirm", "Save", "Buy", "Cancel", "Not now", "OK", "Worn",
      "Yours", "Dice", "Roll a die", "Rock paper scissors", "Tic-tac-toe", "Heads or tails", "Memory", "Reflexes", "Quiz", "Games",
      "Interactions", "Around me", "Battery", "Weather", "Time", "Alarm", "Timer", "Calculator", "Skins", "beeps",
      "voice", "mute", "← Back", "Now", "Forecast", "min", "max", "rain", "feels like", "wind",
      "humidity", "coins", "Daily goal", "steps today", "Choose", "Recommended", "Tight", "Risky", "Got it", "Don't show again",
      "Forget everything", "Access code", "Your companion's name", "Danger zone", "Earn coins", "Choose your companion", "Skins and coins", "Your companion's AI", "Today's step count", "What it knows about you",
      "Your latest messages", "Spotify and Deezer", "English, French", "Notifications, code, name", "Tests and animations", "Guess the number", "True or false", "Mental math", "Word scramble", "Tap fast",
      "Hide and seek", "Magic 8-ball", "Riddle", "Test a notification", "Replay the guided tour"]
  };
  const HAND = {};
  Object.keys(T).forEach(l => { HAND[l] = {}; KEYS.forEach((k, i) => { HAND[l][k] = T[l][i]; }); });
  window.I18N_HAND = HAND;
  // Langues qui ont leur patch complet (assets/i18n/<langue>.js). Les autres passent par le traducteur du téléphone.
  window.I18N_PATCHES = ["en"];

  // Visite guidée et confidentialité, dans les 8 langues (affichées avant le téléchargement du pack de langue).
  window.I18N_TOUR = {
    fr: {
      next: "Suivant", skip: "Passer", go: "C'est parti !", pick: "Choisis ta langue", dl: "Préparation de la langue : {}%", ready: "Langue prête !", offline: "Pas de connexion : la langue sera préparée dès que tu seras en ligne.",
      steps: [
        ["Bienvenue dans Canelle AI !", "Ton compagnon de poche, en Early Access. Choisis d'abord ta langue."],
        ["Voici ton compagnon", "Touche-le, tapote son nez (boop !) ou caresse-le en glissant ton doigt dessus."],
        ["Parle-lui", "Écris ou dicte ici. Il comprend la météo, les réveils, la musique et les jeux, et il bavarde de tout avec son cerveau."],
        ["Raccourcis", "Jeux, interactions, personnages, skins : tout est à portée de doigt."],
        ["Tes pas", "Passe de ton compagnon à ton compteur de pas."],
        ["Pièces", "Gagne des pièces en jouant, en marchant et en revenant chaque jour, puis dépense-les dans la boutique de skins."],
        ["Le menu", "Personnages, boutique, cerveau, souvenirs, réglages et langue : tout se trouve ici."],
        ["Son cerveau", "Pour discuter de tout, télécharge son cerveau dans « Mon cerveau ». Tout reste sur ton téléphone."]
      ],
      privacy: ["Ta vie privée est protégée", "Canelle AI ne collecte aucune information personnelle.", "Tout ce que tu lui dis, ce qu'il retient de toi et tes réglages restent uniquement sur ce téléphone. Son cerveau fonctionne sans internet : tes messages ne sont envoyés à personne.", "Seules la météo et la recherche de lieux utilisent internet, et seulement quand tu les demandes : ta position est alors envoyée à Open-Meteo et à OpenStreetMap.", "Ne plus montrer", "J'ai compris"]
    },
    en: {
      next: "Next", skip: "Skip", go: "Let's go!", pick: "Choose your language", dl: "Preparing the language: {}%", ready: "Language ready!", offline: "No connection: the language will be prepared as soon as you're online.",
      steps: [
        ["Welcome to Canelle AI!", "Your pocket companion, in Early Access. First, choose your language."],
        ["Meet your companion", "Tap him, boop his nose, or stroke him by sliding your finger over him."],
        ["Talk to him", "Type or speak here. He understands weather, alarms, music and games, and chats about anything with his brain."],
        ["Shortcuts", "Games, interactions, characters, skins: everything is at your fingertips."],
        ["Your steps", "Switch between your companion and your step counter."],
        ["Coins", "Earn coins by playing, walking and coming back every day, then spend them in the skin shop."],
        ["The menu", "Characters, shop, brain, memories, settings and language: it's all here."],
        ["His brain", "To chat about anything, download his brain in \"My brain\". Everything stays on your phone."]
      ],
      privacy: ["Your privacy is protected", "Canelle AI doesn't collect any personal information.", "Everything you tell him, what he remembers about you and your settings stay only on this phone. His brain works offline: your messages are never sent to anyone.", "Only the weather and place search use the internet, and only when you ask: your location is then sent to Open-Meteo and OpenStreetMap.", "Don't show again", "Got it"]
    }
  };
})();
