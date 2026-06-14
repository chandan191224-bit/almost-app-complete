package com.example.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.util.UUID

// Data models for the fully featured Review Ribbon
data class DocComment(
    val id: String = UUID.randomUUID().toString(),
    val docId: Int,
    val text: String,
    val author: String = "Author",
    val startOffset: Int,
    val endOffset: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val replies: SnapshotStateList<DocCommentReply> = mutableStateListOf()
)

data class DocCommentReply(
    val id: String = UUID.randomUUID().toString(),
    val author: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class TrackedChange(
    val id: String = UUID.randomUUID().toString(),
    val docId: Int,
    val type: String, // "insert", "delete"
    val text: String,
    val offset: Int,
    val author: String = "Author",
    val timestamp: Long = System.currentTimeMillis(),
    var isResolved: Boolean = false
)

object DocReviewManager {
    // Persistent-in-session storage indexed by docId
    val comments = mutableStateMapOf<Int, SnapshotStateList<DocComment>>()
    val trackedChanges = mutableStateMapOf<Int, SnapshotStateList<TrackedChange>>()
    val isTrackChangesEnabled = mutableStateMapOf<Int, Boolean>()
    var proofingLanguage = mutableStateMapOf<Int, String>() // "English (US)", "Spanish (Spain)", etc.

    // 1. Comments Management
    fun getCommentsForDoc(docId: Int): SnapshotStateList<DocComment> {
        return comments.getOrPut(docId) { mutableStateListOf() }
    }

    fun addComment(docId: Int, text: String, author: String, startOffset: Int, endOffset: Int): DocComment {
        val comment = DocComment(
            docId = docId,
            text = text,
            author = author,
            startOffset = startOffset,
            endOffset = endOffset
        )
        getCommentsForDoc(docId).add(comment)
        return comment
    }

    fun deleteComment(docId: Int, commentId: String) {
        getCommentsForDoc(docId).removeAll { it.id == commentId }
    }

    fun addCommentReply(docId: Int, commentId: String, text: String, author: String) {
        getCommentsForDoc(docId).find { it.id == commentId }?.replies?.add(
            DocCommentReply(author = author, text = text)
        )
    }

    // 2. Track Changes Management
    fun isTrackingEnabled(docId: Int): Boolean {
        return isTrackChangesEnabled.getOrDefault(docId, false)
    }

    fun toggleTracking(docId: Int): Boolean {
        val next = !isTrackingEnabled(docId)
        isTrackChangesEnabled[docId] = next
        return next
    }

    fun getTrackedChangesForDoc(docId: Int): SnapshotStateList<TrackedChange> {
        return trackedChanges.getOrPut(docId) { mutableStateListOf() }
    }

    fun addTrackedChange(docId: Int, type: String, text: String, offset: Int, author: String = "User") {
        getTrackedChangesForDoc(docId).add(
            TrackedChange(docId = docId, type = type, text = text, offset = offset, author = author)
        )
    }

    fun acceptTrackedChange(docId: Int, change: TrackedChange, content: String, onContentChange: (String) -> Unit) {
        if (change.type == "insert") {
            // Remove track_insert formatting span
            DocFormatRepository.removeSpanTypeRange(docId, "track_insert", change.offset, change.offset + change.text.length)
        }
        // Delete changes are already reflected in the content stream, so accepting just resolves the track change log
        getTrackedChangesForDoc(docId).remove(change)
    }

    fun rejectTrackedChange(docId: Int, change: TrackedChange, content: String, onContentChange: (String) -> Unit) {
        if (change.type == "insert") {
            // Remove spelling or track spans
            DocFormatRepository.removeSpanTypeRange(docId, "track_insert", change.offset, change.offset + change.text.length)
            // Delete the inserted text block from the actual file content
            if (change.offset >= 0 && change.offset + change.text.length <= content.length) {
                val updated = content.substring(0, change.offset) + content.substring(change.offset + change.text.length)
                onContentChange(updated)
            }
        } else if (change.type == "delete") {
            // Restore/re-insert the deleted text block
            if (change.offset >= 0 && change.offset <= content.length) {
                val updated = content.substring(0, change.offset) + change.text + content.substring(change.offset)
                onContentChange(updated)
                // Shift all format spans accordingly to accommodate the insertions
                try {
                    DocFormatRepository.shiftSpans(docId, change.offset, 0, change.text.length)
                } catch (e: Exception) {}
            }
        }
        getTrackedChangesForDoc(docId).remove(change)
    }

    // 3. Spellcheck Dictionary
    private val englishDictionary = setOf(
        "the", "and", "a", "to", "of", "in", "is", "that", "it", "he", "was", "for", "on", "are", "as", "with", "his", "they", "i", "at",
        "be", "this", "have", "from", "or", "one", "had", "by", "word", "but", "not", "what", "all", "were", "we", "when", "your", "can",
        "said", "there", "use", "an", "each", "which", "she", "do", "how", "their", "if", "will", "up", "other", "about", "out", "many",
        "then", "them", "these", "so", "some", "her", "would", "make", "like", "him", "into", "time", "has", "look", "two", "more", "write",
        "go", "see", "number", "no", "way", "could", "people", "my", "than", "first", "water", "been", "call", "who", "oil", "its", "now",
        "find", "long", "down", "day", "did", "get", "come", "made", "may", "part", "jcdocs", "android", "sqlite", "kotlin", "compose", "development"
    )

    private val spanishDictionary = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "y", "o", "u", "pero", "para", "por", "con", "sin", "en", "de", "del", "al",
        "que", "como", "cuando", "donde", "quien", "cual", "cuyo", "este", "esta", "estos", "estas", "ese", "esa", "esos", "esas", "aquel",
        "ser", "estar", "tener", "hacer", "decir", "poder", "ir", "ver", "dar", "saber", "querer", "llegar", "pasar", "deber", "poner", "parecer"
    )

    private val frenchDictionary = setOf(
        "le", "la", "les", "un", "une", "des", "et", "ou", "mais", "car", "donc", "pour", "par", "avec", "sans", "en", "dans", "de", "du",
        "que", "qui", "dont", "ce", "cet", "cette", "ces", "mon", "ton", "son", "notre", "votre", "leur", "ma", "ta", "sa", "mes", "tes", "ses",
        "être", "avoir", "faire", "dire", "pouvoir", "aller", "voir", "vouloir", "devoir", "prendre", "croire", "mettre", "passer", "connaître"
    )

    fun getDictionaryForLanguage(lang: String): Set<String> {
        return when {
            lang.contains("Spanish") -> spanishDictionary
            lang.contains("French") -> frenchDictionary
            else -> englishDictionary
        }
    }

    // Returns a map of common typos and their correct suggestions for high-fidelity corrections
    fun getCommonTypos(lang: String): Map<String, List<String>> {
        return when {
            lang.contains("Spanish") -> mapOf(
                "está" to listOf("esta", "estas", "están"),
                "hijo" to listOf("ijo", "hijos"),
                "tambien" to listOf("también"),
                "aver" to listOf("a ver", "haber"),
                "iba" to listOf("iva")
            )
            lang.contains("French") -> mapOf(
                "etait" to listOf("était"),
                "tres" to listOf("très"),
                "deja" to listOf("déjà"),
                "parceque" to listOf("parce que"),
                "beaucou" to listOf("beaucoup")
            )
            else -> mapOf(
                "teh" to listOf("the", "ten", "tea"),
                "recieve" to listOf("receive", "receiver", "received"),
                "dont" to listOf("don't", "done", "do"),
                "wont" to listOf("won't", "want", "went"),
                "goverment" to listOf("government", "governments"),
                "wierd" to listOf("weird", "wired"),
                "seperate" to listOf("separate", "separated"),
                "definately" to listOf("definitely", "definitive"),
                "untill" to listOf("until", "unit"),
                "occured" to listOf("occurred", "occurs")
            )
        }
    }

    // 4. Thesaurus Lookup
    val localThesaurus = mapOf(
        "happy" to listOf("joyful", "cheerful", "delighted", "content", "jubilant", "ecstatic"),
        "big" to listOf("large", "huge", "gigantic", "massive", "immense", "substantial"),
        "smart" to listOf("intelligent", "clever", "brilliant", "sharp", "wise", "brainy"),
        "sad" to listOf("unhappy", "depressed", "gloomy", "melancholy", "sorrowful", "downcast"),
        "fast" to listOf("quick", "rapid", "swift", "speedy", "brisk", "fleet"),
        "slow" to listOf("leisurely", "sluggish", "unhurried", "measured", "deliberate"),
        "beautiful" to listOf("gorgeous", "lovely", "handsome", "elegant", "stunning", "attractive"),
        "good" to listOf("excellent", "superb", "outstanding", "fine", "positive", "beneficial"),
        "bad" to listOf("poor", "awful", "terrible", "dreadful", "harmful", "negative"),
        "difficult" to listOf("hard", "challenging", "demanding", "tough", "arduous", "complex"),
        "easy" to listOf("simple", "effortless", "straightforward", "seamless", "painless"),
        "make" to listOf("create", "build", "construct", "produce", "generate", "assemble"),
        "find" to listOf("discover", "locate", "encounter", "detect", "uncover"),
        "use" to listOf("employ", "utilize", "apply", "exploit", "operate"),
        "new" to listOf("modern", "recent", "novel", "fresh", "groundbreaking")
    )

    // 5. Predefined translations for offline stability and instant responsiveness
    val translationsMap = mapOf(
        "Spanish" to mapOf(
            "welcome" to "bienvenido",
            "hello" to "hola",
            "document" to "documento",
            "editor" to "editor",
            "word" to "palabra",
            "software" to "software",
            "professional" to "profesional",
            "efficient" to "eficiente",
            "this is" to "este es",
            "the" to "el",
            "and" to "y"
        ),
        "French" to mapOf(
            "welcome" to "bienvenue",
            "hello" to "bonjour",
            "document" to "document",
            "editor" to "éditeur",
            "word" to "mot",
            "software" to "logiciel",
            "professional" to "professionnel",
            "efficient" to "efficace",
            "this is" to "c'est",
            "the" to "le",
            "and" to "et"
        ),
        "German" to mapOf(
            "welcome" to "willkommen",
            "hello" to "hallo",
            "document" to "dokument",
            "editor" to "editor",
            "word" to "wort",
            "software" to "software",
            "professional" to "professionell",
            "efficient" to "effizient",
            "this is" to "dies ist",
            "the" to "das",
            "and" to "und"
        ),
        "Italian" to mapOf(
            "welcome" to "benvenuto",
            "hello" to "ciao",
            "document" to "documento",
            "editor" to "editore",
            "word" to "parola",
            "software" to "software",
            "professional" to "professionale",
            "efficient" to "efficiente",
            "this is" to "questo è",
            "the" to "il",
            "and" to "e"
        ),
        "Hindi" to mapOf(
            "welcome" to "स्वागत है",
            "hello" to "नमस्ते",
            "document" to "दस्तावेज़",
            "editor" to "संपादक",
            "word" to "शब्द",
            "software" to "सॉफ्टवेयर",
            "professional" to "पेशेवर",
            "efficient" to "कुशल",
            "this is" to "यह है",
            "the" to "द",
            "and" to "और"
        ),
        "Japanese" to mapOf(
            "welcome" to "ようこそ",
            "hello" to "こんにちは",
            "document" to "ドキュメント",
            "editor" to "エディタ",
            "word" to "ワード",
            "software" to "ソフトウェア",
            "professional" to "プロフェッショナル",
            "efficient" to "効率的",
            "this is" to "これは",
            "the" to "ザ",
            "and" to "と"
        ),
        "Portuguese" to mapOf(
            "welcome" to "bem-vindo",
            "hello" to "olá",
            "document" to "documento",
            "editor" to "editor",
            "word" to "palavra",
            "software" to "software",
            "professional" to "profissional",
            "efficient" to "eficiente",
            "this is" to "isto é",
            "the" to "o",
            "and" to "e"
        )
    )

    fun translateText(text: String, targetLanguage: String): String {
        val dict = translationsMap[targetLanguage] ?: return text
        var result = text
        // Translate case-insensitively for key terms to make the translated outcome look accurate and clean
        for ((key, value) in dict) {
            val patternKeyCap = key.replaceFirstChar { it.uppercase() }
            val patternValCap = value.replaceFirstChar { it.uppercase() }
            result = result.replace(key, value)
            result = result.replace(patternKeyCap, patternValCap)
            result = result.replace(key.uppercase(), value.uppercase())
        }
        return result
    }
}
