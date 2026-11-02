package com.tbm.iptv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tbm.iptv.data.Prefs

/** תרגום ממשק: הטקסטים בקוד כתובים בעברית, L() מחזיר אנגלית כשנבחרה אנגלית */
object I18n {
    /** "he" | "en" – מצב Compose כדי שכל המסכים יתעדכנו מיד */
    var lang by mutableStateOf("he")

    val isEn get() = lang == "en"

    fun init() { lang = Prefs.str("lang", "he") }

    fun set(l: String) { lang = l; Prefs.setStr("lang", l) }

    /** מחרוזות שלא נאספו אוטומטית (תוויות של enum) */
    val extra = mapOf(
        "טלוויזיה" to "Live TV", "סרטים" to "Movies", "סדרות" to "Series", "בית" to "Home",
        "לוח שידורים" to "TV Guide", "מועדפים" to "Favorites", "הקלטות" to "Recordings",
        "מולטי-ויו" to "Multi-view", "חיפוש" to "Search",
        "הרשימה גדולה מדי לזיכרון המכשיר" to "The playlist is too large for this device's memory",
        "המדריך גדול מדי לזיכרון המכשיר" to "The TV guide is too large for this device's memory",
        "האפליקציה נסגרה בפעם הקודמת" to "The app closed unexpectedly last time",
        "לחץ \"העתק\" ושלח את הדוח כדי שאפשר יהיה לתקן." to "Tap \"Copy\" and send the report so it can be fixed.",
        "העתק" to "Copy", "הועתק" to "Copied", "סגור" to "Close",
        "שם לחיפוש (אפשר שם מקורי או tt1234567)" to "Search name (original title or tt1234567)",
        "חפש" to "Search", "מקורות פעילים: {0}" to "Active sources: {0}",
        "לא פעילים (חסר מפתח בהגדרות ← שמע וכתוביות): {0}" to "Inactive (add a key in Settings → Audio & Subtitles): {0}",
        "מחפש כתוביות…" to "Searching subtitles…", "האתר לא זמין ברשת שלך" to "Site unreachable on your network",
        "OpenSubtitles.org – ללא מפתח" to "OpenSubtitles.org – no key needed",
        "בגיבוי אין רשימת ערוצים" to "The backup has no playlist", "שחזור מגיבוי…" to "Restore from backup…",
        "טלוויזיה, סרטים וסדרות – עם כתוביות בעברית" to "Live TV, movies and series – with Hebrew subtitles",
        "כניסה עם Xtream Codes" to "Sign in with Xtream Codes", "שרת, שם משתמש וסיסמה" to "Server, username and password",
        "רשימת M3U" to "M3U playlist", "קישור או קובץ מהמכשיר" to "Link or file from this device",
        "שחזור מגיבוי" to "Restore from backup", "מחזיר חשבון, מועדפים, הגדרות ומפתחות מקובץ גיבוי" to "Brings back account, favorites, settings and keys from a backup file",
        "קריינות AI כבויה" to "AI narration off", "קריינות AI פעילה" to "AI narration on", "קריינות AI" to "AI narration",
        "קריינות תתחיל כשתיטען כתובית" to "Narration will start once a subtitle loads",
        "אין קול מותקן לשפה הזו – התקן קול עברי בהגדרות אנדרואיד ← המרת טקסט לדיבור (Google)" to "No voice installed for this language – install a Hebrew voice in Android Settings → Text-to-speech (Google)",
        "קריינות AI מהכתוביות" to "AI narration from subtitles", "להפעיל אוטומטית בסרטים וסדרות" to "Turn on automatically for movies and series",
        "מהירות דיבור: {0}×" to "Speech rate: {0}×", "עוצמת הקול המקורי בזמן הקריינות: {0}%" to "Original audio volume while narrating: {0}%",
        "הכפתור 🗣 בנגן מפעיל ומכבה. דורש קול עברי מותקן: הגדרות אנדרואיד ← שפה וקלט ← המרת טקסט לדיבור ← Google ← עברית." to "The 🗣 button in the player toggles it. Requires a Hebrew voice: Android Settings → Language & input → Text-to-speech → Google → Hebrew.",
        "סינון לפי ארצות" to "Filter by country", "מוצגות כל הארצות ({0})" to "Showing all countries ({0})", "נבחרו: {0}" to "Selected: {0}",
        "הקלד שם מדינה (ישראל, UK, France…)" to "Type a country name (Israel, UK, France…)", "לא נמצאה מדינה" to "No country found",
        "רצועה {0}" to "Track {0}", "נבחרה כתובית מובנת: {0}" to "Built-in subtitle selected: {0}",
        "כתוביות מובנות בקובץ:" to "Built-in subtitles:", "רקע שחור (מכסה כתוביות צרובות בתמונה)" to "Black box (covers burned-in subtitles)",
        "כתובית חיצונית מחליפה כתוביות מובנות בקובץ" to "External subtitle replaces built-in subtitles",
        "גובה הכתוביות" to "Subtitle height",
        "קטגוריה" to "Category", "קטגוריות – {0}" to "Categories – {0}", "חיפוש קטגוריה…" to "Search category…",
        "לא נמצאה קטגוריה" to "No category found", "הכל" to "All",
        "לא פעיל – חסר מפתח/התחברות או כבוי" to "Inactive – missing key/login or turned off",
        "מחובר, אבל לא הוחזרו תוצאות ({0} מ\"ש)" to "Connected, but no results ({0} ms)",
        "מחובר ✓ – עברית: {0}, אנגלית: {1} ({2} מ\"ש)" to "Connected ✓ – Hebrew: {0}, English: {1} ({2} ms)",
        "אין תגובה מהאתר (חריגת זמן)" to "No response from the site (timeout)",
        "האתר לא זמין ברשת שלך (DNS)" to "Site unreachable on your network (DNS)",
        "נדחה – בדוק מפתח/סיסמה ({0})" to "Rejected – check key/password ({0})",
        "חרגת ממכסת הבקשות – נסה מאוחר יותר" to "Request quota exceeded – try later",
        "שמע {0}" to "Audio {0}", "מהירות: {0}×" to "Speed: {0}×", "שמע: {0}" to "Audio: {0}",
        "צפייה לאחור: {0}" to "Catch-up: {0}", "עדיין צופה?" to "Still watching?",
        "ניגנו 3 פרקים ברצף. להמשיך לפרק הבא?" to "3 episodes played in a row. Continue to the next one?",
        "המשך לצפות" to "Keep watching", "יציאה" to "Exit", "ביטול נעילה" to "Unlock",
        "ערוץ קודם" to "Previous channel", "ערוץ הבא" to "Next channel", "10 שניות אחורה" to "Back 10 seconds",
        "10 שניות קדימה" to "Forward 10 seconds", "ניגון/השהיה" to "Play/Pause", "חזרה לשידור חי" to "Back to live",
        "ערוצים" to "Channels", "מהירות" to "Speed", "שמע וכתוביות" to "Audio & subtitles", "לאורך" to "Portrait",
        "לרוחב" to "Landscape", "נעילה" to "Lock", "הפרק הבא" to "Next episode", "דלג על הפתיח" to "Skip intro",
        "הפרק הבא בעוד {0}" to "Next episode in {0}", "⏪ צפייה לאחור זמינה – {0} ימים" to "⏪ Catch-up available – {0} days",
        "אין ארכיון לערוץ הזה" to "No archive for this channel", "אין מידע לוח שידורים לערוץ הזה" to "No guide data for this channel",
        "תזכורת נקבעה: {0}" to "Reminder set: {0}", "הגדל" to "Expand", "שמע:" to "Audio:", "סנכרון כתובית:" to "Subtitle sync:",
        "חיפוש כתוביות זמין לסרטים וסדרות" to "Subtitle search is available for movies and series",
        "⏺ ההקלטה התחילה – עד {0}" to "⏺ Recording started – until {0}",
        "צופה מתוך ההקלטה – אפשר להשהות" to "Watching from the recording – pause available",
        "ההקלטה נעצרה ונשמרה" to "Recording stopped and saved", "ההקלטה תסתיים ב-{0}" to "Recording will end at {0}",
        "אין הקלטה פעילה לערוץ הזה" to "No active recording for this channel", "⏺ התחל להקליט" to "⏺ Start recording",
        "מסתיים ב-{0} · {1} MB" to "Ends at {0} · {1} MB",
        "צופה מתוך ההקלטה (חיבור אחד לספק) – אפשר להשהות" to "Watching from the recording (one provider connection) – pause available",
        "⏹ עצור הקלטה ושמור" to "⏹ Stop and save recording", "+30 דק׳" to "+30 min", "+שעה" to "+1 hour",
        "עד סוף \"{0}\" ({1})" to "Until end of \"{0}\" ({1})", "ללא הגבלה – עד שאעצור" to "No limit – until I stop",
        "עוד X דקות מעכשיו" to "X more minutes from now", "קבע" to "Set", "גרסה {0}" to "Version {0}", "סובב מסך" to "Rotate screen",
        "🔌 בדוק חיבור לכל המקורות" to "🔌 Test all sources", "בודק…" to "Testing…", "לא פעיל" to "Inactive", "בדוק" to "Test",
        "ללא סימון = כל הארצות מוצגות." to "Nothing selected = all countries shown.", "OpenSubtitles v3 – ללא מפתח" to "OpenSubtitles v3 – no key needed",
        "{0}ש׳ {1}ד׳" to "{0}h {1}m",
        "{0}ד׳" to "{0}m",
        "0ד׳" to "0m",
        "נותרו {0}" to "{0} remaining",
        "פחות" to "Less",
        "עוד" to "More",
        "במאי" to "Director",
        "אין מידע על השחקנים" to "No cast information",
        "לא נמצא טריילר" to "No trailer found",
        "טריילר" to "Trailer",
        "ייפתח ב-YouTube" to "Opens in YouTube", "פתח ב-YouTube" to "Open in YouTube", "נושאים נוספים שמותרים:" to "Extra allowed topics:", "🌿 טבע ובעלי חיים" to "🌿 Nature & animals", "⚽ ספורט" to "⚽ Sports", "🔬 מדע ודוקו" to "🔬 Science & docs", "🎵 מוזיקה" to "🎵 Music", "📚 חינוכי" to "📚 Educational", "מדינות / שפות:" to "Countries / languages:", "כולן" to "All", "להתנתק?" to "Sign out?", "כל ההגדרות שמורות בחשבון שלך ויחזרו כשתתחבר שוב. מהמכשיר הזה הן יימחקו, כדי שמי שיתחבר אחריך לא יראה אותן." to "All settings are saved in your account and will come back when you sign in again. They'll be removed from this device so whoever signs in next won't see them.", "תוכן · עד {0}" to "Content · up to {0}", "תוכן לפרופיל {0}" to "Content for {0}", "גיל מקסימלי:" to "Maximum age:", "סינון אוטומטי לפי שמות קטגוריות וערוצים" to "Automatic filter by category and channel names", "רק מה שמוצג" to "Shown only", "יוצגו לילד: {0} פריטים מתוך {1}. לחיצה על קטגוריה מחליפה: מוצג ⇄ חסום." to "Shown to the child: {0} of {1} items. Tap a category to toggle shown ⇄ blocked.", "התוכן עדיין נטען…" to "Content is still loading…", "מאושר ידנית" to "Approved manually", "מוצג אוטומטית (מתאים לגיל)" to "Shown automatically (age-appropriate)", "חסום" to "Blocked", "לא מוצג" to "Not shown", "מסנכרן…" to "Syncing…", "מסונכרן ✓" to "Synced ✓", "שגיאת סנכרון: {0}" to "Sync error: {0}", "עודכן מהענן ✓" to "Updated from cloud ✓", "אימייל או סיסמה שגויים" to "Wrong email or password", "אין חשבון עם האימייל הזה" to "No account with this email", "כבר קיים חשבון עם האימייל הזה – התחבר" to "An account already exists – sign in", "כתובת אימייל לא תקינה" to "Invalid email address", "הסיסמה צריכה לפחות 6 תווים" to "Password needs at least 6 characters", "אין חיבור לאינטרנט" to "No internet connection", "שיטת הכניסה הזו לא הופעלה ב-Firebase" to "This sign-in method isn't enabled in Firebase", "כניסה עם Google עדיין לא הופעלה ב-Firebase" to "Google sign-in isn't enabled in Firebase yet", "כניסה עם Google נכשלה" to "Google sign-in failed", "חשבון וסנכרון" to "Account & sync", "מחובר כ-" to "Signed in as", "חשבון Google" to "Google account", "סנכרון אחרון: {0}" to "Last sync: {0}", "נשמרים ומסתנכרנים בין כל המכשירים: מקורות תוכן, מועדפים, פרופילים וקוד הורים, הגדרות ומפתחות, ומיקום הצפייה בכל סרט ופרק." to "Synced across all devices: sources, favorites, profiles and parent PIN, settings and keys, and where you stopped in every movie and episode.", "🔄 סנכרן עכשיו" to "🔄 Sync now", "📺 חבר טלוויזיה / סטרימר בקוד" to "📺 Connect a TV / streamer with a code", "התנתק" to "Sign out", "בטלוויזיה או בסטרימר?" to "On a TV or streamer?", "קבל קוד והזן אותו בטלפון שכבר מחובר – בלי להקליד עם השלט." to "Get a code and enter it on a phone that's already signed in – no typing with the remote.", "מחובר ✓" to "Connected ✓", "הקוד פג – נסה שוב" to "The code expired – try again", "📺 התחבר עם קוד מהטלפון" to "📺 Sign in with a code from your phone", "חיבור למכשיר הזה" to "Connect this device", "בטלפון: הגדרות ← חשבון וסנכרון ← \"חבר טלוויזיה / סטרימר בקוד\", והזן:" to "On your phone: Settings → Account & sync → \"Connect a TV / streamer with a code\", and enter:", "ממתין לטלפון… (הקוד בתוקף 5 דקות)" to "Waiting for the phone… (code valid for 5 minutes)", "התחבר כדי לשמור ולסנכרן את כל ההגדרות בין הטלפון, הטאבלט והסטרימר" to "Sign in to save and sync all settings between your phone, tablet and streamer", "המשך עם Google" to "Continue with Google", "או" to "or", "אימייל" to "Email", "התחבר" to "Sign in", "צור חשבון חדש" to "Create a new account", "נשלח מייל לאיפוס סיסמה" to "Password reset email sent", "שכחתי סיסמה" to "Forgot password", "חיבור טלוויזיה / סטרימר" to "Connect a TV / streamer", "בטלוויזיה: הגדרות ← חשבון וסנכרון ← \"התחבר עם קוד מהטלפון\". הזן כאן את הקוד שמופיע שם:" to "On the TV: Settings → Account & sync → \"Sign in with a code from your phone\". Enter the code shown there:", "קוד (8 תווים)" to "Code (8 characters)", "הסיסמה של החשבון (לאישור)" to "Account password (to confirm)", "נשלח – הטלוויזיה תתחבר בעוד רגע" to "Sent – the TV will sign in in a moment", "הקוד לא נמצא או שפג תוקפו" to "Code not found or expired", "חבר" to "Connect", "חשבון" to "Account", "לא מחובר – התחבר כדי לסנכרן בין מכשירים" to "Not signed in – sign in to sync between devices", "התחבר לחשבון TBM" to "Sign in to a TBM account", "כבר יש לך חשבון? הכל ייטען אוטומטית – גם בטלוויזיה עם קוד מהטלפון" to "Already have an account? Everything loads automatically – on TV too, with a code from your phone", "הזן את קוד ההורים (4 ספרות)" to "Enter the parent PIN (4 digits)", "קוד שגוי – נסה שוב" to "Wrong PIN – try again", "בחר קוד בן 4 ספרות" to "Choose a 4-digit PIN", "הקודים לא תאמו – התחל שוב" to "PINs didn't match – start again", "אימות קוד" to "Confirm PIN", "הזן שוב את אותו קוד" to "Enter the same PIN again", "הוספת פרופיל" to "Add profile", "הוסף פרופיל" to "Add profile", "ניהול פרופילים" to "Manage profiles", "✏ ניהול פרופילים" to "✏ Manage profiles", "פרופיל חדש" to "New profile", "עריכת פרופיל" to "Edit profile", "הורה / מבוגר" to "Parent / adult", "גיל הילד – יוצג רק תוכן שמתאים עד הגיל הזה:" to "Child's age – only content suitable up to this age is shown:", "עד {0}" to "Up to {0}", "סינון אוטומטי (ערוצים וקטגוריות ילדים)" to "Automatic filter (kids channels & categories)", "בנוסף אפשר לאשר ידנית כל ערוץ, סרט או קבוצה (לחיצה ארוכה ← \"אשר לפרופיל ילדים\"). תוכן אימה, פשע ולמבוגרים נחסם לפי הגיל, ועם מפתח TMDB נבדק גם סיווג הגיל הרשמי של כל סרט וסדרה." to "You can also approve any channel, movie or group manually (long press → \"Approve for kids profile\"). Horror, crime and adult content is blocked by age, and with a TMDB key the official age rating of each title is checked too.", "ילדים · עד גיל {0}" to "Kids · up to age {0}", "הורה · 🔒" to "Parent · 🔒", "עד גיל {0}" to "Up to age {0}", "+ הוסף פרופיל" to "+ Add profile", "להציג \"מי צופה?\" בכל פתיחה" to "Show \"Who's watching?\" on every launch", "לא מתאים לגיל" to "Not suitable for this age", "התוכן הזה מסווג לגיל {0}+ ולכן לא זמין בפרופיל {1}." to "This title is rated {0}+ so it's not available in the {1} profile.", "שנה גודל" to "Resize", "מתחבר מחדש… ({0}/{1})" to "Reconnecting… ({0}/{1})", "הספק דחה את הבקשה (מנוי, הרשאה או יותר מדי חיבורים)" to "The provider rejected the request (subscription, permission or too many connections)", "הקובץ לא נמצא אצל הספק" to "The file was not found on the provider", "יותר מדי חיבורים פעילים לחשבון – סגור צפייה במכשיר אחר" to "Too many active connections – stop watching on another device", "שגיאת שרת אצל הספק – נסה שוב בעוד רגע" to "Provider server error – try again in a moment", "שגיאת ניגון: {0}" to "Playback error: {0}", "טוען מחדש…" to "Reloading…", "🔄 נסה שוב" to "🔄 Retry", "נגן חיצוני" to "External player", "הבא ⏭" to "Next ⏭", "רענן / טען מחדש" to "Refresh / reload", "אם התמונה נתקעה או יש שגיאה" to "If the picture froze or there's an error", "איך משיגים מפתח חינמי (2 דקות): נרשמים ב-themoviedb.org ← הגדרות ← API ← בקשת מפתח (Developer) ← מעתיקים את \"API Key\" או את \"API Read Access Token\" ומדביקים כאן." to "Getting a free key (2 minutes): sign up at themoviedb.org → Settings → API → request a key (Developer) → copy the \"API Key\" or \"API Read Access Token\" and paste it here.", "פתח את אתר TMDB" to "Open TMDB website", "סרטים פופולריים היום" to "Trending Movies Today", "סדרות פופולריות היום" to "Trending Series Today", "🔥 סרטים וסדרות פופולריים היום" to "🔥 Trending movies & series today", "כדי לראות את הטרנדים של היום מתוך התוכן שלך, הוסף מפתח TMDB חינמי. לחץ כאן להגדרה." to "To see today's trends from your content, add a free TMDB key. Tap here to set it up.", "החלון הקטן חסום – אפשר אותו בהגדרות: אפליקציות ← TBM Player ← תמונה בתוך תמונה" to "Mini window is blocked – enable it in Settings → Apps → TBM Player → Picture-in-picture", "חלון קטן (תמונה בתוך תמונה)" to "Mini window (picture-in-picture)", "פתח עכשיו – או לחץ על כפתור הבית" to "Open now – or press Home", "חסום במכשיר – לחץ כדי לאפשר" to "Blocked on this device – tap to allow", "רשת (איכותי יותר)" to "network (better quality)", "קול {0}" to "Voice {0}", "סגנון קול" to "Voice style", "לפי הדובר" to "By speaker", "מספר" to "Narrator", "גבר" to "Man", "אישה" to "Woman", "ילד" to "Child", "שלום, ככה אני נשמע." to "Hello, this is how I sound.", "\"לפי הדובר\": מזהה מהדקדוק אם מדבר גבר או אישה (\"אני הולכת\" / \"אני הולך\") ומחליף קול בהתאם." to "\"By speaker\": detects from Hebrew grammar whether a man or a woman is speaking and switches voice.", "קול שונה לכל צד בדיאלוג (שורות עם מקף)" to "Different voice for each side of a dialogue (dash lines)", "לא נמצאו קולות עבריים מותקנים – התקן: הגדרות אנדרואיד ← המרת טקסט לדיבור ← Google ← עברית" to "No Hebrew voices installed – install: Android Settings → Text-to-speech → Google → Hebrew", "קול לגבר" to "Male voice", "קול לאישה / ילד" to "Female / child voice", "אני הולך הביתה, נתראה מחר." to "I'm going home, see you tomorrow.", "אני הולכת הביתה, נתראה מחר." to "I'm going home, see you tomorrow.", "▶ נסה" to "▶ Try", "בחר" to "Choose", "קולות ☁ (רשת) נשמעים טבעיים יותר אבל צריכים אינטרנט. כשיש רק קול אחד, ההבדל בין גבר/אישה/ילד נעשה בגובה הקול." to "☁ network voices sound more natural but need internet. With a single voice, man/woman/child differ by pitch.", "מושבת" to "Disabled", "מעדכן מדריך שידורים…" to "Updating TV guide…", "עבור למקור הזה כדי לעדכן את המדריך" to "Switch to this source to update its guide", "המדריך נמחק" to "Guide cleared", "עדכון מדריך שידורים" to "Update EPG", "מחיקת מדריך שידורים" to "Clear EPG", "הפעלה" to "Enable", "השבתה" to "Disable", "מידע" to "Info", "סוג" to "Type", "עריכת מקור" to "Edit source", "שם המקור" to "Source nickname", "כתובת השרת / הרשימה" to "Server / playlist URL", "נדרשת התחברות? (Xtream)" to "Login required? (Xtream)", "ללא" to "None", " שעות" to " h", "היסט זמן למדריך (EPG)" to "EPG offset", "אם לוח השידורים מראה תוכניות בשעה לא נכונה (למשל שעה מוקדם), הזז כאן." to "If the guide shows programs at the wrong time (e.g. one hour early), shift it here.", "אפשרויות מתקדמות" to "Advanced options", "כתובת EPG חלופית (XMLTV)" to "Alternative EPG URL (XMLTV)", "ריק = המדריך של הספק." to "Empty = provider's guide.", "עדכן" to "Update", "סטטוס" to "Status", "נוצר" to "Created", "ניסיון" to "Trial", "כן" to "Yes", "לא" to "No", "חיבורים פעילים" to "Active connections", "פורמטים" to "Formats", "שרת" to "Server", "אזור זמן" to "Time zone", "שעת שרת" to "Server time", "ללא הגבלה" to "Unlimited", "משתמש: {0}" to "User: {0}", "● פעיל כעת" to "● Active now", "לחץ כדי לעבור למקור הזה" to "Tap to switch to this source", "עריכה" to "Edit", "נשמר" to "Saved", "מחיקת מקור" to "Delete source", "למחוק את \"{0}\"?" to "Delete \"{0}\"?", "מחק" to "Delete", "עריכת רשימת M3U" to "Edit M3U playlist", "שמור" to "Save", "עריכת חשבון Xtream" to "Edit Xtream account", "הצג סיסמה" to "Show password", "פתח באמצעות" to "Open with", "הנגן החיצוני לא נמצא – משתמש בנגן המובנה" to "External player not found – using the built-in player", "מחפש כתוביות לנגן החיצוני…" to "Finding subtitles for the external player…", "פתח בנגן חיצוני" to "Open in external player", "VLC / MX – בחירה" to "VLC / MX – choose", "בחירת נגן" to "Player selection", "מובנה" to "Built-in", "חיצוני" to "External", "נגן חיצוני מועדף" to "Preferred external player", "לשאול בכל פעם" to "Ask every time", "בנגן החיצוני מצורפת כתובית עברית (מהמטמון או מחיפוש אוטומטי) והצפייה ממשיכה מהמקום שעצרת. בנגן המובנה: ⚙ ← \"פתח בנגן חיצוני\"." to "Hebrew subtitles (cached or auto-found) are passed to the external player and playback resumes where you stopped. In the built-in player: ⚙ → \"Open in external player\".", "לא נמצאו נגנים מותקנים – התקן VLC או MX Player מחנות Play" to "No players installed – install VLC or MX Player from Google Play", "תרגום תקצירים, ז'אנרים ושמות" to "Translate plots, genres and names", "פעיל – לשפת הממשק (TMDB / Google)" to "On – to the app language (TMDB / Google)",
        "אין תמונות" to "No images",
        "{0} עונות" to "{0} seasons",
        "פרקים" to "Episodes",
        "שחקנים" to "Cast",
        "תמונות" to "Media",
        "{0} פרקים" to "{0} episodes",
        "פרטים" to "More info",
        "הכל ›" to "See all ›",
        "אין מידע שידור" to "No program info",
        "🕘 נצפו לאחרונה" to "🕘 Recently watched",
        "תצוגה" to "View",
        "רשת + בחירת קטגוריה" to "Grid + category picker",
        "שורות לפי קטגוריה" to "Rows by category",
        "רשימת קטגוריות" to "Category list",
        "אפשרויות" to "Options",
        "רצועת שמע" to "Audio track",
        "ברירת מחדל" to "Default",
        "כבוי" to "Off",
        "רגילה" to "Normal",
        "{0} דק׳" to "{0} min",
        "פעילה" to "On",
        "כבויה" to "Off",
        "אין רצועות שמע נוספות" to "No other audio tracks",
        "זום (מילוי עם חיתוך)" to "Zoom (crop to fill)",
        "מתיחה למסך מלא" to "Stretch to fill",
        "מקורי" to "Original",
        "כללי" to "General",
        "שפת אפליקציה" to "App language",
        "לוח שידורים וצפייה לאחור" to "TV guide and catch-up",
        "תצוגת ערוצים" to "Channels view",
        "תצוגת סרטים" to "Movies view",
        "תצוגת סדרות" to "Series view",
        "סידור והסתרת קטגוריות" to "Re-order & show/hide categories",
        "סדר, הסתרה, הצמדה, סינון ארצות" to "Order, hide, pin, country filter",
        "מפתח TMDB" to "TMDB key",
        "PiP, פרק הבא אוטומטי, נגן חיצוני, קריינות" to "PiP, autoplay next, external player, narration",
        "חיפוש אוטומטי, מקורות, תרגום מכונה" to "Auto search, sources, machine translation",
        "קוד, נעילות, פרופיל ילדים" to "PIN, locks, kids profile",
        "נתונים" to "Data",
        "היסטוריית צפייה" to "Watching history",
        "{0} פריטים – לחץ לניקוי" to "{0} items – tap to clear",
        "תצוגת {0}" to "{0} view",
    )
}

/** מתרגם מחרוזת. {0}, {1}… מוחלפים בארגומנטים */
fun L(s: String, vararg args: Any?): String {
    var r = if (I18n.isEn) (I18nEn.map[s] ?: I18n.extra[s] ?: s) else s
    args.forEachIndexed { i, a -> r = r.replace("{$i}", a?.toString() ?: "") }
    return r
}
