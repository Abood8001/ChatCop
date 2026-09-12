package dev.chatcop;

import dev.chatcop.filter.FilterSupport;
import dev.chatcop.util.ColorUtil;
import dev.chatcop.util.DurationParser;
import dev.chatcop.util.TextNormalizer;
import dev.chatcop.util.WordList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Detection is almost entirely regex plus a normalization pipeline, which is
 * exactly the kind of code that breaks quietly. These lock in the behaviour
 * that regressed before: obfuscation that slipped through, and ordinary chat
 * that got caught.
 */
class DetectionTest {

    private static boolean flagged(String message) {
        List<String> variants = TextNormalizer.variants(message);
        return TextNormalizer.matchesAny(variants, WordList.compiledProfanity)
                || TextNormalizer.matchesAny(variants, WordList.compiledSlurs)
                || TextNormalizer.matchesAny(variants, WordList.compiledSexual)
                || TextNormalizer.matchesAny(variants, WordList.compiledThreats);
    }

    private static boolean advert(String message) {
        String scan = TextNormalizer.deobfuscateDomains(message);
        for (Pattern p : WordList.compiledAdUrls) if (p.matcher(scan).find()) return true;
        for (Pattern p : WordList.compiledAdIps) if (p.matcher(scan).find()) return true;
        return false;
    }

    @Nested
    @DisplayName("Obfuscated profanity and slurs are detected")
    class Obfuscation {

        @ParameterizedTest(name = "\"{0}\" is caught")
        @ValueSource(strings = {
                // Plain
                "fuck", "shit", "nigger", "bitch", "cunt",
                // Symbol substitution - these all passed before, because the
                // normalizer stripped the symbol before leet mapping ran.
                "f*ck", "sh!t", "n!gg3r", "n!gger", "c*nt", "b!tch", "f@ck",
                // Digit leet
                "sh1t", "n1gg3r", "$hit",
                // Separator obfuscation
                "f.u.c.k", "f-u-c-k", "n.i.g.g.e.r",
                // Letter spacing - advertised in the README but never worked
                "f u c k", "fu ck", "s h i t", "n i g g e r", "a s s h o l e",
                // Trailing punctuation must not hide the word
                "fuck!", "what the shit!",
                // Threats
                "kys", "kill yourself"
        })
        void isCaught(String message) {
            assertTrue(flagged(message), "should have been flagged: " + message);
        }
    }

    @Nested
    @DisplayName("Ordinary chat is not flagged")
    class NoFalsePositives {

        @ParameterizedTest(name = "\"{0}\" is clean")
        @ValueSource(strings = {
                "good game everyone",
                "the night is dark",
                "i am a knight in the arena",
                "how do i get night vision",
                // "cracker" used to be in the slur list and fired constantly
                "nice crackers lol",
                "i have crackers in my inventory",
                // These were mangled by the old letter-joining regex
                "can u c me",
                "he is a knight",
                "is a good day",
                "u ok m8",
                "gg wp nice round",
                "lets go to the nether",
                "anyone want to trade",
                "negative numbers dont work",
                "the negotiation failed",
                "he is such a class act",
                "that pass was great",
                "i need iron and coal",
                "im gonna go afk brb",
                "1v1 me bro",
                "see you tomorrow"
        })
        void isClean(String message) {
            assertFalse(flagged(message), "should NOT have been flagged: " + message);
        }
    }

    @Nested
    @DisplayName("Advertising")
    class Advertising {

        @ParameterizedTest(name = "\"{0}\" is an advert")
        @ValueSource(strings = {
                "join hypixel.net",
                "play.example.com",
                "192.168.1.1",
                "connect 51.83.24.11:25565",
                "mc.server.gg",
                "server.gg:25565",
                "https://youtu.be/abc",
                "www.example.io",
                "discord.gg/abcd",
                "example (dot) com",
                "play (dot) server (dot) net"
        })
        void isAdvert(String message) {
            assertTrue(advert(message), "should have been flagged as advertising: " + message);
        }

        @ParameterizedTest(name = "\"{0}\" is not an advert")
        @ValueSource(strings = {
                // Ambiguous TLDs need corroborating evidence now
                "that was fun.me and you should play again",
                "nice.gg",
                "the end.io",
                "see you.tv",
                "hey.us guys",
                "done.me too",
                "ok.co worker",
                "wait.im coming",
                "stop.it now",
                "i died at 1.20.1"
        })
        void isNotAdvert(String message) {
            assertFalse(advert(message), "should NOT have been flagged as advertising: " + message);
        }
    }

    @Nested
    @DisplayName("Custom phrase compilation")
    class CustomPhrases {

        @Test
        @DisplayName("blank entries are skipped instead of matching every message")
        void blankIsSkipped() {
            List<Pattern> compiled = FilterSupport.compileCustom(
                    null, java.util.Arrays.asList("", "   ", null), "test");
            assertTrue(compiled.isEmpty(), "blank phrases must not compile");
        }

        @Test
        @DisplayName("a plain word matches as a whole word only")
        void plainWordIsBounded() {
            List<Pattern> compiled = FilterSupport.compileCustom(null, List.of("nega"), "test");
            assertEquals(1, compiled.size());
            Pattern p = compiled.get(0);
            assertTrue(p.matcher("stop nega now").find());
            assertFalse(p.matcher("negative").find(), "must not match inside a longer word");
            assertFalse(p.matcher("i negated it").find());
        }

        @Test
        @DisplayName("a phrase with regex characters is still treated as regex")
        void regexStaysRegex() {
            List<Pattern> compiled = FilterSupport.compileCustom(null, List.of("bad(word)?s"), "test");
            assertEquals(1, compiled.size());
            assertTrue(compiled.get(0).matcher("badwords").find());
        }

        @Test
        @DisplayName("an invalid regex is dropped, not thrown")
        void invalidRegexIsDropped() {
            List<Pattern> compiled = FilterSupport.compileCustom(null, List.of("[unclosed"), "test");
            assertTrue(compiled.isEmpty());
        }
    }

    @Nested
    @DisplayName("Colour codes")
    class Colours {

        @Test
        @DisplayName("an ampersand that isn't a colour code is left alone")
        void bareAmpersandSurvives() {
            assertEquals("Fish & Chips", ColorUtil.translate("Fish & Chips"));
            assertEquals("Tom & Jerry", ColorUtil.translate("Tom & Jerry"));
            assertEquals("100% & rising", ColorUtil.translate("100% & rising"));
        }

        @Test
        @DisplayName("an ampersand followed by a real code letter is still a code")
        void ampersandBeforeCodeLetterIsACode() {
            // "&d" is light purple, so "R&D" is genuinely ambiguous - this
            // matches vanilla Bukkit behaviour. "&&" is the escape.
            assertEquals("R§dteam", ColorUtil.translate("R&Dteam"));
            assertEquals("R&Dteam", ColorUtil.translate("R&&Dteam"));
        }

        @Test
        @DisplayName("real colour codes are translated")
        void codesTranslate() {
            assertEquals("§aGreen", ColorUtil.translate("&aGreen"));
            assertEquals("§lBold", ColorUtil.translate("&lBold"));
        }

        @Test
        @DisplayName("&& is an escaped literal ampersand")
        void escapedAmpersand() {
            assertEquals("Tom & Jerry", ColorUtil.translate("Tom && Jerry"));
        }

        @Test
        @DisplayName("hex colours are expanded")
        void hexExpands() {
            assertEquals("§x§f§f§0§0§0§0red", ColorUtil.translate("&#FF0000red"));
        }

        @Test
        @DisplayName("stripping removes both code styles")
        void stripsBoth() {
            assertEquals("hello", ColorUtil.strip("&ahe§bllo"));
        }
    }

    @Nested
    @DisplayName("Duration parsing")
    class Durations {

        @Test
        void parsesUnits() {
            assertEquals(10_000L, DurationParser.parse("10s"));
            assertEquals(600_000L, DurationParser.parse("10m"));
            assertEquals(5_400_000L, DurationParser.parse("1h30m"));
            assertEquals(-1L, DurationParser.parse("perm"));
        }

        @Test
        @DisplayName("unparseable input returns 0, never permanent")
        void invalidIsZeroNotPermanent() {
            assertEquals(0L, DurationParser.parse("abc"));
            assertEquals(0L, DurationParser.parse("0m"));
            assertEquals(0L, DurationParser.parse("5x"));
            assertEquals(0L, DurationParser.parse("10"));
        }
    }

    @Nested
    @DisplayName("Similarity")
    class Similarity {

        @Test
        void identicalIsOne() {
            assertEquals(1.0, TextNormalizer.similarity("hello there", "hello there"));
        }

        @Test
        void nearDuplicatesScoreHigh() {
            assertTrue(TextNormalizer.similarity("join my server now", "join my server now!") >= 0.8);
        }

        @Test
        void differentMessagesScoreLow() {
            assertTrue(TextNormalizer.similarity("hello everyone", "where is the nether portal") < 0.5);
        }
    }
}
