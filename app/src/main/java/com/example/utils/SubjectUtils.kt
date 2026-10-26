package com.example.utils

import com.example.TopicStats

enum class CanonicalSubject(
    val displayName: String,
    val icon: String,
    val keywords: List<String>
) {
    ANATOMY("Anatomy", "💀", listOf("anatom", "anant", "anat")),
    PHYSIOLOGY("Physiology", "🫁", listOf("physio", "physiology")),
    BIOCHEMISTRY("Biochemistry", "🧪", listOf("biochem", "biochemistry")),
    PATHOLOGY("Pathology", "🔬", listOf("patho", "pathology")),
    PHARMACOLOGY("Pharmacology", "💊", listOf("pharma", "pharmacology")),
    MICROBIOLOGY("Microbiology", "🦠", listOf("micro", "microbiology")),
    FMT("Forensic Medicine (FMT)", "🕵️", listOf("fmt", "forensic")),
    PSM("Community Medicine (PSM)", "🏡", listOf("psm", "community", "preventive")),
    OPHTHALMOLOGY("Ophthalmology", "👁️", listOf("ophth", "ophthalmology")),
    ENT("ENT", "👂", listOf("ent", "ear", "nose", "throat")),
    MEDICINE("Medicine", "🩺", listOf("medicine", "med")),
    SURGERY("Surgery", "🔪", listOf("surgery", "surg")),
    OBG("Obstetrics & Gynecology (OBG)", "🤰", listOf("obg", "obs", "gyne", "obstetrics", "gynecology")),
    PEDIATRICS("Pediatrics", "👶", listOf("pedia", "peds", "pediatrics")),
    ORTHOPEDICS("Orthopedics", "🦴", listOf("ortho", "orthop", "orthopedic", "orthopedics", "orthopaedic", "orthopaedics")),
    PSYCHIATRY("Psychiatry", "🧠", listOf("psyc", "psych", "psychiatry")),
    DERMATOLOGY("Dermatology", "🧴", listOf("derma", "dermatology")),
    RADIOLOGY("Radiology", "🩻", listOf("radio", "radiology")),
    ANESTHESIA("Anesthesia", "💉", listOf("anesthesia", "anaesthesia", "anest", "anaest")),
    GRAND_TESTS("Grand Tests & Mock Exams", "📝", listOf("grand test", "gt", "test series", "dams", "mock"))
}

private fun levenshtein(s1: String, s2: String): Int {
    val dp = IntArray(s2.length + 1) { it }
    for (i in 1..s1.length) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..s2.length) {
            val temp = dp[j]
            if (s1[i - 1] == s2[j - 1]) {
                dp[j] = prev
            } else {
                dp[j] = minOf(dp[j - 1], dp[j], prev) + 1
            }
            prev = temp
        }
    }
    return dp[s2.length]
}

private val categorizeSubjectCache = java.util.concurrent.ConcurrentHashMap<String, CanonicalSubject>()
private val categorizeSubjectNullCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

fun categorizeSubject(rawName: String): CanonicalSubject? {
    if (categorizeSubjectCache.containsKey(rawName)) {
        return categorizeSubjectCache[rawName]
    }
    if (categorizeSubjectNullCache.containsKey(rawName)) {
        return null
    }
    
    val cleanName = rawName.lowercase()
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .trim()
    
    val tokens = cleanName.split(spaceRegex)
        .filter { it !in listOf("pyq", "pqy", "eqb", "mcq", "qb", "qbank", "e", "bank", "old", "new", "test", "practice") }
    
    if (tokens.isEmpty()) {
        categorizeSubjectNullCache[rawName] = true
        return null
    }

    for (category in CanonicalSubject.values()) {
        for (keyword in category.keywords) {
            for (token in tokens) {
                // Direct or token equality
                if (token == keyword) {
                    categorizeSubjectCache[rawName] = category
                    return category
                }
                
                // Prefix or suffix-prefix matching (e.g., "peds" -> "pediatrics", "gyn" -> "gynecology")
                if (token.length >= 3 && (keyword.startsWith(token) || token.startsWith(keyword))) {
                    categorizeSubjectCache[rawName] = category
                    return category
                }
                
                // Levenshtein matching for spelling errors / typos
                if (token.length >= 4 && keyword.length >= 4) {
                    val dist = levenshtein(token, keyword)
                    if (dist <= 1 || (token.length >= 6 && dist <= 2)) {
                        categorizeSubjectCache[rawName] = category
                        return category
                    }
                }
            }
        }
    }
    
    // Substring fallback
    for (category in CanonicalSubject.values()) {
        for (keyword in category.keywords) {
            if (cleanName.contains(keyword)) {
                categorizeSubjectCache[rawName] = category
                return category
            }
        }
    }
    
    categorizeSubjectNullCache[rawName] = true
    return null
}

data class GroupedSubject(
    val canonical: CanonicalSubject?,
    val displayName: String,
    val icon: String,
    val rawMatches: List<Pair<String, Map<String, TopicStats>>>
)

fun groupSubjects(
    summary: Map<String, Map<String, TopicStats>>,
    query: String
): List<GroupedSubject> {
    val matches = mutableMapOf<CanonicalSubject?, MutableList<Pair<String, Map<String, TopicStats>>>>()
    
    for ((subject, topics) in summary) {
        // Evaluate if any topic name matches search query, or the subject name itself
        val matchesQuery = query.isBlank() || 
                           subject.fuzzyMatch(query) ||
                           topics.keys.any { it.fuzzyMatch(query) }
                           
        if (matchesQuery) {
            val category = categorizeSubject(subject)
            matches.getOrPut(category) { mutableListOf() }.add(subject to topics)
        }
    }
    
    val list = mutableListOf<GroupedSubject>()
    
    for (canonical in CanonicalSubject.values()) {
        val rawList = matches[canonical] ?: continue
        rawList.sortBy { it.first }
        list.add(
            GroupedSubject(
                canonical = canonical,
                displayName = canonical.displayName,
                icon = canonical.icon,
                rawMatches = rawList
            )
        )
    }
    
    val otherList = matches[null]
    if (otherList != null && otherList.isNotEmpty()) {
        otherList.sortBy { it.first }
        list.add(
            GroupedSubject(
                canonical = null,
                displayName = "Other Study Material",
                icon = "📁",
                rawMatches = otherList
            )
        )
    }
    
    return list
}

private val spaceRegex = "\\s+".toRegex()

fun String.fuzzyMatch(query: String): Boolean {
    if (query.isBlank()) return true
    if (this.contains(query, ignoreCase = true)) return true
    
    val queryTokens = query.lowercase().split(spaceRegex).filter { it.isNotBlank() }
    val thisTokens = this.lowercase().split(spaceRegex).filter { it.isNotBlank() }
    
    if (queryTokens.isEmpty() || thisTokens.isEmpty()) return false
    
    return queryTokens.all { qToken ->
        thisTokens.any { tToken ->
            if (tToken == qToken) true
            else if (tToken.startsWith(qToken)) true
            else if (qToken.length >= 4 && tToken.length >= 3) {
                val dist = levenshtein(qToken, tToken)
                dist <= 1 || (qToken.length >= 6 && dist <= 2)
            } else {
                false
            }
        }
    }
}

/**
 * Format topic key string (e.g. "Forensic_Toxicology-Previous_Year_Questions")
 * into a clean, human-readable display string (e.g., "Forensic Toxicology: Previous Year Questions").
 */
fun formatTopicDisplayName(topic: String): String {
    if (topic.isBlank()) return topic
    
    val formatted = topic.replace("-", ": ").replace("_", " ")
    val words = formatted.trim().split(spaceRegex)
    val capitalizedWords = words.map { word ->
        if (word.isBlank()) return@map ""
        word.lowercase().replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase() else it.toString() 
        }
    }
    return capitalizedWords.filter { it.isNotEmpty() }.joinToString(" ").replace(" :", ":")
}

