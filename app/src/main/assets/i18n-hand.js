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
    "Vos derniers messages", "Spotify et Deezer", "8 langues", "Notifications, code, nom", "Tests et animations", "Devine le nombre", "Vrai ou faux", "Calcul mental", "Mots mélangés", "Clique vite",
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
      "Your latest messages", "Spotify and Deezer", "8 languages", "Notifications, code, name", "Tests and animations", "Guess the number", "True or false", "Mental math", "Word scramble", "Tap fast",
      "Hide and seek", "Magic 8-ball", "Riddle", "Test a notification", "Replay the guided tour"],
    es: ["Pasos", "Conversaciones", "Menú", "Cerrar", "Tienda", "Personajes", "Mi cerebro", "Recuerdos", "Música", "Ajustes",
      "Idioma", "Herramientas dev", "Recoger", "Validar", "Guardar", "Comprar", "Cancelar", "Ahora no", "De acuerdo", "Puesto",
      "Tuyo", "Dado", "Tira un dado", "Piedra, papel o tijera", "Tres en raya", "Cara o cruz", "Memoria", "Reflejos", "Quiz", "Juegos",
      "Interacciones", "A mi alrededor", "Batería", "Tiempo", "Hora", "Alarma", "Temporizador", "Cálculo", "Skins", "pitidos",
      "voz", "silencio", "← Volver", "Ahora", "Previsión", "mín", "máx", "lluvia", "sensación", "viento",
      "humedad", "monedas", "Objetivo del día", "pasos hoy", "Elegir", "Recomendado", "Justo", "Arriesgado", "Entendido", "No volver a mostrar",
      "Olvidarlo todo", "Código de acceso", "Nombre de tu compañero", "Zona sensible", "Ganar monedas", "Elige tu compañero", "Skins y monedas", "La IA de tu compañero", "Tu contador del día", "Lo que sabe de ti",
      "Tus últimos mensajes", "Spotify y Deezer", "8 idiomas", "Notificaciones, código, nombre", "Pruebas y animaciones", "Adivina el número", "Verdadero o falso", "Cálculo mental", "Palabras revueltas", "Toca rápido",
      "Escondite", "Bola mágica", "Adivinanza", "Probar una notificación", "Volver a ver la visita guiada"],
    pt: ["Passos", "Conversas", "Menu", "Fechar", "Loja", "Personagens", "Meu cérebro", "Memórias", "Música", "Configurações",
      "Idioma", "Ferramentas dev", "Coletar", "Confirmar", "Salvar", "Comprar", "Cancelar", "Agora não", "OK", "Em uso",
      "Seu", "Dado", "Jogue um dado", "Pedra, papel e tesoura", "Jogo da velha", "Cara ou coroa", "Memória", "Reflexos", "Quiz", "Jogos",
      "Interações", "Perto de mim", "Bateria", "Clima", "Hora", "Alarme", "Temporizador", "Cálculo", "Skins", "bipes",
      "voz", "mudo", "← Voltar", "Agora", "Previsão", "mín", "máx", "chuva", "sensação", "vento",
      "umidade", "moedas", "Meta do dia", "passos hoje", "Escolher", "Recomendado", "No limite", "Arriscado", "Entendi", "Não mostrar de novo",
      "Esquecer tudo", "Código de acesso", "Nome do seu companheiro", "Zona sensível", "Ganhar moedas", "Escolha seu companheiro", "Skins e moedas", "A IA do seu companheiro", "Seu contador do dia", "O que ele sabe sobre você",
      "Suas últimas mensagens", "Spotify e Deezer", "8 idiomas", "Notificações, código, nome", "Testes e animações", "Adivinhe o número", "Verdadeiro ou falso", "Cálculo mental", "Palavras embaralhadas", "Toque rápido",
      "Esconde-esconde", "Bola mágica", "Charada", "Testar uma notificação", "Rever a visita guiada"],
    ru: ["Шаги", "Переписка", "Меню", "Закрыть", "Магазин", "Персонажи", "Мой мозг", "Воспоминания", "Музыка", "Настройки",
      "Язык", "Инструменты", "Забрать", "Подтвердить", "Сохранить", "Купить", "Отмена", "Не сейчас", "Хорошо", "Надето",
      "Твоё", "Кубик", "Брось кубик", "Камень, ножницы, бумага", "Крестики-нолики", "Орёл или решка", "Память", "Реакция", "Викторина", "Игры",
      "Взаимодействия", "Рядом со мной", "Батарея", "Погода", "Время", "Будильник", "Таймер", "Калькулятор", "Скины", "бипы",
      "голос", "без звука", "← Назад", "Сейчас", "Прогноз", "мин", "макс", "дождь", "ощущается", "ветер",
      "влажность", "монеты", "Цель дня", "шагов сегодня", "Выбрать", "Рекомендуется", "На пределе", "Рискованно", "Понятно", "Больше не показывать",
      "Забыть всё", "Код доступа", "Имя твоего компаньона", "Опасная зона", "Как заработать монеты", "Выбери компаньона", "Скины и монеты", "ИИ твоего компаньона", "Шагомер на сегодня", "Что он о тебе знает",
      "Твои последние сообщения", "Spotify и Deezer", "8 языков", "Уведомления, код, имя", "Тесты и анимации", "Угадай число", "Правда или ложь", "Устный счёт", "Перепутанные слова", "Жми быстрее",
      "Прятки", "Магический шар", "Загадка", "Проверить уведомление", "Снова пройти обзор"],
    zh: ["步数", "聊天记录", "菜单", "关闭", "商店", "角色", "我的大脑", "记忆", "音乐", "设置",
      "语言", "开发工具", "领取", "确认", "保存", "购买", "取消", "以后再说", "好的", "已穿戴",
      "已拥有", "骰子", "掷骰子", "石头剪刀布", "井字棋", "猜硬币", "记忆", "反应", "问答", "游戏",
      "互动", "我的周边", "电量", "天气", "时间", "闹钟", "计时器", "计算", "皮肤", "哔声",
      "语音", "静音", "← 返回", "现在", "预报", "最低", "最高", "降雨", "体感", "风",
      "湿度", "金币", "每日目标", "今日步数", "选择", "推荐", "勉强", "有风险", "知道了", "不再显示",
      "全部忘记", "访问码", "伙伴的名字", "危险区域", "赚取金币", "选择你的伙伴", "皮肤和金币", "伙伴的 AI", "今日计步", "它对你的了解",
      "最近的消息", "Spotify 和 Deezer", "8 种语言", "通知、访问码、名字", "测试和动画", "猜数字", "对还是错", "心算", "字母乱序", "快速点击",
      "捉迷藏", "魔法八号球", "谜语", "测试通知", "重新查看引导"],
    ja: ["歩数", "会話", "メニュー", "閉じる", "ショップ", "キャラクター", "わたしの頭脳", "思い出", "音楽", "設定",
      "言語", "開発ツール", "受け取る", "確定", "保存", "購入", "キャンセル", "あとで", "OK", "着用中",
      "所持", "サイコロ", "サイコロを振る", "じゃんけん", "三目並べ", "コイントス", "記憶", "反射神経", "クイズ", "ゲーム",
      "ふれあい", "周辺", "バッテリー", "天気", "時刻", "アラーム", "タイマー", "計算", "スキン", "ビープ",
      "音声", "ミュート", "← 戻る", "現在", "予報", "最低", "最高", "降水", "体感", "風",
      "湿度", "コイン", "今日の目標", "今日の歩数", "選ぶ", "おすすめ", "ギリギリ", "危険", "わかった", "今後表示しない",
      "すべて忘れる", "アクセスコード", "相棒の名前", "危険ゾーン", "コインを稼ぐ", "相棒を選ぶ", "スキンとコイン", "相棒のAI", "今日の歩数計", "あなたについて知っていること",
      "最近のメッセージ", "Spotify と Deezer", "8 言語", "通知・コード・名前", "テストとアニメーション", "数当て", "○×クイズ", "暗算", "文字並べ替え", "連打",
      "かくれんぼ", "魔法の8ボール", "なぞなぞ", "通知をテスト", "ガイドをもう一度見る"],
    ar: ["الخطوات", "المحادثات", "القائمة", "إغلاق", "المتجر", "الشخصيات", "دماغي", "الذكريات", "الموسيقى", "الإعدادات",
      "اللغة", "أدوات المطوّر", "استلام", "تأكيد", "حفظ", "شراء", "إلغاء", "ليس الآن", "حسنًا", "مُرتدى",
      "لك", "النرد", "ارمِ النرد", "حجر ورقة مقص", "إكس أو", "ملك أم كتابة", "الذاكرة", "ردّ الفعل", "مسابقة", "الألعاب",
      "التفاعلات", "حولي", "البطارية", "الطقس", "الساعة", "المنبّه", "المؤقّت", "الحساب", "المظاهر", "صفير",
      "صوت", "صامت", "رجوع ←", "الآن", "التوقعات", "الصغرى", "العظمى", "المطر", "الإحساس", "الرياح",
      "الرطوبة", "عملات", "هدف اليوم", "خطوات اليوم", "اختيار", "موصى به", "على الحدّ", "محفوف بالمخاطر", "فهمت", "لا تُظهر مجددًا",
      "نسيان كل شيء", "رمز الدخول", "اسم رفيقك", "منطقة حساسة", "كسب العملات", "اختر رفيقك", "المظاهر والعملات", "ذكاء رفيقك", "عدّاد اليوم", "ما يعرفه عنك",
      "آخر رسائلك", "Spotify وDeezer", "8 لغات", "الإشعارات، الرمز، الاسم", "اختبارات ورسوم متحركة", "خمّن الرقم", "صح أم خطأ", "الحساب الذهني", "كلمات مبعثرة", "انقر بسرعة",
      "الغميضة", "الكرة السحرية", "لغز", "تجربة إشعار", "إعادة الجولة الإرشادية"]
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
    },
    es: {
      next: "Siguiente", skip: "Saltar", go: "¡Vamos!", pick: "Elige tu idioma", dl: "Preparando el idioma: {}%", ready: "¡Idioma listo!", offline: "Sin conexión: el idioma se preparará en cuanto estés en línea.",
      steps: [
        ["¡Bienvenido a Canelle AI!", "Tu compañero de bolsillo, en Early Access. Primero, elige tu idioma."],
        ["Este es tu compañero", "Tócalo, toca su nariz (¡boop!) o acarícialo deslizando el dedo."],
        ["Háblale", "Escribe o dicta aquí. Entiende el tiempo, las alarmas, la música y los juegos, y charla de todo con su cerebro."],
        ["Atajos", "Juegos, interacciones, personajes, skins: todo al alcance de tu dedo."],
        ["Tus pasos", "Cambia entre tu compañero y tu contador de pasos."],
        ["Monedas", "Gana monedas jugando, caminando y volviendo cada día, y gástalas en la tienda de skins."],
        ["El menú", "Personajes, tienda, cerebro, recuerdos, ajustes e idioma: todo está aquí."],
        ["Su cerebro", "Para charlar de todo, descarga su cerebro en «Mi cerebro». Todo se queda en tu teléfono."]
      ],
      privacy: ["Tu privacidad está protegida", "Canelle AI no recopila ninguna información personal.", "Todo lo que le dices, lo que recuerda de ti y tus ajustes se quedan solo en este teléfono. Su cerebro funciona sin internet: tus mensajes nunca se envían a nadie.", "Solo el tiempo y la búsqueda de lugares usan internet, y solo cuando los pides: tu ubicación se envía entonces a Open-Meteo y OpenStreetMap.", "No volver a mostrar", "Entendido"]
    },
    pt: {
      next: "Próximo", skip: "Pular", go: "Vamos lá!", pick: "Escolha seu idioma", dl: "Preparando o idioma: {}%", ready: "Idioma pronto!", offline: "Sem conexão: o idioma será preparado assim que você estiver on-line.",
      steps: [
        ["Bem-vindo ao Canelle AI!", "Seu companheiro de bolso, em Early Access. Primeiro, escolha seu idioma."],
        ["Este é seu companheiro", "Toque nele, toque no nariz (boop!) ou faça carinho deslizando o dedo."],
        ["Fale com ele", "Escreva ou dite aqui. Ele entende clima, alarmes, música e jogos, e conversa sobre tudo com o cérebro dele."],
        ["Atalhos", "Jogos, interações, personagens, skins: tudo ao alcance do dedo."],
        ["Seus passos", "Alterne entre seu companheiro e seu contador de passos."],
        ["Moedas", "Ganhe moedas jogando, caminhando e voltando todo dia, e gaste-as na loja de skins."],
        ["O menu", "Personagens, loja, cérebro, memórias, configurações e idioma: está tudo aqui."],
        ["O cérebro dele", "Para conversar sobre tudo, baixe o cérebro dele em \"Meu cérebro\". Tudo fica no seu celular."]
      ],
      privacy: ["Sua privacidade está protegida", "O Canelle AI não coleta nenhuma informação pessoal.", "Tudo o que você diz a ele, o que ele lembra de você e suas configurações ficam apenas neste celular. O cérebro dele funciona sem internet: suas mensagens nunca são enviadas a ninguém.", "Só o clima e a busca de lugares usam a internet, e só quando você pede: sua localização é então enviada ao Open-Meteo e ao OpenStreetMap.", "Não mostrar de novo", "Entendi"]
    },
    ru: {
      next: "Далее", skip: "Пропустить", go: "Поехали!", pick: "Выбери язык", dl: "Подготовка языка: {}%", ready: "Язык готов!", offline: "Нет подключения: язык подготовится, как только ты будешь в сети.",
      steps: [
        ["Добро пожаловать в Canelle AI!", "Твой карманный компаньон в раннем доступе. Сначала выбери язык."],
        ["Это твой компаньон", "Коснись его, нажми на нос (буп!) или погладь, проведя пальцем."],
        ["Поговори с ним", "Пиши или говори здесь. Он понимает погоду, будильники, музыку и игры и болтает обо всём с помощью своего мозга."],
        ["Быстрые кнопки", "Игры, взаимодействия, персонажи, скины — всё под рукой."],
        ["Твои шаги", "Переключайся между компаньоном и шагомером."],
        ["Монеты", "Зарабатывай монеты, играя, гуляя и возвращаясь каждый день, и трать их в магазине скинов."],
        ["Меню", "Персонажи, магазин, мозг, воспоминания, настройки и язык — всё здесь."],
        ["Его мозг", "Чтобы болтать обо всём, скачай его мозг в разделе «Мой мозг». Всё остаётся на твоём телефоне."]
      ],
      privacy: ["Твоя конфиденциальность защищена", "Canelle AI не собирает никакой личной информации.", "Всё, что ты ему говоришь, что он о тебе помнит, и твои настройки хранятся только на этом телефоне. Его мозг работает без интернета: твои сообщения никому не отправляются.", "Интернет нужен только для погоды и поиска мест, и только когда ты их просишь: тогда твоё местоположение отправляется в Open-Meteo и OpenStreetMap.", "Больше не показывать", "Понятно"]
    },
    zh: {
      next: "下一步", skip: "跳过", go: "开始吧！", pick: "选择你的语言", dl: "正在准备语言：{}%", ready: "语言已就绪！", offline: "没有网络：联网后会自动准备语言。",
      steps: [
        ["欢迎来到 Canelle AI！", "你的口袋伙伴，抢先体验版。先选择你的语言吧。"],
        ["这是你的伙伴", "点点它，戳戳它的鼻子（啵！），或用手指滑动来摸摸它。"],
        ["和它说话", "在这里输入或语音输入。它懂天气、闹钟、音乐和游戏，还能用它的大脑和你聊任何话题。"],
        ["快捷方式", "游戏、互动、角色、皮肤：一触即达。"],
        ["你的步数", "在伙伴和计步器之间切换。"],
        ["金币", "玩游戏、走路、每天回来都能赚金币，然后在皮肤商店里花掉。"],
        ["菜单", "角色、商店、大脑、记忆、设置和语言：都在这里。"],
        ["它的大脑", "想和它聊任何话题，请在“我的大脑”中下载它的大脑。所有内容都留在你的手机上。"]
      ],
      privacy: ["你的隐私受到保护", "Canelle AI 不收集任何个人信息。", "你对它说的话、它记住的关于你的事以及你的设置都只保存在这部手机上。它的大脑离线运行：你的消息不会发送给任何人。", "只有天气和地点搜索会使用网络，而且只在你请求时：那时你的位置会发送给 Open-Meteo 和 OpenStreetMap。", "不再显示", "知道了"]
    },
    ja: {
      next: "次へ", skip: "スキップ", go: "はじめよう！", pick: "言語を選んでね", dl: "言語を準備中：{}%", ready: "言語の準備ができました！", offline: "接続がありません：オンラインになったら言語を準備します。",
      steps: [
        ["Canelle AI へようこそ！", "ポケットの相棒（アーリーアクセス版）。まずは言語を選んでね。"],
        ["これがあなたの相棒", "タップしたり、鼻をつついたり（ブープ！）、指でなでたりしてみてね。"],
        ["話しかけよう", "ここに入力するか話しかけてね。天気、アラーム、音楽、ゲームがわかるし、頭脳を使って何でもおしゃべりできるよ。"],
        ["ショートカット", "ゲーム、ふれあい、キャラクター、スキン：すべて指先ひとつ。"],
        ["あなたの歩数", "相棒と歩数計を切り替えられます。"],
        ["コイン", "遊んだり、歩いたり、毎日来たりしてコインを集めて、スキンショップで使おう。"],
        ["メニュー", "キャラクター、ショップ、頭脳、思い出、設定、言語：ぜんぶここにあります。"],
        ["相棒の頭脳", "何でも話すには「わたしの頭脳」で頭脳をダウンロードしてね。すべてあなたのスマホの中に残ります。"]
      ],
      privacy: ["あなたのプライバシーは守られています", "Canelle AI は個人情報を一切収集しません。", "あなたが話したこと、覚えていること、設定はすべてこのスマホの中だけに保存されます。頭脳はオフラインで動くので、メッセージが誰かに送られることはありません。", "インターネットを使うのは天気と場所の検索だけで、あなたが頼んだときだけです。そのとき位置情報が Open-Meteo と OpenStreetMap に送られます。", "今後表示しない", "わかった"]
    },
    ar: {
      next: "التالي", skip: "تخطٍّ", go: "هيا بنا!", pick: "اختر لغتك", dl: "جارٍ تجهيز اللغة: {}%", ready: "اللغة جاهزة!", offline: "لا يوجد اتصال: ستُجهَّز اللغة بمجرد اتصالك بالإنترنت.",
      steps: [
        ["مرحبًا بك في Canelle AI!", "رفيقك في الجيب، في مرحلة الوصول المبكر. اختر لغتك أولًا."],
        ["هذا رفيقك", "المسه، أو انقر على أنفه (بوب!)، أو داعبه بتمرير إصبعك عليه."],
        ["تحدّث إليه", "اكتب أو تحدّث هنا. إنه يفهم الطقس والمنبّهات والموسيقى والألعاب، ويتحدث عن أي شيء بفضل دماغه."],
        ["اختصارات", "الألعاب والتفاعلات والشخصيات والمظاهر: كل شيء في متناول إصبعك."],
        ["خطواتك", "انتقل بين رفيقك وعدّاد خطواتك."],
        ["العملات", "اكسب العملات باللعب والمشي والعودة كل يوم، ثم أنفقها في متجر المظاهر."],
        ["القائمة", "الشخصيات والمتجر والدماغ والذكريات والإعدادات واللغة: كل شيء هنا."],
        ["دماغه", "للتحدث عن أي شيء، نزّل دماغه من «دماغي». يبقى كل شيء على هاتفك."]
      ],
      privacy: ["خصوصيتك محمية", "لا يجمع Canelle AI أي معلومات شخصية.", "كل ما تقوله له، وما يتذكره عنك، وإعداداتك تبقى على هذا الهاتف فقط. دماغه يعمل دون إنترنت: لا تُرسَل رسائلك إلى أي أحد.", "الطقس والبحث عن الأماكن فقط يستخدمان الإنترنت، وفقط عندما تطلبهما: عندها يُرسَل موقعك إلى Open-Meteo وOpenStreetMap.", "لا تُظهر مجددًا", "فهمت"]
    }
  };
})();
