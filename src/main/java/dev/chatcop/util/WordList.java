package dev.chatcop.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Built-in detection patterns.
 *
 * These run against the output of {@link TextNormalizer}, which has already
 * lowercased the text and mapped leet characters, so patterns only need to
 * carry the alternatives the normalizer deliberately preserves (the censor
 * star) plus the leet forms that also read as plain letters.
 */
public final class WordList {

    private WordList() {}

    // ── RACIAL / ETHNIC / IDENTITY SLURS ─────────────────────────────────────
    // Word-boundary anchored so ordinary words that merely contain the letters
    // (night, knight, Nigeria, niggle, class, pass) are never touched.
    public static final List<String> SLUR_PATTERNS = List.of(
            // N-word — requires a double-g or an explicit slur ending.
            "\\bn[i!1|]+gg+[a@4e3o0]*[hr]*s*\\b",
            "\\bn[i!1|]+g[a@4]+h*s*\\b",
            // Symbol/space obfuscation that survives normalization.
            "\\bn[^a-z0-9]{0,3}i[^a-z0-9]{0,3}g[^a-z0-9]{0,3}g[^a-z0-9]{0,3}[a@4e3]+r?s?\\b",
            // K-word
            "\\bk[i1]+k[e3]s?\\b",
            // Anti-Chinese
            "\\bch[i1]+nk[s]?\\b",
            // Sp*c
            "\\bsp[i1]+c[ks]?\\b",
            // G slur
            "\\bf[a@4]+gg?[o0][ts]?\\b",
            "\\bf[a@4]+g\\b",
            // Tr*nny
            "\\btr[a@4]nn[yi1][e3]?s?\\b",
            // R-word
            "\\br[e3]t[a@4]rd(ed|s)?\\b",
            // W-word
            "\\bw[e3]tb[a@4]ck[s]?\\b",
            // Other ethnic slurs
            "\\bt[o0]welh[e3][a@4]d[s]?\\b",
            "\\bb[e3][a@4]n[e3]r[s]?\\b",
            "\\bg[o0]{2,}k[s]?\\b"
            // NOTE: "cracker" is intentionally NOT here. It collides with the
            // food and with "safecracker"/"nutcracker" and produced constant
            // false positives. Servers that want it can add it under
            // filters.toxicity.blocked-phrases.
    );

    // ── SEXUAL CONTENT ───────────────────────────────────────────────────────
    // Separate from slurs: different severity, different alert wording, and
    // toggleable on its own (filters.toxicity.block-sexual-content).
    public static final List<String> SEXUAL_PATTERNS = List.of(
            "\\br[a@4]+p[e3](d|ing|r|s|ist)?\\b",
            "\\bmolest(ed|ing|er)?\\b",
            "\\bp[e3]d[o0](phile)?s?\\b",
            "\\bp[o0]rn(o|hub|ography)?\\b",
            "\\bcumm?(ing|shot)?\\b",
            "\\bf[a@4]pp(ing|ed)?\\b",
            "\\bj[e3]rk\\s*off\\b",
            "\\bs[e3]xting\\b",
            "\\bnud[e3]s\\b",
            "\\bonlyf[a@4]ns\\b"
            // "sex" and "nude" on their own are left out: they are ordinary
            // words far more often than not. Add them under blocked-phrases
            // if your server wants them.
    );

    // ── DEATH / VIOLENCE THREATS ─────────────────────────────────────────────
    public static final List<String> THREAT_PATTERNS = List.of(
            "\\bi('?ll|\\s+will|'?m going to)\\s+(kill|murder|end|shoot|stab|slice|gut|destroy)\\s+(you|u|your|ur)\\b",
            "\\bkill\\s+(your|ur)?\\s*self\\b",
            "\\bkys\\b",
            "\\bgo\\s+(die|hang|kys)\\b",
            "\\bi\\s+know\\s+where\\s+(you|u)\\s+(live|stay|sleep|are)\\b",
            "\\bfind\\s+(your|ur)\\s+(address|ip|location|house|home)\\b",
            "\\bdox(x?ing|x?ed)?\\s+(you|u|your|ur)\\b",
            "\\bswatt?(ing|ed|er)?\\b",
            "\\bdd?[o0][s5](ing|ed)?\\s+(you|u|your|ur|this)?\\s*(server)?\\b",
            "\\bboot(ed|ing|er)?\\s+(you|u|offline|off)\\b",
            "\\b(bomb|shoot|blow\\s+up)\\s+(your|ur|the)\\s+(school|house|server|base)\\b"
    );

    // ── ADVERTISING ──────────────────────────────────────────────────────────
    // Run against the raw (and dot-deobfuscated) message, never the normalized
    // form, because normalization strips the punctuation URLs are made of.
    public static final List<String> AD_IP_PATTERNS = List.of(
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{2,5})?\\b"
    );

    /**
     * TLDs that essentially never appear mid-sentence in English, so a bare
     * "word.tld" is strong enough evidence on its own.
     */
    private static final String STRONG_TLDS =
            "com|net|org|xyz|top|club|store|shop|online|site|website|space|host|pro|live|link|fun|games|network";

    /**
     * TLDs that collide badly with ordinary chat — ".me", ".gg", ".io", ".tv",
     * ".us", ".co". "that was fun.me and you" and "nice.gg" are not adverts,
     * so these need corroborating evidence: a protocol, www., a sub-domain, a
     * port or a path.
     */
    private static final String WEAK_TLDS =
            "gg|io|me|cc|us|eu|tv|co|to|ly|sh|st|is|it|at|be|in|de|nu|gl|ru|pl|nl";

    public static final List<String> AD_URL_PATTERNS = List.of(
            // Anything with an explicit protocol.
            "\\bhttps?://[^\\s]+",
            // www.anything
            "\\bwww\\.[a-z0-9.-]{2,}\\.[a-z]{2,}\\b",
            // Minecraft-style sub-domains are strong evidence on any TLD.
            "\\b(?:play|join|mc|pvp|hub|survival|skyblock|smp|craft|server)\\.[a-z0-9-]{2,}\\.[a-z]{2,}(?::\\d{2,5})?\\b",
            // Bare domain on a TLD that does not collide with prose.
            "\\b[a-z0-9][a-z0-9-]{2,}\\.(?:" + STRONG_TLDS + ")(?::\\d{2,5})?(?:/\\S*)?\\b",
            // Ambiguous TLD, but with a sub-domain, a port or a path to back it up.
            "\\b[a-z0-9][a-z0-9-]{1,}\\.[a-z0-9-]{2,}\\.(?:" + WEAK_TLDS + ")(?::\\d{2,5})?\\b",
            "\\b[a-z0-9][a-z0-9-]{2,}\\.(?:" + WEAK_TLDS + ")(?::\\d{2,5})\\b",
            "\\b[a-z0-9][a-z0-9-]{2,}\\.(?:" + WEAK_TLDS + ")/\\S+",
            // Discord invites.
            "\\bdiscord(?:app)?\\.(?:gg|com)/[a-z0-9-]+",
            "\\bdiscord\\.gg\\b"
    );

    // ── PROFANITY ────────────────────────────────────────────────────────────
    public static final List<String> PROFANITY_PATTERNS = List.of(
            // {0,3} rather than + so the vowel-substituted and vowel-dropped
            // forms (f@ck -> "fack", f*ck, fck) are all covered. The character
            // class is deliberately narrow: no English word is f + [u*@a] + ck.
            "\\bf[u*@a]{0,3}ck(ing|ed|er|ers|s)?\\b",
            "\\bfuk+(ing|ed|er|s)?\\b",
            "\\bsh[i1!*@a]{0,3}t(ty|ting|ter|s)?\\b",
            "\\ba[s$*]+h[o0]l[e3][s]?\\b",
            "\\bb[i1*]+tch(es|ing|y)?\\b",
            "\\bd[i1*]+ck(head|s)?\\b",
            "\\bc[u*]+nt[s]?\\b",
            "\\bb[a@*]+st[a@]+rd[s]?\\b",
            "\\b[a@]ss(hole|wipe|hat|clown|face)?s?\\b",
            "\\bwh[o0*]+re[s]?\\b",
            "\\bsl[u*]+t[s]?\\b",
            "\\bm[o0]+th[e3]r\\s*f[u*]+ck[e3]r[s]?\\b",
            "\\bp[u*]+ss[yi][s]?\\b",
            "\\bt[w*]+at[s]?\\b",
            "\\bw[a@*]+nk(er|ers|ing)?\\b"
    );

    // Pre-compiled for speed; immutable so filters can copy them freely.
    public static final List<Pattern> compiledSlurs     = compile(SLUR_PATTERNS);
    public static final List<Pattern> compiledSexual    = compile(SEXUAL_PATTERNS);
    public static final List<Pattern> compiledThreats   = compile(THREAT_PATTERNS);
    public static final List<Pattern> compiledAdIps     = compile(AD_IP_PATTERNS);
    public static final List<Pattern> compiledAdUrls    = compile(AD_URL_PATTERNS);
    public static final List<Pattern> compiledProfanity = compile(PROFANITY_PATTERNS);

    private static List<Pattern> compile(List<String> patterns) {
        return patterns.stream()
                .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
                .toList();
    }
}
