package dev.chatcop.util;

import java.util.List;
import java.util.regex.Pattern;

public final class WordList {

    private WordList() {}

    // ── RACIAL / ETHNIC SLURS ────────────────────────────────────────────────
    // Patterns use word-boundary aware forms and common obfuscations.
    public static final List<String> SLUR_PATTERNS = List.of(
            // N-word — short form "nig", full form, hard-r
            "n[i!1|]+g(?:g?[a@e3]?r?s?)?",
            "n[^a-z0-9]{0,3}i[^a-z0-9]{0,3}g[^a-z0-9]{0,3}g[^a-z0-9]{0,3}[ae]r?",
            // Rape / sexual violence
            "\\br[a@4]+p[e3](d|ing|r|s|ist)?\\b",
            "\\bmolest(ed|ing|er)?\\b",
            // Sexual content
            "\\bp[e3]d[o0](phile)?s?\\b",
            "\\bp[o0]rn(o|hub|ography)?\\b",
            "\\bcumm?(ing|shot)?\\b",
            "\\bf[a@4]pp(ing|ed)?\\b",
            "\\bj[e3]rk\\s*off\\b",
            "\\bs[e3]x(ting)?\\b",
            "\\bnud[e3]s?\\b",
            "\\bonlyf[a@4]ns\\b",
            // K-word
            "\\bk[i1]+k[e3]s?\\b",
            // C-word (anti-chinese)
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
            // Other
            "\\bt[o0]welh[e3][a@4]d\\b",
            "\\bb[e3][a@4]n[e3]r[s]?\\b",
            "\\bg[o0]+k[s]?\\b",
            "\\bcr[a@4]ck[e3]r[s]?\\b"
    );

    // ── DEATH / VIOLENCE THREATS ─────────────────────────────────────────────
    public static final List<String> THREAT_PATTERNS = List.of(
        // I will kill you
        "\\bi('?ll|\\s+will|'?m going to)\\s+(kill|murder|end|shoot|stab|slice|gut|destroy)\\s+(you|u|your|ur)\\b",
        // Kill yourself
        "\\bkill\\s+(your|ur)?\\s*self\\b",
        "\\bkys\\b",
        "\\bgo\\s+(die|hang|kys)\\b",
        // I know where you live / dox threat
        "\\bi\\s+know\\s+where\\s+(you|u)\\s+(live|stay|sleep|are)\\b",
        "\\bfind\\s+(your|ur)\\s+(address|ip|location|house|home)\\b",
        "\\bdox(x?ing|x?ed)?\\s+(you|u|your|ur)\\b",
        // Swat threats
        "\\bswatt?(ing|ed|er)?\\b",
        // DDos threats
        "\\bdd?[o0][s5](ing|ed)?\\s+(you|u|your|ur|this)?\\s*(server)?\\b",
        "\\bboot(ed|ing|er)?\\s+(you|u|offline|off)\\b",
        // Bomb / shooting
        "\\b(bomb|shoot|blow\\s+up)\\s+(your|ur|the)\\s+(school|house|server|base)\\b"
    );

    // ── ADVERTISING PATTERNS ─────────────────────────────────────────────────
    public static final List<String> AD_PATTERNS = List.of(
        // IP address (v4)
        "\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{2,5})?\\b",
        // URL with protocol
        "\\bhttps?://[^\\s]+",
        // www. links
        "\\bwww\\.[a-z0-9.-]{2,}\\.[a-z]{2,}\\b",
        // Minecraft server domain patterns
        "\\b(?:play|join|mc|pvp|hub|survival|skyblock)\\.[a-z0-9-]{2,}\\.[a-z]{2,}(?::\\d+)?\\b",
        // Generic domain.tld (common TLDs)
        "\\b[a-z0-9-]{3,}\\.(?:net|com|org|gg|xyz|top|io|me|cc|us|eu|tv|co)(?::\\d+)?\\b",
        // Discord invite links
        "\\bdiscord(?:app)?\\.(?:gg|com)/[a-z0-9-]+",
        // Obfuscated dots: server(dot)net
        "\\bserver\\s*\\(?dot\\)?\\s*(?:net|com|org|gg)\\b"
    );

    // ── PROFANITY (censor mode) ──────────────────────────────────────────────
    public static final List<String> PROFANITY_PATTERNS = List.of(
        "\\bf[u*]+ck(ing|ed|er|s)?\\b",
        "\\bsh[i1!]+t(ty|ting|ter|s)?\\b",
        "\\ba[s$]+h[o0]le[s]?\\b",
        "\\bb[i1]+tch(es|ing)?\\b",
        "\\bd[i1]+ck(head|s)?\\b",
        "\\bc[u*]+nt[s]?\\b",
        "\\bb[a@]+st[a@]+rd[s]?\\b",
        "\\b[a@]ss(hole|wipe|hat|clown|face)?s?\\b",
        "\\bwh[o0]+re[s]?\\b",
        "\\bsl[u*]+t[s]?\\b",
        "\\bm[o0]+th[e3]r\\s*f[u*]+ck[e3]r[s]?\\b",
        "\\bp[u*]+ss[yi][s]?\\b"
    );

    // Pre-compile all patterns for speed
    public static List<Pattern> compiledSlurs;
    public static List<Pattern> compiledThreats;
    public static List<Pattern> compiledAds;
    public static List<Pattern> compiledProfanity;

    static {
        compiledSlurs   = compile(SLUR_PATTERNS);
        compiledThreats = compile(THREAT_PATTERNS);
        compiledAds     = compile(AD_PATTERNS);
        compiledProfanity = compile(PROFANITY_PATTERNS);
    }

    private static List<Pattern> compile(List<String> patterns) {
        return patterns.stream()
            .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
            .toList();
    }
}
